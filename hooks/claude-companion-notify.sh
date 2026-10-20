#!/usr/bin/env bash
# Claude Companion — relaie les événements Claude Code vers la montre via ntfy.
#
# Utilisation en hook Claude Code : l'événement JSON arrive sur stdin.
# Utilisation manuelle :  claude-companion-notify.sh <type> "<message>"
#   types : permission | input | done | subagent | session_start | session_end | compact | error | other
#
# Variables d'environnement :
#   CLAUDE_COMPANION_TOPIC   (obligatoire) sujet affiché dans Paramètres > Connexion sur la montre
#   CLAUDE_COMPANION_SERVER  (facultatif)  défaut : https://ntfy.sh
#   CLAUDE_COMPANION_TOKEN   (facultatif)  jeton d'accès pour un serveur ntfy protégé
set -uo pipefail

CONFIG="${CLAUDE_COMPANION_CONFIG:-$HOME/.claude/claude-companion.env}"
# shellcheck disable=SC1090
[ -f "$CONFIG" ] && . "$CONFIG"

SERVER="${CLAUDE_COMPANION_SERVER:-https://ntfy.sh}"
TOPIC="${CLAUDE_COMPANION_TOPIC:-}"
TOKEN="${CLAUDE_COMPANION_TOKEN:-}"

# Ne jamais bloquer ni faire échouer Claude Code.
[ -n "$TOPIC" ] || exit 0
command -v curl >/dev/null 2>&1 || exit 0
command -v jq >/dev/null 2>&1 || exit 0

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
    TYPE=done
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

PAYLOAD="$(jq -n \
  --arg topic "$TOPIC" \
  --arg title "$PROJECT" \
  --arg message "$MESSAGE" \
  --arg type "cc-$TYPE" \
  --arg session "ccs-${SESSION:-manual}" \
  --argjson priority "$PRIORITY" \
  '{topic: $topic, title: $title, message: $message, tags: [$type, $session], priority: $priority}')"

ARGS=(-fsS -m 10 -H "Content-Type: application/json" -d "$PAYLOAD")
[ -n "$TOKEN" ] && ARGS+=(-H "Authorization: Bearer $TOKEN")

# Envoi en arrière-plan pour ne pas ralentir Claude.
( curl "${ARGS[@]}" "${SERVER%/}" >/dev/null 2>&1 & )
exit 0
