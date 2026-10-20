#!/usr/bin/env bash
# Installe le hook Claude Companion dans ~/.claude (Linux / macOS / WSL).
#   ./hooks/install.sh <sujet> [serveur] [jeton]
set -euo pipefail

TOPIC="${1:-}"
SERVER="${2:-https://ntfy.sh}"
TOKEN="${3:-}"

if [ -z "$TOPIC" ]; then
  echo "Usage : $0 <sujet> [serveur] [jeton]"
  echo "Le sujet est affiché sur la montre : Paramètres > Connexion > Sujet."
  exit 1
fi
for bin in jq curl; do
  command -v "$bin" >/dev/null || { echo "« $bin » est requis."; exit 1; }
done

DIR="$(cd "$(dirname "$0")" && pwd)"
CLAUDE_DIR="$HOME/.claude"
HOOK="$CLAUDE_DIR/hooks/claude-companion-notify.sh"
SETTINGS="$CLAUDE_DIR/settings.json"

mkdir -p "$CLAUDE_DIR/hooks"
cp "$DIR/claude-companion-notify.sh" "$HOOK"
chmod +x "$HOOK"

umask 077
cat > "$CLAUDE_DIR/claude-companion.env" <<CONF
CLAUDE_COMPANION_TOPIC=$TOPIC
CLAUDE_COMPANION_SERVER=$SERVER
CLAUDE_COMPANION_TOKEN=$TOKEN
CONF

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
