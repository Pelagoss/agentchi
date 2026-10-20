#!/usr/bin/env bash
# Claude Companion — relaie les événements Claude Code vers la montre via ntfy.
#
# Utilisation en hook Claude Code : l'événement JSON arrive sur stdin.
# Utilisation manuelle :  claude-companion-notify.sh <type> "<message>"
#   types : permission | input | done | subagent | session_start | session_end | compact | error | other
# Option --cloud-only (en premier) : ne fait rien hors d'une session cloud claude.ai/code.
#
# Configuration (écrite par install.sh dans ~/.claude/claude-companion.env,
# ou variables d'environnement, par ex. celles d'un environnement cloud) :
#   CLAUDE_COMPANION_PAIRING  code d'appairage « sujet.clé » (Paramètres > Appairer)
#     ou bien séparément :
#   CLAUDE_COMPANION_TOPIC    sujet ntfy de la montre
#   CLAUDE_COMPANION_KEY      clé secrète de la montre
#   CLAUDE_COMPANION_SERVER   facultatif, défaut : https://ntfy.sh
#   CLAUDE_COMPANION_TOKEN    facultatif, jeton d'accès pour un serveur ntfy protégé
#   CLAUDE_COMPANION_ACCOUNT  facultatif, nom du compte affiché sur la montre
#                             (par défaut : e-mail du compte Claude connecté)
#
# Le contenu est chiffré (AES-256-CBC + HMAC-SHA256) : le serveur ntfy ne voit rien,
# et la montre ignore tout message qui n'a pas été produit avec sa clé.
set -uo pipefail

if [ "${1:-}" = "--cloud-only" ]; then
  shift
  [ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0
fi

CONFIG="${CLAUDE_COMPANION_CONFIG:-$HOME/.claude/claude-companion.env}"
# shellcheck disable=SC1090
[ -f "$CONFIG" ] && . "$CONFIG"

SERVER="${CLAUDE_COMPANION_SERVER:-https://ntfy.sh}"
TOPIC="${CLAUDE_COMPANION_TOPIC:-}"
TOKEN="${CLAUDE_COMPANION_TOKEN:-}"
KEY="${CLAUDE_COMPANION_KEY:-}"
ACCOUNT="${CLAUDE_COMPANION_ACCOUNT:-}"
PAIRING="${CLAUDE_COMPANION_PAIRING:-}"
if [ -n "$PAIRING" ] && [ "${PAIRING#*.}" != "$PAIRING" ]; then
  TOPIC="${PAIRING%%.*}"
  KEY="${PAIRING#*.}"
fi

# Ne jamais bloquer ni faire échouer Claude Code.
[ -n "$TOPIC" ] && [ -n "$KEY" ] || exit 0
for bin in curl jq openssl; do command -v "$bin" >/dev/null 2>&1 || exit 0; done

INPUT='{}'
TYPE=other
MESSAGE=""
PRIORITY=3

if [ $# -ge 1 ]; then
  TYPE="$1"
  MESSAGE="${2:-}"
else
  INPUT="$(cat)"
fi

field() { jq -r "$1 // empty" <<<"$INPUT" 2>/dev/null; }

CWD="$(field .cwd)"
PROJECT="$(basename "${CWD:-$PWD}")"
SESSION="$(field .session_id | cut -c1-8)"

case "$(field .hook_event_name)" in
  Notification)
    MESSAGE="$(field .message)"
    case "$(field .notification_type)" in
      permission_prompt) TYPE=permission ;;
      idle_prompt | elicitation_dialog) TYPE=input ;;
      "")
        if grep -qi 'permission' <<<"$MESSAGE"; then TYPE=permission
        elif grep -qiE 'waiting|input' <<<"$MESSAGE"; then TYPE=input
        else TYPE=other; fi ;;
      *) TYPE=other ;;
    esac
    ;;
  Stop)
    TYPE="done"
    MESSAGE="$(field .last_assistant_message)"
    TRANSCRIPT="$(field .transcript_path)"
    if [ -z "$MESSAGE" ] && [ -f "$TRANSCRIPT" ]; then
      # Dernier texte écrit par Claude, pour avoir un résumé sur la montre
      MESSAGE="$(tail -n 400 "$TRANSCRIPT" | jq -rs '
        [ .[] | select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text ]
        | last // empty' 2>/dev/null)"
    fi
    [ -n "$MESSAGE" ] || MESSAGE="Claude a terminé sa tâche."
    ;;
  SubagentStop) TYPE=subagent; MESSAGE="Un sous-agent a terminé." ;;
  SessionStart) TYPE=session_start; MESSAGE="Nouvelle session ($(field .source))" ;;
  SessionEnd) TYPE=session_end; MESSAGE="Session terminée ($(field .reason))" ;;
  PreCompact) TYPE=compact; MESSAGE="Compaction du contexte ($(field .trigger))" ;;
esac

case "$TYPE" in
  permission) PRIORITY=5 ;;
  input | error) PRIORITY=4 ;;
  session_start | compact | subagent) PRIORITY=2 ;;
esac

# Une ligne, 300 caractères max : c'est une montre.
MESSAGE="$(tr '\n\r\t' '   ' <<<"$MESSAGE" | sed 's/  */ /g; s/^ //; s/ $//' | cut -c1-300)"
[ -n "$MESSAGE" ] || MESSAGE="Événement Claude ($TYPE)"

# Compte Claude connecté, pour pouvoir filtrer par compte sur la montre
[ -n "$ACCOUNT" ] || ACCOUNT="${CLAUDE_CODE_USER_EMAIL:-}"
if [ -z "$ACCOUNT" ]; then
  CLAUDE_JSON="${CLAUDE_CONFIG_DIR:-$HOME}/.claude.json"
  [ -f "$CLAUDE_JSON" ] && ACCOUNT="$(jq -r '.oauthAccount.emailAddress // empty' "$CLAUDE_JSON" 2>/dev/null)"
fi

# Session cloud (claude.ai/code) : lien pour l'ouvrir depuis le téléphone
CLOUD=false
URL=""
if [ "${CLAUDE_CODE_REMOTE:-}" = "true" ]; then
  CLOUD=true
  REMOTE_ID="${CLAUDE_CODE_REMOTE_SESSION_ID:-}"
  [ -n "$REMOTE_ID" ] && URL="https://claude.ai/code/session_${REMOTE_ID#cse_}"
fi

PLAIN="$(jq -cn --arg t "$TYPE" --arg m "$MESSAGE" --arg p "$PROJECT" --arg s "$SESSION" --arg a "$ACCOUNT" \
  --arg u "$URL" --argjson c "$CLOUD" \
  '{t: $t, m: $m, p: $p, s: $s, a: $a, u: $u, c: $c} | with_entries(select(.value != "" and .value != false))')"

# Chiffrement v1 — voir app/.../net/PayloadCrypto.kt
hex_digest() { openssl dgst -sha256 "$@" | awk '{print $NF}'; }
ENC_KEY="$(printf '%s' "claude-companion/enc:$KEY" | hex_digest)"
IV="$(openssl rand -hex 16)"
CIPHER="$(printf '%s' "$PLAIN" | openssl enc -aes-256-cbc -K "$ENC_KEY" -iv "$IV" | openssl base64 -A)"
HEAD="v1.$IV.$CIPHER"
MAC="$(printf '%s' "$HEAD" | hex_digest -hmac "claude-companion/mac:$KEY")"

PAYLOAD="$(jq -n --arg topic "$TOPIC" --arg message "$HEAD.$MAC" --argjson priority "$PRIORITY" \
  '{topic: $topic, message: $message, priority: $priority}')"

ARGS=(-fsS -m 10 -H "Content-Type: application/json" -d "$PAYLOAD")
[ -n "$TOKEN" ] && ARGS+=(-H "Authorization: Bearer $TOKEN")

# Envoi en arrière-plan pour ne pas ralentir Claude.
( curl "${ARGS[@]}" "${SERVER%/}" >/dev/null 2>&1 & )
exit 0
