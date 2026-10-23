# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Développeurs qui font tourner des agents de code** (Claude Code, Codex) et portent une montre Wear OS : ils veulent s'éloigner de l'écran sans rater une demande d'autorisation, une question ou une fin de tâche.
- **Utilisateurs déjà équipés** qui arrivent sur la page d'accueil de leur serveur Agentchi pour appairer un ordinateur, une session cloud ou Codex.

## Product Purpose

Agentchi relaie les événements des agents de code (hooks Claude Code et Codex) vers une montre Wear OS par Firebase Cloud Messaging, permet d'y répondre depuis le poignet (autoriser, refuser, choisir, approuver un plan, dicter), et fait vivre une mascotte pixel-art au rythme des sessions. Succès : l'utilisateur installe l'appli, appaire ses ordinateurs et répond depuis sa montre.

## Positioning

Le seul compagnon de poignet pour agents de code qui combine réponses actionnables depuis la montre et une mascotte pixel-art à soigner (style Tamagotchi, sans le nom), avec un serveur open source auto-hébergé, sans compte ni publicité.

## Operating Context

- Montre Wear OS avec services Google Play (Galaxy Watch 4 et plus récentes) ; aucune appli téléphone.
- Appairage : la montre crée un code `XXXX-XXXX-XXXX` par ordinateur ou environnement cloud, révocable depuis elle.
- Claude Code (terminal, IDE, Desktop, Remote Control) via le plugin `agentchi` ; sessions cloud claude.ai/code via un hook http dans `.claude/settings.json` ; Codex via `~/.codex/hooks.json`.
- Chaque serveur auto-hébergé sert sa propre page d'accueil (`/`) avec son URL publique injectée dans les commandes.

## Capabilities and Constraints

- Serveur Go (`server/`), binaire statique distroless ; la page d'accueil est rendue par `html/template`.
- Mascotte : 3 espèces (Crabe, Points, Fantôme), 6 couleurs (Corail, Encre, Océan, Menthe, Lilas, Rose), 5 accessoires ; grilles portées en JS dans `Stores/source/mascot.js`.
- L'appli est en français uniquement.
- Pas encore de lien Play Store publié : l'appel à l'action de la page d'accueil mène au guide d'installation.

## Brand Commitments

- Nom : Agentchi. Ton : tutoiement, français simple et direct.
- Marques : jamais « Claude », « Codex », « OpenAI » ni « Tamagotchi » dans le titre ou l'icône ; citées seulement pour la compatibilité, avec la mention « appli non officielle, sans lien avec Anthropic ni OpenAI ».
- Identité des visuels des stores (`Stores/assets/`) : mascottes pixel-art, accent corail, fond sombre chaud.

## Evidence on Hand

- `Stores/assets/` : icônes 512, image de présentation 1024×500, 8 captures Wear OS 900×900, 4 visuels promo 9:16, vidéo 43 s + poster.
- Textes des fiches : `Stores/FICHE_STORES.md`.
- Aucun avis, chiffre d'utilisateurs ni témoignage : n'en inventer aucun.

## Product Principles

1. La montre d'abord : tout ce qu'on montre doit pouvoir se faire depuis le poignet.
2. Ton serveur, tes données : auto-hébergé, les événements ne sont pas conservés (seules les montres enregistrées et leurs codes le sont), pas de compte.
3. Le compagnon donne envie, l'utilité fait rester.
4. Honnêteté sur la compatibilité et les limites (ex. Codex ne passe pas les questions à choix).
