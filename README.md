# Agentchi ⌚️

Application **Wear OS** (Galaxy Watch 4 et plus récentes) qui t'accompagne pendant que tes agents de code travaillent, **Claude Code** comme **Codex** :

- 🔔 **Leurs événements sur la montre** : demande d'autorisation, réponse attendue, tâche terminée, erreur. Ils viennent des **hooks** (ordinateurs, Remote Control, sessions cloud claude.ai/code), passent par **ton serveur** et arrivent en **push** (Firebase Cloud Messaging). Aucune appli sur le téléphone. Le Markdown des agents est mis en forme : gras, italique, `code`, titres, listes à puces.
- ⌚ **Leur répondre depuis la montre** : autoriser ou refuser une commande, choisir parmi les options proposées (une ou plusieurs), approuver un plan, ou répondre au clavier ou à la voix quand l'agent a fini.
- 🦀 **Une mascotte virtuelle à soigner** : nourris-la, joue avec elle, mets-la au lit. Elle fait la fête quand l'agent a fini et s'affole quand il t'attend. **Personnalisable** (espèce, couleurs, accessoires, icônes d'actions), avec sa **tuile**, sa **complication** et même l'**icône de l'appli** assortie.
- ⚙️ **Des paramètres rangés par catégorie** pour choisir quoi recevoir et comment : plein écran, notification normale ou discrète, motif de vibration, son, heures calmes.

> Application non officielle, sans lien avec Anthropic ni OpenAI.

## Comment ça marche

```
Claude Code (ordinateur, Remote Control) ──plugin agentchi──────────┐
Session cloud claude.ai/code ─────────────hook http du dépôt────────┼──HTTPS──▶ ton serveur ──FCM──▶ montre
Codex (CLI, appli, IDE) ──────────────────~/.codex/hooks.json───────┘   (server/)
```

Le projet contient :

- **`server/`** : un petit serveur Go à héberger chez toi. Il reçoit les hooks et les pousse aussitôt à la bonne montre par FCM, sans rien stocker. **Multi-utilisateur** : chaque montre s'y enregistre toute seule et ne reçoit que ses événements.
- **`plugins/agentchi/`** : le plugin Claude Code. Un simple `curl` : pas de script, ni `jq` ni `openssl`.
- **`plugins/agentchi-codex/`** : les mêmes hooks pour Codex (généré depuis le serveur, voir `CodexHooks`).
- **`app/`** : l'appli Wear OS, autonome. Elle reçoit les pushs, les trie et les affiche, crée les codes d'appairage et héberge la mascotte.

Chaque ordinateur ou environnement cloud est appairé avec **son propre code** (`XXXX-XXXX-XXXX`), créé sur la montre et révocable depuis elle.

| Où tourne l'agent | Comment la montre est prévenue |
|---|---|
| Claude Code : terminal, IDE, appli Desktop | Plugin `agentchi` |
| Claude Code en **Remote Control** (claude.ai/code, appli mobile) | Plugin : la session tourne sur ton ordinateur |
| **Session cloud** claude.ai/code | Hook `http` dans le `.claude/settings.json` du dépôt : les sessions cloud ne chargent pas les plugins |
| **Codex** : CLI, appli, extension IDE (macOS, Linux, WSL) | Hooks Codex (`~/.codex/hooks.json` ou plugin `agentchi-codex`) |
| Chat claude.ai, ChatGPT | Non pris en charge : pas de hooks |

## Installation

### 1. Firebase (une fois)

Le push passe par Firebase Cloud Messaging, gratuit.

1. Sur https://console.firebase.google.com, crée un projet (ou reprends le tien : son nom n'a pas d'importance), puis ajoute une **appli Android** avec le package `fr.codingpacer.agentchi`.
2. Télécharge `google-services.json`. Il ne sert qu'à lire quatre valeurs, il ne se versionne pas :

   | Variable (ou propriété Gradle) | Champ de `google-services.json` |
   |---|---|
   | `FIREBASE_APP_ID` (`-PfirebaseAppId`) | `client[0].client_info.mobilesdk_app_id` |
   | `FIREBASE_API_KEY` (`-PfirebaseApiKey`) | `client[0].api_key[0].current_key` |
   | `FIREBASE_PROJECT_ID` (`-PfirebaseProjectId`) | `project_info.project_id` |
   | `FIREBASE_SENDER_ID` (`-PfirebaseSenderId`) | `project_info.project_number` |

   Reporte-les, avec `COMPANION_SERVER` (adresse du serveur), dans `app/build.secrets.env`, chiffré avec [sops](https://github.com/getsops/sops) :

   ```bash
   sops edit app/build.secrets.env
   ```

   La CI le déchiffre avant de compiler la montre : elle produit un APK prêt à l'emploi. Une valeur laissée à `A_REMPLACER` est ignorée.
3. **Paramètres du projet › Comptes de service › Générer une nouvelle clé privée** : c'est la clé que le serveur utilise pour envoyer les pushs. Colle son contenu JSON à la place de l'exemple dans `k8s/0.secrets.yaml`, lui aussi chiffré :

   ```bash
   sops edit k8s/0.secrets.yaml
   ```

**sops** : les deux fichiers sont chiffrés pour la clé age de `.sops.yaml`, la même que Pelagoss/chess-analyzer. Pour les éditer, il faut la clé privée correspondante en local (`~/.config/sops/age/keys.txt` ou `SOPS_AGE_KEY`), et dans le dépôt GitHub le secret **`DECRYPTING_KEY`** pour que la CI puisse les lire.

### 2. Le serveur

#### Kubernetes (déploiement par défaut)

Même chaîne que Pelagoss/chess-analyzer : le workflow **Deploy** construit l'image `server/Dockerfile`, la pousse sur `harbor.codingpacer.fr/prod/agentchi-server`, déchiffre `k8s/*.secrets.yaml` avec sops puis applique `k8s/` dans le namespace `claude-companion` (nom d'origine, conservé : le volume `state.json` et le Secret chiffré y sont rattachés). Il part à chaque tag `X.Y.Z`, ou à la main (*Actions › Deploy › Run workflow*).

| Fichier | Contenu |
|---|---|
| `k8s/0.secrets.yaml` | Secret `firebase-service-account` (chiffré sops), monté en `/secrets/firebase-service-account.json` |
| `k8s/config.yaml` | Configuration du serveur, dont `CC_PUBLIC_URL` |
| `k8s/server.yaml` | Deployment (une réplique), volume persistant pour `state.json`, Service |
| `k8s/ingress.yaml` | Ingress Traefik + cert-manager sur `agentchi.codingpacer.fr` (et l'ancien `claude-companion.codingpacer.fr`, le temps de la transition) |

Secrets GitHub du dépôt nécessaires : `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `KUBE_CONFIG` et `DECRYPTING_KEY`. Pour un autre domaine, change-le dans `k8s/ingress.yaml`, `k8s/config.yaml` et `app/build.secrets.env`.

Ouvre `https://agentchi.codingpacer.fr` : la page d'accueil donne les commandes prêtes à copier, avec l'adresse du serveur.

#### Docker Compose (autre serveur)

Il faut un nom de domaine qui pointe vers ton serveur, avec les ports 80 et 443 ouverts. Caddy obtient le certificat HTTPS tout seul.

```bash
git clone https://github.com/Pelagoss/claude-companion && cd claude-companion
sops decrypt --extract '["stringData"]["firebase-service-account.json"]' k8s/0.secrets.yaml > server/firebase-service-account.json
chmod 644 server/firebase-service-account.json    # lisible par le conteneur
cd server && CC_DOMAIN=agentchi.example.fr docker compose up -d
```

Sans Docker : `go build` dans `server/`, puis lance le binaire derrière ton reverse proxy. Variables : `CC_FCM_CREDENTIALS` (chemin de la clé du compte de service, **obligatoire** pour les pushs), `CC_ADDR` (défaut `:8080`), `CC_DATA` (défaut `data/state.json`), `CC_PUBLIC_URL`, `CC_TRUST_PROXY=true` derrière un proxy, `CC_MAX_DEVICES` (défaut 1000). Derrière un reverse proxy, laisse les requêtes `POST /ask` ouvertes 5 minutes (Traefik et Caddy le font par défaut).

### 3. La montre

Onglet **Actions** du dépôt → dernier build vert → artefact `agentchi-debug`, qui contient `agentchi-montre.apk` (compilé avec les valeurs Firebase de l'étape 1) :

```bash
gh run download -R Pelagoss/claude-companion -n agentchi-debug
```

1. Active le débogage : *Paramètres → À propos de la montre → Informations sur le logiciel*, touche 7 fois *Version du logiciel*. Ensuite, dans *Options de développement*, active *Débogage ADB* et *Débogage sans fil* (montre et ordinateur sur le même Wi-Fi).
2. Installe :
   ```bash
   adb pair <ip>:<port-appairage>      # code affiché sur la montre
   adb connect <ip>:<port>
   adb install agentchi-montre.apk
   ```
3. Ouvre **Agentchi** sur la montre, accepte les notifications, puis **Paramètres › Serveur › Ordinateurs et cloud** : saisis l'adresse du serveur et touche **Connecter la montre**.

La montre doit avoir les services Google Play (c'est le cas des Galaxy Watch). Elle reçoit les pushs en Wi-Fi, en LTE ou via le téléphone auquel elle est appairée.

Pour compiler toi-même : `./gradlew :app:assembleDebug -PcompanionServer=https://agentchi.codingpacer.fr -PfirebaseAppId=… -PfirebaseApiKey=… -PfirebaseProjectId=… -PfirebaseSenderId=…`.

### 4. Claude Code, sur chaque ordinateur

Sur la montre : **Paramètres › Serveur › Ordinateurs et cloud › Nouveau code**, nomme l'ordinateur et note le code. Puis, sur l'ordinateur :

```bash
claude plugin install agentchi --marketplace Pelagoss/claude-companion \
  --config server_url=https://agentchi.codingpacer.fr --config code=XXXX-XXXX-XXXX
```

C'est tout, pour tous tes projets. Le code est rangé dans le trousseau du système, pas dans `settings.json`. Sans `--config`, Claude Code demande les deux valeurs (`/plugin` › agentchi › *Configure options*). Le plugin couvre aussi les sessions locales que tu suis en **Remote Control** depuis claude.ai/code ou l'appli mobile.

Tu peux aussi ajouter la marketplace `Pelagoss/claude-companion` dans **claude.ai › Personnaliser › Plugins** : le plugin se synchronise alors sur chaque ordinateur où tu te connectes à Claude Code. Il ne reste qu'à saisir le code.

### 5. Sessions cloud Claude (claude.ai/code)

Les sessions cloud ne chargent pas les plugins, mais elles exécutent les hooks du dépôt :

1. Ajoute à `.claude/settings.json` du dépôt le bloc que donne `https://agentchi.codingpacer.fr/v1/cloud-settings.json`. C'est un hook `http` qui envoie `Authorization: Bearer $AGENTCHI_CODE`.
2. Sur la montre, crée un code pour cet environnement cloud.
3. Dans les paramètres de l'environnement cloud sur claude.ai/code :
   - variable d'environnement `AGENTCHI_CODE=XXXX-XXXX-XXXX` ;
   - accès réseau **personnalisé** qui autorise `agentchi.codingpacer.fr`.

Chaque personne qui travaille sur le dépôt met **son propre code** dans **son** environnement. Sans code, le hook ne fait rien et n'affiche pas d'erreur. Sur un ordinateur, la variable n'est pas définie, donc pas de doublon avec le plugin.

### 6. Codex

Codex lit ses hooks dans `~/.codex/hooks.json` (CLI, appli et extension IDE ; macOS, Linux ou WSL). Le serveur les fournit, avec son adresse :

```bash
curl -fsS https://agentchi.codingpacer.fr/v1/codex-hooks.json -o ~/.codex/hooks.json
echo 'export AGENTCHI_CODE=XXXX-XXXX-XXXX' >> ~/.zshrc   # ou ~/.bashrc
```

Si tu as déjà un `~/.codex/hooks.json`, fusionne les deux plutôt que de l'écraser. Codex ignore un hook tant que tu ne l'as pas approuvé : lance **`/hooks`** dans Codex et approuve ceux d'Agentchi.

Dans l'appli Codex (desktop), tu peux aussi installer le plugin `agentchi-codex` : `codex plugin marketplace add Pelagoss/claude-companion`, puis l'installer depuis le répertoire des plugins. Il lit le code dans `AGENTCHI_CODE` et l'adresse dans `AGENTCHI_SERVER` (défaut `https://agentchi.codingpacer.fr`).

Avec Codex, la montre reçoit les fins de tâche, les demandes d'autorisation (commandes, modifications de fichiers, outils MCP) et peut y répondre. Ses questions à choix (`request_user_input`) restent sur l'ordinateur : Codex ne les passe pas aux hooks.

### Vérifier

**Paramètres › Serveur › Ordinateurs et cloud › Tester le push** : le serveur envoie une notification par FCM et affiche l'erreur sur la montre si elle ne part pas (clé du compte de service absente, jeton refusé…). Firebase garde un push 24 h si la montre est éteinte ou hors ligne.

## Répondre depuis la montre

Sur la montre : **Paramètres › Réponses › Répondre depuis la montre**. Active-le quand tu t'éloignes de l'écran, et désactive-le quand tu y reviens.

| Quand l'agent… | Hook | Sur la montre |
|---|---|---|
| demande une autorisation (« 1. Oui / 2. Oui, ne plus demander / 3. Non ») | `PermissionRequest` | ✅ Autoriser, ♾️ Toujours autoriser (Claude Code), ❌ Refuser, avec ou sans explication |
| pose une question à choix (Claude Code : `AskUserQuestion`, cases à cocher comprises) | `PreToolUse` | Options à cocher, une ou plusieurs, ou ✍️ autre réponse |
| propose un plan (Claude Code : `ExitPlanMode`) | `PreToolUse` | ✅ Approuver, ✍️ Demander des changements |
| a fini et attend ta réponse | `Stop` | 💬 Réponse au clavier, à la voix ou rapide (« Continue »…), ou 🏁 Terminer |

Les réponses simples se donnent depuis la notification. Touche-la pour l'écran complet : texte entier, choix multiples, compte à rebours. La **tuile** et la **complication** signalent aussi une question en attente et y mènent d'un toucher. L'**historique** garde ce que tu as répondu sous chaque question.

Le hook attend ta réponse **1, 2 ou 5 minutes** selon le réglage *Attendre ma réponse* (2 par défaut). Les **réponses rapides** se modifient dans *Réponses › Réponses rapides*. Passé le délai, ou si tu **balaies la notification**, l'agent pose la question à l'écran comme d'habitude. Réglage désactivé, le serveur rend la main tout de suite : rien ne change sur l'ordinateur, la fin de tâche reste une simple notification.

## La mascotte

| Jauge | Baisse | Remonte |
|---|---|---|
| 🍪 Satiété | −6 / h | Nourrir (+25) |
| ⚡ Énergie | −5 / h | Dormir (+15 / h) |
| 💖 Bonheur | −4 / h | Jouer (+20), la caresser (tap), chaque tâche terminée par un agent (+10) |

Chaque tâche terminée rapporte aussi de l'XP (+10). La mascotte monte d'un niveau tous les 100 XP.

**Paramètres › Mascotte**, avec un aperçu en direct :

- **Nom**, et **espèce** : Crabe, Points (un nuage de points) ou Fantôme ;
- **Couleurs** : Corail, Encre, Océan, Menthe, Lilas, Rose ;
- **Accessoire** : chapeau de fête, nœud, lunettes de soleil (niveau 2), haut-de-forme (niveau 3), couronne (niveau 5) ;
- **Icônes d'actions** : pixel-art assorti à la mascotte, ou emojis ;
- **Icône de l'appli** : classique, ou assortie à la mascotte (espèce et couleurs). Le lanceur la change en quelques secondes.

Tout le dessin vient de `MascotSprite` (grille pixel-art) : l'appli, la tuile et les icônes d'appli (générées par `AppIconsTest`) restent toujours d'accord.

- **Tuile** (balaie le cadran, puis *+* › Agentchi) : la mascotte, son humeur et ses jauges. Quand un agent attend une réponse, la tuile affiche la question avec un bouton **Répondre**.
- **Complication** *Mascotte Agentchi* (appui long sur le cadran › Modifier) : forme moyenne de la mascotte en jauge ou en texte court, 🔔 quand un agent attend.

## Publier sur le Play Store

Le guide pas à pas est dans [`docs/PUBLICATION_PLAY_STORE.md`](docs/PUBLICATION_PLAY_STORE.md), et la politique de confidentialité dans [`docs/PRIVACY.md`](docs/PRIVACY.md).

## Confidentialité

- **Serveur** : il est à toi. Il reçoit les événements en HTTPS (nom du dossier, identifiant court de session, 300 premiers caractères du dernier message de l'agent) et les transmet aussitôt à Firebase, sans les stocker. Les hooks envoient du JSON brut : pas de chiffrement de bout en bout, la confiance repose sur ton serveur et sur Firebase Cloud Messaging (Google), qui achemine le push.
- **Réponses** : une question en attente ne vit qu'en mémoire, le temps de la réponse. Seule la montre qui l'a reçue peut y répondre, une seule fois. Une montre qui répond peut autoriser des commandes : désactive le réglage si tu la prêtes.
- **Codes et jetons** : le serveur n'en garde que l'empreinte SHA-256. Chaque montre ne lit que ses événements. Les tentatives avec un code invalide sont limitées.

## Développement

- Montre : Kotlin, Jetpack Compose (Wear OS Material), tuiles ProtoLayout, complications, Firebase Cloud Messaging, DataStore, OkHttp
- Serveur : Go, bibliothèque standard uniquement
- `./gradlew :app:testDebugUnitTest` : mascotte et sprites, icônes d'appli à jour, lecture des pushs, filtres, Markdown
- `cd server && go test ./...` : traduction des hooks (Claude Code, Codex), réponses depuis la montre, appairage, isolation entre montres, envoi FCM (JWT, OAuth, jeton périmé), persistance, plugin Codex à jour
- `claude plugin validate .` : marketplace et plugin Claude Code
- Fichiers générés : `UPDATE_CODEX_HOOKS=1 go test -run TestPluginCodex` (dans `server/`) et `UPDATE_APP_ICONS=1 ./gradlew :app:testDebugUnitTest --tests '*AppIconsTest*'`

```
app/              appli Wear OS : push/ (FCM), net/ (API du serveur), notify/, pet/ (logique et sprites), sync/, text/, ui/, widget/ (tuile, complication, icônes)
server/           serveur Go : hooks → FCM, réponses, API d'appairage, Docker + Caddy
plugins/          plugin Claude Code agentchi (.claude-plugin/marketplace.json) et Codex agentchi-codex (.agents/plugins/marketplace.json)
keystore/         clé de debug (même signature d'un build à l'autre)
```

## Idées pour la suite

- Répondre à plusieurs sessions en parallèle depuis la tuile
- D'autres espèces et accessoires de mascotte
