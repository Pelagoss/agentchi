#!/usr/bin/env bash
# Installe le hook Claude Companion dans ~/.claude (Linux / macOS / WSL).
#
#   ./hooks/install.sh <code-appairage> [options]
#
# Le code d'appairage est affiché sur la montre : Paramètres > Appairer un ordinateur.
# Options :
#   --account <nom>    nom du compte affiché sur la montre (défaut : e-mail du compte Claude)
#   --server <url>     serveur ntfy (défaut : https://ntfy.sh)
#   --token <jeton>    jeton d'accès pour un serveur ntfy protégé
set -euo pipefail

usage() { sed -n '3,12p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

CODE="${1:-}"
if [ -z "$CODE" ] || [ "${CODE#-}" != "$CODE" ]; then usage; fi
shift
SERVER="https://ntfy.sh"; TOKEN=""; ACCOUNT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --server) SERVER="$2"; shift 2 ;;
    --token) TOKEN="$2"; shift 2 ;;
    --account) ACCOUNT="$2"; shift 2 ;;
    *) usage ;;
  esac
done

TOPIC="${CODE%%.*}"
KEY="${CODE#*.}"
if [ "$TOPIC" = "$CODE" ] || ! [[ "$TOPIC" =~ ^[-_A-Za-z0-9]{1,64}$ ]] || ! [[ "$KEY" =~ ^[a-z0-9]{20,}$ ]]; then
  echo "Code d'appairage invalide. Format attendu : <sujet>.<clé> (voir la montre)."
  exit 1
fi
for bin in jq curl openssl; do
  command -v "$bin" >/dev/null || { echo "« $bin » est requis."; exit 1; }
done

DIR="$(cd "$(dirname "$0")" && pwd)"
CLAUDE_DIR="$HOME/.claude"
HOOK="$CLAUDE_DIR/hooks/claude-companion-notify.sh"
SETTINGS="$CLAUDE_DIR/settings.json"

mkdir -p "$CLAUDE_DIR/hooks"
cp "$DIR/claude-companion-notify.sh" "$HOOK"
chmod +x "$HOOK"

# La clé est secrète : fichier lisible uniquement par toi.
( umask 077
  cat > "$CLAUDE_DIR/claude-companion.env" <<CONF
CLAUDE_COMPANION_TOPIC=$TOPIC
CLAUDE_COMPANION_KEY=$KEY
CLAUDE_COMPANION_SERVER=$SERVER
CLAUDE_COMPANION_TOKEN=$TOKEN
CLAUDE_COMPANION_ACCOUNT=$ACCOUNT
CONF
)

[ -f "$SETTINGS" ] || echo '{}' > "$SETTINGS"
cp "$SETTINGS" "$SETTINGS.bak.$(date +%s)"

# Ajoute le hook à chaque événement, sans dupliquer ni toucher aux autres hooks.
TMP="$(mktemp)"
jq --arg cmd "$HOOK" '
  .hooks //= {} |
  reduce ("Notification", "Stop", "SubagentStop", "SessionStart", "SessionEnd", "PreCompact") as $e (.;
    .hooks[$e] = (
      ((.hooks[$e] // []) | map(select(([.hooks[]?.command] | index($cmd)) | not)))
      + [{hooks: [{type: "command", command: $cmd}]}]
    ))
' "$SETTINGS" > "$TMP" && mv "$TMP" "$SETTINGS"

echo "✅ Hook installé : $HOOK"
echo "   Paramètres mis à jour : $SETTINGS (sauvegarde créée)"
echo "   Test : $HOOK done \"Bonjour depuis Claude Code 👋\""
