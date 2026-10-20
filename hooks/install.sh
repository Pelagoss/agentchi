#!/usr/bin/env bash
# Facultatif : hook Claude Code pour les sessions lancées dans un terminal sur cet ordinateur
# (les notifications de l'appli Claude sur le téléphone, elles, sont relayées sans hook).
#
#   ./hooks/install.sh [code-appairage] [--account <nom>] [--server <url>] [--token <jeton>]
#
# Le code d'appairage est affiché sur la montre : Paramètres > Hook Claude Code.
# Une seule installation par ordinateur, valable pour tous tes projets.
set -euo pipefail

usage() { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

CODE=""
SERVER="https://ntfy.sh"; TOKEN=""; ACCOUNT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --server) [ $# -ge 2 ] || usage; SERVER="$2"; shift 2 ;;
    --token) [ $# -ge 2 ] || usage; TOKEN="$2"; shift 2 ;;
    --account) [ $# -ge 2 ] || usage; ACCOUNT="$2"; shift 2 ;;
    -*) usage ;;
    *) CODE="$1"; shift ;;
  esac
done

for bin in jq curl openssl; do
  command -v "$bin" >/dev/null || { echo "« $bin » est requis."; exit 1; }
done

if [ -z "$CODE" ]; then
  echo "Sur la montre : Paramètres > Hook Claude Code."
  read -rp "Code d'appairage : " CODE
fi
CODE="$(printf '%s' "$CODE" | tr '[:upper:]' '[:lower:]' | tr -d ' -')"
if ! [[ "$CODE" =~ ^[a-z0-9]{20,}$ ]] && ! [[ "$CODE" =~ ^[-_a-z0-9]{1,64}\.[a-z0-9]{20,}$ ]]; then
  echo "Code d'appairage invalide (voir la montre)."
  exit 1
fi

CLAUDE_DIR="$HOME/.claude"
HOOK="$CLAUDE_DIR/hooks/claude-companion-notify.sh"
mkdir -p "$CLAUDE_DIR/hooks"
cp "$(cd "$(dirname "$0")" && pwd)/claude-companion-notify.sh" "$HOOK"
chmod +x "$HOOK"

# Le code est secret : fichier lisible uniquement par toi.
( umask 077
  cat > "$CLAUDE_DIR/claude-companion.env" <<CONF
CLAUDE_COMPANION_PAIRING=$CODE
CLAUDE_COMPANION_SERVER=$SERVER
CLAUDE_COMPANION_TOKEN=$TOKEN
CLAUDE_COMPANION_ACCOUNT=$ACCOUNT
CONF
)

SETTINGS="$CLAUDE_DIR/settings.json"
if [ -f "$SETTINGS" ]; then cp "$SETTINGS" "$SETTINGS.bak.$(date +%s)"; else echo '{}' > "$SETTINGS"; fi
# Ajoute le hook à chaque événement, sans dupliquer ni toucher aux autres hooks.
TMP="$(mktemp)"
jq --arg cmd "$HOOK" '
  .hooks //= {} |
  reduce ("Notification", "Stop", "SubagentStop", "SessionStart", "SessionEnd", "PreCompact") as $e (.;
    .hooks[$e] = (
      ((.hooks[$e] // []) | map(select(([.hooks[]?.command] | map(test("claude-companion-notify")) | any) | not)))
      + [{hooks: [{type: "command", command: $cmd}]}]
    ))
' "$SETTINGS" > "$TMP" && mv "$TMP" "$SETTINGS"

echo "✅ Hook installé pour tous tes projets sur cet ordinateur."
"$HOOK" "done" "Appairage réussi 🎉 Claude Companion est prêt." || true
echo "   Notification de test envoyée : ouvre l'appli sur la montre."
