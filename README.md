# Claude Companion ⌚️

Application **Wear OS** (Galaxy Watch 4 et plus récentes) qui t'accompagne pendant tes sessions **Claude Code** :

- 🔔 **Notifications** sur la montre quand Claude a besoin d'une autorisation, attend ta réponse, a terminé sa tâche… et tous les autres événements de session.
- 🦀 **Une mascotte façon tamagotchi** : nourris-la, joue avec elle, mets-la au lit. Elle réagit en direct à tes sessions : elle fait la fête quand une tâche se termine et s'affole quand Claude t'attend.
- ⚙️ **Des paramètres** pour choisir quels événements te notifier et comment : plein écran, notification normale ou discrète, motif de vibration, son, heures calmes.

> Application non officielle, sans lien avec Anthropic.

## Comment ça marche

```
Claude Code ──hook──▶ claude-companion-notify.sh ──chiffré──▶ ntfy ──▶ Galaxy Watch (déchiffre + vérifie)
```

Claude Code déclenche des [hooks](https://docs.claude.com/en/docs/claude-code/hooks) à chaque événement de session. Le script `hooks/claude-companion-notify.sh` publie l'événement sur un sujet [ntfy](https://ntfy.sh) (service de notifications push gratuit, open source et auto-hébergeable). La montre est abonnée à ce sujet. Aucun compte n'est nécessaire, et aucune application téléphone non plus : la montre se connecte directement en Wi-Fi ou LTE, ou via le Bluetooth du téléphone.

| Événement Claude Code | Sur la montre | Urgent | Activé par défaut |
|---|---|:-:|:-:|
| `Notification` (autorisation) | 🔐 Demande d'autorisation | ✔︎ | ✔︎ |
| `Notification` (inactivité / question) | 💬 Claude attend ta réponse | ✔︎ | ✔︎ |
| `Stop` | ✅ Tâche terminée (avec le dernier message de Claude) | | ✔︎ |
| `SubagentStop` | 🤖 Sous-agent terminé | | |
| `SessionStart` | 🚀 Session démarrée | | |
| `SessionEnd` | 🏁 Session terminée | | ✔︎ |
| `PreCompact` | 🗜️ Compaction du contexte | | |
| manuel (`notify.sh error …`) | ⚠️ Erreur | ✔︎ | ✔︎ |

## Installation

### 1. La montre

1. Récupère l'APK : onglet **Actions** du dépôt → dernier build → artefact `claude-companion-debug`. Tu peux aussi le compiler avec `./gradlew assembleDebug`.
2. Active le débogage sans fil sur la montre : *Paramètres → À propos → Informations logicielles*, appuie 7 fois sur *Version du logiciel*, puis *Options de développement → Débogage ADB* et *Débogage sans fil*.
3. Installe l'APK :
   ```bash
   adb pair <ip>:<port-appairage>      # code affiché sur la montre
   adb connect <ip>:<port>
   adb install app-debug.apk
   ```
4. Ouvre **Claude Companion** et accepte les notifications. Va dans **Paramètres → 🔗 Appairer un ordinateur** : la montre affiche un **code d'appairage** (et son QR code) de la forme `claude-companion-k7m2xq9pza.4hx…`.

### 2. Claude Code (sur ton ordinateur)

`jq`, `curl` et `openssl` sont requis.

```bash
git clone https://github.com/Pelagoss/claude-companion && cd claude-companion
./hooks/install.sh <code-appairage>
# options : --account "Pro"  --server https://ntfy.mondomaine.fr  --token tk_xxx
```

Le script copie le hook dans `~/.claude/hooks/`, enregistre ta configuration dans `~/.claude/claude-companion.env` et ajoute les hooks à `~/.claude/settings.json`. Tes autres réglages et hooks sont conservés, et une sauvegarde est créée. Pour une configuration manuelle, voir `hooks/settings.example.json`.

Pour tester :

```bash
~/.claude/hooks/claude-companion-notify.sh done "Bonjour depuis Claude Code 👋"
```

Puis, sur la montre, ouvre l'appli ou appuie sur **Paramètres → Synchroniser**.

## Plusieurs comptes, plusieurs personnes

**Chaque montre a son propre code d'appairage**, généré au hasard au premier lancement. Il contient un sujet ntfy et une **clé secrète de 130 bits**.

- **Plusieurs personnes** : chacune installe l'appli sur sa montre et lance `install.sh` avec *son* code. Les événements sont chiffrés et signés avec la clé de la montre destinataire. Une montre ignore tout message qu'elle ne peut pas vérifier : celui d'une autre personne, un message forgé par un tiers qui connaîtrait le sujet, ou un message lié à un ancien code.
- **Plusieurs comptes Claude** (perso / pro, plusieurs ordinateurs) : lance `install.sh` avec le même code sur chaque ordinateur. Chaque événement porte le compte Claude connecté (l'e-mail lu dans `~/.claude.json`, ou le nom passé avec `--account`). Dans **Paramètres → Comptes Claude**, la montre liste les comptes vus, et tu choisis ceux dont tu veux les notifications.
- **Révoquer** : *Appairer un ordinateur → Nouveau code* change le sujet et la clé. Les ordinateurs déjà appairés ne peuvent plus rien envoyer tant que tu ne relances pas `install.sh` avec le nouveau code.

## Délai de réception

| Mode | Délai | Batterie |
|---|---|---|
| **Économie** (par défaut) | jusqu'à 15 min, et immédiat à l'ouverture de l'appli | négligeable |
| **⚡ Instantané** | quelques secondes (connexion permanente) | plus élevée |

Le mode instantané s'active dans **Paramètres → Connexion**. Il est surtout utile pendant une longue session où tu attends que Claude ait besoin de toi.

## Confidentialité

- **Chiffrement de bout en bout** (AES-256-CBC + HMAC-SHA256, encrypt-then-MAC) : le serveur ntfy ne voit qu'un bloc chiffré. Le projet, le compte et le message de Claude ne sont lisibles que par la montre.
- Le code d'appairage est secret : il est stocké sur la montre et dans `~/.claude/claude-companion.env` (lisible par toi seul). Il n'est **jamais** dans ce dépôt, qui peut donc rester public.
- Désactive **Aperçu du message** pour masquer le texte sur l'écran de la montre.
- Pour garder le contrôle total, [auto-héberge ntfy](https://docs.ntfy.sh/install/) avec contrôle d'accès, puis renseigne le **Serveur** et le **Jeton** sur la montre et dans `install.sh <code> --server … --token …`.

## La mascotte

| Jauge | Baisse | Remonte |
|---|---|---|
| 🍪 Satiété | −6 / h | Nourrir (+25) |
| ⚡ Énergie | −5 / h | Dormir (+15 / h) |
| 💖 Bonheur | −4 / h | Jouer (+20), la caresser (tap), chaque tâche terminée par Claude (+10) |

Chaque tâche terminée rapporte aussi de l'XP (+10). La mascotte monte d'un niveau tous les 100 XP. Tu peux la renommer dans les paramètres.

## Développement

- Kotlin, Jetpack Compose pour Wear OS (Material), DataStore, WorkManager, OkHttp, kotlinx.serialization
- `minSdk 30` (Wear OS 3), `targetSdk 34`
- `./gradlew testDebugUnitTest` lance les tests : mascotte, heures calmes, et déchiffrement de vrais messages produits par le hook avec `openssl`.

```
app/src/main/java/com/claudecompanion/
├── data/      modèle d'événements, paramètres, persistance DataStore
├── net/       client ntfy (sondage + flux), parsing, chiffrement
├── notify/    canaux, notifications, vibrations
├── pet/       logique du tamagotchi (pure Kotlin, testée)
├── sync/      WorkManager (15 min), service instantané, redémarrage
└── ui/        écrans Compose : mascotte, paramètres, appairage (QR), historique, alerte
hooks/         script de hook Claude Code + installateur
```

## Idées pour la suite

- Répondre depuis la montre (*Autoriser / Refuser*) via un sujet de retour lu par un hook `PreToolUse`
- Tuile Wear OS et complication avec l'humeur de la mascotte
- Plusieurs sessions en parallèle, avec un regroupement par projet
