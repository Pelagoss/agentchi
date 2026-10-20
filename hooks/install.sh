#!/usr/bin/env bash
# Installe le hook Claude Companion (Linux / macOS / WSL).
#
#   ./hooks/install.sh                          mode guidé (recommandé)
#   ./hooks/install.sh <code> [--repo o/nom]…   ordinateur + dépôts GitHub pour le cloud
#   ./hooks/install.sh --repo <owner/nom>…      dépôts seulement (commit + push du hook)
#   ./hooks/install.sh --project <dossier>      ajoute le hook à un dépôt local, sans commiter
#
# Le code d'appairage est affiché sur la montre : Paramètres > Appairer un ordinateur.
# Sur l'ordinateur, une installation couvre tous tes projets. Les sessions cloud, elles,
# n'exécutent que les hooks commités dans le dépôt : d'où --repo, une fois par dépôt.
# Aucun secret n'est écrit dans les dépôts : le cloud lit le code dans la variable
# d'environnement CLAUDE_COMPANION_PAIRING de l'environnement cloud.
#
# Options : --account <nom>  --server <url ntfy>  --token <jeton ntfy>
set -euo pipefail

usage() { sed -n '3,16p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

CODE=""
REPOS=()
PROJECT_DIR=""
SERVER="https://ntfy.sh"; TOKEN=""; ACCOUNT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --repo) [ $# -ge 2 ] || usage; REPOS+=("$2"); shift 2 ;;
    --project) [ $# -ge 2 ] || usage; PROJECT_DIR="$2"; shift 2 ;;
    --server) [ $# -ge 2 ] || usage; SERVER="$2"; shift 2 ;;
    --token) [ $# -ge 2 ] || usage; TOKEN="$2"; shift 2 ;;
    --account) [ $# -ge 2 ] || usage; ACCOUNT="$2"; shift 2 ;;
    -h | --help | -*) usage ;;
    *) CODE="$1"; shift ;;
  esac
done

for bin in jq curl openssl git; do
  command -v "$bin" >/dev/null || { echo "« $bin » est requis."; exit 1; }
done

DIR="$(cd "$(dirname "$0")" && pwd)"
NOTIFY="$DIR/claude-companion-notify.sh"

# Ajoute la commande à chaque événement, sans dupliquer ni toucher aux autres hooks.
add_hooks() { # <settings.json> <commande>
  local settings="$1" cmd="$2" tmp
  mkdir -p "$(dirname "$settings")"
  [ -f "$settings" ] || echo '{}' > "$settings"
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

# Hook de dépôt : actif uniquement dans les sessions cloud (en local, celui de ~/.claude
# prend le relais, sans doublon) et silencieux sans CLAUDE_COMPANION_PAIRING.
add_project() { # <dossier>
  mkdir -p "$1/.claude/hooks"
  cp "$NOTIFY" "$1/.claude/hooks/"
  chmod +x "$1/.claude/hooks/claude-companion-notify.sh"
  # shellcheck disable=SC2016 # $CLAUDE_PROJECT_DIR est développé par Claude Code
  add_hooks "$1/.claude/settings.json" '"$CLAUDE_PROJECT_DIR"/.claude/hooks/claude-companion-notify.sh --cloud-only'
}

add_repo() { # <owner/nom> : clone, ajoute le hook, commit et push
  local repo="$1" tmp
  tmp="$(mktemp -d)"
  if command -v gh >/dev/null; then
    gh repo clone "$repo" "$tmp/r" -- --depth 1 -q 2>/dev/null
  else
    git clone -q --depth 1 "https://github.com/$repo" "$tmp/r" 2>/dev/null
  fi || { echo "   ❌ $repo : impossible de cloner"; rm -rf "$tmp"; return 0; }
  add_project "$tmp/r"
  if [ -z "$(git -C "$tmp/r" status --porcelain)" ]; then
    echo "   ✔︎ $repo : déjà à jour"
  else
    git -C "$tmp/r" add .claude
    git -C "$tmp/r" commit -q -m "Claude Companion : notifications des sessions cloud vers la montre"
    if git -C "$tmp/r" push -q origin HEAD 2>/dev/null; then
      echo "   ✅ $repo"
    elif git -C "$tmp/r" push -q origin HEAD:claude-companion 2>/dev/null; then
      echo "   ⚠️  $repo : branche protégée, hook poussé sur la branche « claude-companion » (à fusionner)"
    else
      echo "   ❌ $repo : push refusé"
    fi
  fi
  rm -rf "$tmp"
}

normalize() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d ' -'; }

valid_code() { # nouveau format (clé seule) ou ancien « sujet.clé »
  local c; c="$(normalize "$1")"
  [[ "$c" =~ ^[a-z0-9]{20,}$ ]] || [[ "$c" =~ ^[-_a-z0-9]{1,64}\.[a-z0-9]{20,}$ ]]
}

install_local() {
  local claude_dir="$HOME/.claude" hook
  hook="$claude_dir/hooks/claude-companion-notify.sh"
  mkdir -p "$claude_dir/hooks"
  cp "$NOTIFY" "$hook"
  chmod +x "$hook"
  # Le code est secret : fichier lisible uniquement par toi.
  ( umask 077
    cat > "$claude_dir/claude-companion.env" <<CONF
CLAUDE_COMPANION_PAIRING=$(normalize "$CODE")
CLAUDE_COMPANION_SERVER=$SERVER
CLAUDE_COMPANION_TOKEN=$TOKEN
CLAUDE_COMPANION_ACCOUNT=$ACCOUNT
CONF
  )
  [ -f "$claude_dir/settings.json" ] && cp "$claude_dir/settings.json" "$claude_dir/settings.json.bak.$(date +%s)"
  add_hooks "$claude_dir/settings.json" "$hook"
  echo "✅ Ordinateur : hook installé pour tous tes projets ($hook)"
  "$hook" "done" "Appairage réussi 🎉 Claude Companion est prêt." || true
  echo "   Une notification de test vient d'être envoyée : ouvre l'appli sur la montre."
}

# --- Mode guidé ---
if [ -z "$CODE" ] && [ ${#REPOS[@]} -eq 0 ] && [ -z "$PROJECT_DIR" ]; then
  echo "Claude Companion — installation"
  echo "Sur la montre : Paramètres > Appairer un ordinateur."
  read -rp "Code d'appairage : " CODE
  [ -n "$CODE" ] || exit 1
  if ! valid_code "$CODE"; then echo "Code invalide."; exit 1; fi
  echo
  echo "Les sessions cloud n'exécutent que les hooks commités dans le dépôt."
  read -rp "Dépôts GitHub à équiper (owner/nom séparés par des espaces, Entrée pour passer) : " -a REPOS
fi

if [ -n "$CODE" ]; then
  valid_code "$CODE" || { echo "Code d'appairage invalide (voir la montre)."; exit 1; }
  install_local
fi

if [ -n "$PROJECT_DIR" ]; then
  [ -d "$PROJECT_DIR" ] || { echo "Dossier introuvable : $PROJECT_DIR"; exit 1; }
  add_project "$PROJECT_DIR"
  echo "✅ Hook ajouté à $PROJECT_DIR/.claude/ : commite ce dossier."
fi

if [ ${#REPOS[@]} -gt 0 ]; then
  echo "Dépôts (sessions cloud) :"
  for repo in "${REPOS[@]}"; do add_repo "$repo"; done
fi

if [ ${#REPOS[@]} -gt 0 ] || [ -n "$PROJECT_DIR" ]; then
  echo
  echo "Dernière étape, une seule fois, dans l'environnement cloud (claude.ai/code > environnement > Modifier) :"
  echo "  • Accès réseau : autoriser le domaine $(printf %s "$SERVER" | sed -E "s#^[a-z]+://##; s#[/:].*##")"
  echo "  • Variable d'environnement : CLAUDE_COMPANION_PAIRING=<ton code d'appairage>"
fi
