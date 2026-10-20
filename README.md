# Claude Companion ⌚️

Application **Wear OS** (Galaxy Watch 4 et plus récentes) qui t'accompagne pendant que Claude travaille :

- 🔔 **Les événements de Claude Code** sur la montre : demande d'autorisation, réponse attendue, tâche terminée, erreur. Ils viennent des **hooks Claude Code** (ordinateurs, Remote Control, sessions cloud claude.ai/code) via **ton serveur**, sans appli téléphone. Ça marche aussi avec un iPhone.
- 📱 En option, **les notifications de l'appli Claude** (conversations du chat) relayées par le téléphone Android.
- 🦀 **Une mascotte façon tamagotchi** : nourris-la, joue avec elle, mets-la au lit. Elle réagit en direct : elle fait la fête quand Claude a fini et s'affole quand il t'attend.
- ⚙️ **Des paramètres** pour choisir quoi recevoir et comment : plein écran, notification normale ou discrète, motif de vibration, son, heures calmes.

> Application non officielle, sans lien avec Anthropic.

## Comment ça marche

```
Claude Code (ordinateur, Remote Control) ──plugin watch-companion──┐
                                                                    ├──HTTPS──▶ ton serveur ──▶ montre
Session cloud claude.ai/code ─────────hook http du dépôt──────────┘   (server/)      (Wi-Fi / LTE)
```

Le projet contient :

- **`server/`** : un petit serveur Go à héberger chez toi. Il reçoit les hooks, garde les événements 48 h et les sert à la montre. **Multi-utilisateur** : chaque montre s'y enregistre toute seule et ne voit que ses événements.
- **`plugins/watch-companion/`** : le plugin Claude Code qui envoie les événements au serveur. Un simple `curl` : pas de script, ni `jq` ni `openssl`.
- **`app/`** : l'appli montre. Elle reçoit, trie et affiche les événements, crée les codes d'appairage et héberge la mascotte.
- **`phone/`** (facultatif) : un relais Android qui transmet aussi les notifications de l'appli Claude (chat) à la montre.

Chaque ordinateur ou environnement cloud est appairé avec **son propre code** (`XXXX-XXXX-XXXX`), créé sur la montre et révocable depuis elle.

| Où tourne Claude | Comment la montre est prévenue |
|---|---|
| Terminal, IDE, appli Desktop (local) | Plugin `watch-companion` |
| Session locale pilotée en **Remote Control** (claude.ai/code, appli mobile) | Plugin : la session tourne sur ton ordinateur |
| **Session cloud** claude.ai/code | Hook `http` dans le `.claude/settings.json` du dépôt : les sessions cloud ne chargent pas les plugins |
| Chat claude.ai | Pas de hook : relais téléphone facultatif |

## Installation

### 1. Le serveur

Il faut un nom de domaine qui pointe vers ton serveur, avec les ports 80 et 443 ouverts. Caddy obtient le certificat HTTPS tout seul.

```bash
git clone https://github.com/Pelagoss/claude-companion && cd claude-companion/server
CC_DOMAIN=cc.example.fr docker compose up -d
```

Ouvre `https://cc.example.fr` : la page d'accueil donne les commandes prêtes à copier, avec ton adresse.

Sans Docker : `go build` dans `server/`, puis lance le binaire derrière ton reverse proxy. Variables : `CC_ADDR` (défaut `:8080`), `CC_DATA` (défaut `data/state.json`), `CC_PUBLIC_URL`, `CC_TRUST_PROXY=true` derrière un proxy, `CC_MAX_DEVICES` (défaut 1000). Le reverse proxy ne doit pas mettre le flux en tampon (Caddy : `flush_interval -1`).

### 2. La montre

Onglet **Actions** du dépôt → dernier build vert → artefact `claude-companion-debug`, qui contient `claude-companion-montre.apk` :

```bash
gh run download -R Pelagoss/claude-companion -n claude-companion-debug
```

1. Active le débogage : *Paramètres → À propos de la montre → Informations sur le logiciel*, touche 7 fois *Version du logiciel*. Ensuite, dans *Options de développement*, active *Débogage ADB* et *Débogage sans fil* (montre et ordinateur sur le même Wi-Fi).
2. Installe :
   ```bash
   adb pair <ip>:<port-appairage>      # code affiché sur la montre
   adb connect <ip>:<port>
   adb install claude-companion-montre.apk
   ```
3. Ouvre **Claude Companion** sur la montre, accepte les notifications, puis **Paramètres › Ordinateurs et cloud** : saisis l'adresse du serveur et touche **Connecter la montre**.

Pour ne pas avoir à saisir l'adresse sur la montre, compile l'appli avec ton serveur par défaut : `./gradlew :app:assembleDebug -PcompanionServer=https://cc.example.fr`.

### 3. Chaque ordinateur

Sur la montre : **Ordinateurs et cloud › Nouveau code**, nomme l'ordinateur et note le code. Puis, sur l'ordinateur :

```bash
claude plugin install watch-companion --marketplace Pelagoss/claude-companion \
  --config server_url=https://cc.example.fr --config code=XXXX-XXXX-XXXX
```

C'est tout, pour tous tes projets. Le code est rangé dans le trousseau du système, pas dans `settings.json`. Sans `--config`, Claude Code demande les deux valeurs (`/plugin` › watch-companion › *Configure options*). Le plugin couvre aussi les sessions locales que tu suis en **Remote Control** depuis claude.ai/code ou l'appli mobile.

Tu peux aussi ajouter la marketplace `Pelagoss/claude-companion` dans **claude.ai › Personnaliser › Plugins** : le plugin se synchronise alors sur chaque ordinateur où tu te connectes à Claude Code. Il ne reste qu'à saisir le code.

### 4. Sessions cloud (claude.ai/code)

Les sessions cloud ne chargent pas les plugins, mais elles exécutent les hooks du dépôt :

1. Ajoute à `.claude/settings.json` du dépôt le bloc que donne `https://cc.example.fr/v1/cloud-settings.json`. C'est un hook `http` qui envoie `Authorization: Bearer $CLAUDE_COMPANION_CODE`.
2. Sur la montre, crée un code pour cet environnement cloud.
3. Dans les paramètres de l'environnement cloud sur claude.ai/code :
   - variable d'environnement `CLAUDE_COMPANION_CODE=XXXX-XXXX-XXXX` ;
   - accès réseau **personnalisé** qui autorise `cc.example.fr`.

Chaque personne qui travaille sur le dépôt met **son propre code** dans **son** environnement. Sans code, le hook ne fait rien et n'affiche pas d'erreur. Sur un ordinateur, la variable n'est pas définie, donc pas de doublon avec le plugin.

### Réception sur la montre

La montre récupère les événements toutes les 15 minutes et à l'ouverture de l'appli, ou en quelques secondes avec **⚡ Mode instantané** (connexion permanente, plus gourmande en batterie). **Ordinateurs et cloud › Tester le serveur** vérifie toute la chaîne.

## Option : notifications de l'appli Claude (téléphone Android)

Le chat claude.ai n'a pas de hooks. Pour recevoir aussi ses notifications, installe le relais téléphone (`claude-companion-telephone.apk`, du **même build** que la montre : les deux APK doivent être signés avec la même clé, `keystore/debug.keystore`).

```bash
adb install claude-companion-telephone.apk
```

Ouvre **Claude Companion** sur le téléphone :
1. **Accès aux notifications** → *Autoriser*, puis active *Relais Claude Companion*. Si Android affiche « paramètre restreint » : *Paramètres → Applications → Claude Companion → ⋮ → Autoriser les paramètres restreints*, puis recommence.
2. Vérifie que **Montre connectée** affiche ta Galaxy Watch.
3. Touche **Envoyer un test à la montre**.

**Notifications seulement sur la montre** : active **4. Seulement sur la montre**. Le relais retire alors la notification Claude du téléphone dès que la montre l'a reçue ; si la montre est injoignable, elle reste sur le téléphone. Pour que le téléphone ne sonne pas non plus, règle les notifications de l'appli Claude sur **Silencieux** dans les paramètres Android. Ne les **désactive pas** : le relais ne les verrait plus. Pour éviter les doublons, désactive l'appli **Claude** dans **Galaxy Wearable → Notifications**.

Le type de ces notifications (🔐 autorisation, 💬 réponse attendue, ✅ terminé, ⚠️ erreur) est **déduit de leur texte**.

## La mascotte

| Jauge | Baisse | Remonte |
|---|---|---|
| 🍪 Satiété | −6 / h | Nourrir (+25) |
| ⚡ Énergie | −5 / h | Dormir (+15 / h) |
| 💖 Bonheur | −4 / h | Jouer (+20), la caresser (tap), chaque tâche terminée par Claude (+10) |

Chaque tâche terminée rapporte aussi de l'XP (+10). La mascotte monte d'un niveau tous les 100 XP. Tu peux la renommer dans les paramètres.

## Publier sur le Play Store

Le guide pas à pas est dans [`docs/PUBLICATION_PLAY_STORE.md`](docs/PUBLICATION_PLAY_STORE.md), et la politique de confidentialité dans [`docs/PRIVACY.md`](docs/PRIVACY.md).

## Confidentialité

- **Serveur** : il est à toi. Il reçoit les événements en HTTPS (nom du dossier, identifiant court de session, 300 premiers caractères du dernier message de Claude) et les garde 48 h au plus. Les hooks Claude Code envoient du JSON brut : pas de chiffrement de bout en bout, la confiance repose sur ton serveur.
- **Codes et jetons** : le serveur n'en garde que l'empreinte SHA-256. Chaque montre ne lit que ses événements. Les tentatives avec un code invalide sont limitées.
- **Relais téléphone (facultatif)** : seules les notifications de l'appli Claude (`com.anthropic.claude`) sont lues. Elles passent directement du téléphone à la montre par la liaison Wear OS.

## Développement

- Montre et téléphone : Kotlin, Jetpack Compose (Wear OS Material sur la montre, Material 3 sur le téléphone), Wearable Data Layer, DataStore, WorkManager, OkHttp
- Serveur : Go, bibliothèque standard uniquement
- `./gradlew :app:testDebugUnitTest :phone:testDebugUnitTest` : mascotte, lecture des événements, filtres
- `cd server && go test ./...` : traduction des hooks, appairage, isolation entre montres, flux, persistance
- `claude plugin validate .` : marketplace et plugin

```
app/              appli montre : relay/ (réception téléphone), notify/, pet/, ui/, net/ + sync/ (serveur)
phone/            appli téléphone (facultative) : relais des notifications de l'appli Claude
server/           serveur Go : hooks → montre, API d'appairage, Docker + Caddy
plugins/          plugin Claude Code watch-companion (marketplace : .claude-plugin/marketplace.json)
keystore/         clé de debug partagée (requise pour que montre et téléphone communiquent)
```

## Idées pour la suite

- Répondre (*Autoriser / Refuser*) directement depuis la montre via les actions des notifications de l'appli Claude
- Tuile Wear OS et complication avec l'humeur de la mascotte
