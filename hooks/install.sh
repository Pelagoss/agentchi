#!/usr/bin/env bash
# Installe le hook Claude Companion (Linux / macOS / WSL / sessions cloud).
#
#   ./hooks/install.sh <code-appairage> [options]   ordinateur : ~/.claude
#   ./hooks/install.sh --project <dossier-dépôt>    dépôt : hook pour les sessions cloud, à commiter
#   ./hooks/install.sh --cloud                      script de configuration d'un environnement cloud
#
# Le code d'appairage est affiché sur la montre : Paramètres > Appairer un ordinateur.
# Avec --project et --cloud, le code n'est jamais écrit sur disque : il est lu au moment
# de l'envoi dans la variable d'environnement CLAUDE_COMPANION_PAIRING.
#
# Options :
#   --account <nom>    nom du compte affiché sur la montre (défaut : e-mail du compte Claude)
#   --server <url>     serveur ntfy (défaut : https://ntfy.sh)
#   --token <jeton>    jeton d'accès pour un serveur ntfy protégé
set -euo pipefail

usage() { sed -n '3,16p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

MODE=user
CODE=""
PROJECT_DIR=""
SERVER="https://ntfy.sh"; TOKEN=""; ACCOUNT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --project) MODE=project; PROJECT_DIR="${2:-}"; shift 2 || usage ;;
    --cloud) MODE=cloud; shift ;;
    --server) SERVER="$2"; shift 2 ;;
    --token) TOKEN="$2"; shift 2 ;;
    --account) ACCOUNT="$2"; shift 2 ;;
    -*) usage ;;
    *) CODE="$1"; shift ;;
  esac
done

for bin in jq curl openssl; do
  command -v "$bin" >/dev/null || { echo "« $bin » est requis."; exit 1; }
done

DIR="$(cd "$(dirname "$0")" && pwd)"

# Ajoute la commande à chaque événement, sans dupliquer ni toucher aux autres hooks.
add_hooks() { # <settings.json> <commande>
  local settings="$1" cmd="$2" tmp
  mkdir -p "$(dirname "$settings")"
  if [ -f "$settings" ]; then
    cp "$settings" "$settings.bak.$(date +%s)"
  else
    echo '{}' > "$settings"
  fi
  tmp="$(mktemp)"
  jq --arg cmd "$cmd" '
    .hooks //= {} |
    reduce ("Notification", "Stop", "SubagentStop", "SessionStart", "SessionEnd", "PreCompact") as $e (.;
      .hooks[$e] = (
        ((.hooks[$e] // []) | map(select(([.hooks[]?.command] | map(test("claude-companion-notify")) | any) | not)))
        + [{hooks: [{type: "command", command: $cmd}]}]
      ))
  ' "$settings" > "$tmp" && mv "$tmp" "$settings"
}

case "$MODE" in
  project)
    [ -d "$PROJECT_DIR" ] || { echo "Dossier introuvable : $PROJECT_DIR"; exit 1; }
    PROJECT_DIR="$(cd "$PROJECT_DIR" && pwd)"
    mkdir -p "$PROJECT_DIR/.claude/hooks"
    cp "$DIR/claude-companion-notify.sh" "$PROJECT_DIR/.claude/hooks/"
    chmod +x "$PROJECT_DIR/.claude/hooks/claude-companion-notify.sh"
    # Hook actif uniquement dans les sessions cloud : en local, celui de ~/.claude prend le relais
    # (pas de notification en double). Sans CLAUDE_COMPANION_PAIRING, il ne fait rien.
    # shellcheck disable=SC2016 # $CLAUDE_PROJECT_DIR est développé par Claude Code
    add_hooks "$PROJECT_DIR/.claude/settings.json" \
      '"$CLAUDE_PROJECT_DIR"/.claude/hooks/claude-companion-notify.sh --cloud-only'
    rm -f "$PROJECT_DIR"/.claude/settings.json.bak.*
    echo "✅ Hook ajouté au dépôt : $PROJECT_DIR/.claude/"
    echo "   À commiter : git -C \"$PROJECT_DIR\" add .claude && git -C \"$PROJECT_DIR\" commit -m \"Claude Companion\""
    echo "   Puis, dans l'environnement cloud : variable CLAUDE_COMPANION_PAIRING=<code> et domaine ntfy.sh autorisé."
    ;;

  cloud)
    mkdir -p "$HOME/.claude/hooks"
    cp "$DIR/claude-companion-notify.sh" "$HOME/.claude/hooks/"
    chmod +x "$HOME/.claude/hooks/claude-companion-notify.sh"
    add_hooks "$HOME/.claude/settings.json" "$HOME/.claude/hooks/claude-companion-notify.sh --cloud-only"
    echo "✅ Hook Claude Companion installé pour les sessions cloud."
    ;;

  user)
    [ -n "$CODE" ] || usage
    TOPIC="${CODE%%.*}"
    KEY="${CODE#*.}"
    if [ "$TOPIC" = "$CODE" ] || ! [[ "$TOPIC" =~ ^[-_A-Za-z0-9]{1,64}$ ]] || ! [[ "$KEY" =~ ^[a-z0-9]{20,}$ ]]; then
      echo "Code d'appairage invalide. Format attendu : <sujet>.<clé> (voir la montre)."
      exit 1
    fi
    CLAUDE_DIR="$HOME/.claude"
    HOOK="$CLAUDE_DIR/hooks/claude-companion-notify.sh"
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
    add_hooks "$CLAUDE_DIR/settings.json" "$HOOK"
    echo "✅ Hook installé : $HOOK"
    echo "   Paramètres mis à jour : $CLAUDE_DIR/settings.json (sauvegarde créée)"
    echo "   Test : $HOOK done \"Bonjour depuis Claude Code 👋\""
    ;;
esac
