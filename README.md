# Claude Companion ⌚️

Application **Wear OS** (Galaxy Watch 4 et plus récentes) qui t'accompagne pendant que Claude travaille :

- 🔔 **Les événements de Claude Code** sur la montre : demande d'autorisation, réponse attendue, tâche terminée, erreur. Ils viennent des **hooks Claude Code** (ordinateurs, Remote Control, sessions cloud claude.ai/code), passent par **ton serveur** et arrivent en **push** (Firebase Cloud Messaging). Aucune appli sur le téléphone.
- ⌚ **Répondre à Claude depuis la montre** : autoriser ou refuser une commande, choisir parmi les options qu'il propose (une ou plusieurs), approuver son plan, ou lui répondre au clavier ou à la voix quand il a fini.
- 🦀 **Une mascotte façon tamagotchi** : nourris-la, joue avec elle, mets-la au lit. Elle réagit en direct : elle fait la fête quand Claude a fini et s'affole quand il t'attend.
- ⚙️ **Des paramètres** pour choisir quoi recevoir et comment : plein écran, notification normale ou discrète, motif de vibration, son, heures calmes.

> Application non officielle, sans lien avec Anthropic.

## Comment ça marche

```
Claude Code (ordinateur, Remote Control) ──plugin watch-companion──┐
                                                                    ├──HTTPS──▶ ton serveur ──FCM──▶ montre
Session cloud claude.ai/code ─────────hook http du dépôt──────────┘   (server/)
```

Le projet contient :

- **`server/`** : un petit serveur Go à héberger chez toi. Il reçoit les hooks et les pousse aussitôt à la bonne montre par FCM, sans rien stocker. **Multi-utilisateur** : chaque montre s'y enregistre toute seule et ne reçoit que ses événements.
- **`plugins/watch-companion/`** : le plugin Claude Code qui envoie les événements au serveur. Un simple `curl` : pas de script, ni `jq` ni `openssl`.
- **`app/`** : l'appli Wear OS, autonome. Elle reçoit les pushs, les trie et les affiche, crée les codes d'appairage et héberge la mascotte.

Chaque ordinateur ou environnement cloud est appairé avec **son propre code** (`XXXX-XXXX-XXXX`), créé sur la montre et révocable depuis elle.

| Où tourne Claude | Comment la montre est prévenue |
|---|---|
| Terminal, IDE, appli Desktop (local) | Plugin `watch-companion` |
| Session locale pilotée en **Remote Control** (claude.ai/code, appli mobile) | Plugin : la session tourne sur ton ordinateur |
| **Session cloud** claude.ai/code | Hook `http` dans le `.claude/settings.json` du dépôt : les sessions cloud ne chargent pas les plugins |
| Chat claude.ai | Non pris en charge : le chat n'a pas de hooks |

## Installation

### 1. Firebase (une fois)

Le push passe par Firebase Cloud Messaging, gratuit.

1. Sur https://console.firebase.google.com, crée un projet, puis ajoute une **appli Android** avec le package `fr.codingpacer.claudecompanion`.
2. Télécharge `google-services.json`. Il ne sert qu'à lire quatre valeurs, il ne se versionne pas :

   | Variable (ou propriété Gradle) | Champ de `google-services.json` |
   |---|---|
   | `FIREBASE_APP_ID` (`-PfirebaseAppId`) | `client[0].client_info.mobilesdk_app_id` |
   | `FIREBASE_API_KEY` (`-PfirebaseApiKey`) | `client[0].api_key[0].current_key` |
   | `FIREBASE_PROJECT_ID` (`-PfirebaseProjectId`) | `project_info.project_id` |
   | `FIREBASE_SENDER_ID` (`-PfirebaseSenderId`) | `project_info.project_number` |

   Reporte-les, avec `COMPANION_SERVER`, dans `app/build.secrets.env`, chiffré avec [sops](https://github.com/getsops/sops) :

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

Même chaîne que Pelagoss/chess-analyzer : le workflow **Deploy** construit l'image `server/Dockerfile`, la pousse sur `harbor.codingpacer.fr/prod/claude-companion-server`, déchiffre `k8s/*.secrets.yaml` avec sops puis applique `k8s/` dans le namespace `claude-companion`. Il part à chaque tag `X.Y.Z`, ou à la main (*Actions › Deploy › Run workflow*).

| Fichier | Contenu |
|---|---|
| `k8s/0.secrets.yaml` | Secret `firebase-service-account` (chiffré sops), monté en `/secrets/firebase-service-account.json` |
| `k8s/config.yaml` | Configuration du serveur, dont `CC_PUBLIC_URL` |
| `k8s/server.yaml` | Deployment (une réplique), volume persistant pour `state.json`, Service |
| `k8s/ingress.yaml` | Ingress Traefik + cert-manager sur `claude-companion.codingpacer.fr` |

Secrets GitHub du dépôt nécessaires : `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `KUBE_CONFIG` et `DECRYPTING_KEY`. Pour un autre domaine, change-le dans `k8s/ingress.yaml`, `k8s/config.yaml` et `app/build.secrets.env`.

Ouvre `https://claude-companion.codingpacer.fr` : la page d'accueil donne les commandes prêtes à copier, avec l'adresse du serveur.

#### Docker Compose (autre serveur)

Il faut un nom de domaine qui pointe vers ton serveur, avec les ports 80 et 443 ouverts. Caddy obtient le certificat HTTPS tout seul.

```bash
git clone https://github.com/Pelagoss/claude-companion && cd claude-companion
sops decrypt --extract '["stringData"]["firebase-service-account.json"]' k8s/0.secrets.yaml > server/firebase-service-account.json
chmod 644 server/firebase-service-account.json    # lisible par le conteneur
cd server && CC_DOMAIN=cc.example.fr docker compose up -d
```

Sans Docker : `go build` dans `server/`, puis lance le binaire derrière ton reverse proxy. Variables : `CC_FCM_CREDENTIALS` (chemin de la clé du compte de service, **obligatoire** pour les pushs), `CC_ADDR` (défaut `:8080`), `CC_DATA` (défaut `data/state.json`), `CC_PUBLIC_URL`, `CC_TRUST_PROXY=true` derrière un proxy, `CC_MAX_DEVICES` (défaut 1000).

### 3. La montre

Onglet **Actions** du dépôt → dernier build vert → artefact `claude-companion-debug`, qui contient `claude-companion-montre.apk` (compilé avec les valeurs Firebase de l'étape 1) :

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

La montre doit avoir les services Google Play (c'est le cas des Galaxy Watch). Elle reçoit les pushs en Wi-Fi, en LTE ou via le téléphone auquel elle est appairée.

Pour compiler toi-même : `./gradlew :app:assembleDebug -PcompanionServer=https://claude-companion.codingpacer.fr -PfirebaseAppId=… -PfirebaseApiKey=… -PfirebaseProjectId=… -PfirebaseSenderId=…`.

### 4. Chaque ordinateur

Sur la montre : **Ordinateurs et cloud › Nouveau code**, nomme l'ordinateur et note le code. Puis, sur l'ordinateur :

```bash
claude plugin install watch-companion --marketplace Pelagoss/claude-companion \
  --config server_url=https://claude-companion.codingpacer.fr --config code=XXXX-XXXX-XXXX
```

C'est tout, pour tous tes projets. Le code est rangé dans le trousseau du système, pas dans `settings.json`. Sans `--config`, Claude Code demande les deux valeurs (`/plugin` › watch-companion › *Configure options*). Le plugin couvre aussi les sessions locales que tu suis en **Remote Control** depuis claude.ai/code ou l'appli mobile.

Tu peux aussi ajouter la marketplace `Pelagoss/claude-companion` dans **claude.ai › Personnaliser › Plugins** : le plugin se synchronise alors sur chaque ordinateur où tu te connectes à Claude Code. Il ne reste qu'à saisir le code.

### 5. Sessions cloud (claude.ai/code)

Les sessions cloud ne chargent pas les plugins, mais elles exécutent les hooks du dépôt :

1. Ajoute à `.claude/settings.json` du dépôt le bloc que donne `https://claude-companion.codingpacer.fr/v1/cloud-settings.json`. C'est un hook `http` qui envoie `Authorization: Bearer $CLAUDE_COMPANION_CODE`.
2. Sur la montre, crée un code pour cet environnement cloud.
3. Dans les paramètres de l'environnement cloud sur claude.ai/code :
   - variable d'environnement `CLAUDE_COMPANION_CODE=XXXX-XXXX-XXXX` ;
   - accès réseau **personnalisé** qui autorise `claude-companion.codingpacer.fr`.

Chaque personne qui travaille sur le dépôt met **son propre code** dans **son** environnement. Sans code, le hook ne fait rien et n'affiche pas d'erreur. Sur un ordinateur, la variable n'est pas définie, donc pas de doublon avec le plugin.

### Vérifier

**Ordinateurs et cloud › Tester le push** : le serveur envoie une notification par FCM et affiche l'erreur sur la montre si elle ne part pas (clé du compte de service absente, jeton refusé…). Firebase garde un push 24 h si la montre est éteinte ou hors ligne.

## Répondre depuis la montre

Sur la montre : **Paramètres › Répondre depuis la montre › Répondre à Claude**. Active-le quand tu t'éloignes de l'écran, et désactive-le quand tu y reviens.

| Quand Claude… | Hook | Sur la montre |
|---|---|---|
| demande une autorisation (« 1. Oui / 2. Oui, ne plus demander / 3. Non ») | `PermissionRequest` | ✅ Autoriser, ♾️ Toujours autoriser, ❌ Refuser, avec ou sans explication |
| pose une question à choix (`AskUserQuestion`, cases à cocher comprises) | `PreToolUse` | Options à cocher, une ou plusieurs, ou ✍️ autre réponse |
| propose un plan (`ExitPlanMode`) | `PreToolUse` | ✅ Approuver, ✍️ Demander des changements |
| a fini et attend ta réponse | `Stop` | 💬 Réponse au clavier, à la voix ou rapide (« Continue »…), ou 🏁 Terminer |

Les réponses simples se donnent depuis la notification. Touche-la pour l'écran complet : texte entier, choix multiples, compte à rebours.

Le hook attend ta réponse **2 minutes au plus**. Ensuite, ou si tu **balaies la notification**, Claude Code pose la question à l'écran comme d'habitude. Réglage désactivé, le serveur rend la main tout de suite : rien ne change sur l'ordinateur, la fin de tâche reste une simple notification.

Il faut le plugin **0.2.0** (`claude plugin update watch-companion`). Pour une session cloud, recopie le bloc de `/v1/cloud-settings.json`. Derrière un reverse proxy, laisse les requêtes `POST /ask` ouvertes 2 minutes (Traefik et Caddy le font par défaut).

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

- **Serveur** : il est à toi. Il reçoit les événements en HTTPS (nom du dossier, identifiant court de session, 300 premiers caractères du dernier message de Claude) et les transmet aussitôt à Firebase, sans les stocker. Les hooks Claude Code envoient du JSON brut : pas de chiffrement de bout en bout, la confiance repose sur ton serveur et sur Firebase Cloud Messaging (Google), qui achemine le push.
- **Réponses** : une question en attente ne vit qu'en mémoire, le temps de la réponse. Seule la montre qui l'a reçue peut y répondre, une seule fois. Une montre qui répond peut autoriser des commandes : désactive le réglage si tu la prêtes.
- **Codes et jetons** : le serveur n'en garde que l'empreinte SHA-256. Chaque montre ne lit que ses événements. Les tentatives avec un code invalide sont limitées.

## Développement

- Montre : Kotlin, Jetpack Compose (Wear OS Material), Firebase Cloud Messaging, DataStore, OkHttp
- Serveur : Go, bibliothèque standard uniquement
- `./gradlew :app:testDebugUnitTest` : mascotte, lecture des pushs, filtres
- `cd server && go test ./...` : traduction des hooks, réponses depuis la montre, appairage, isolation entre montres, envoi FCM (JWT, OAuth, jeton périmé), persistance
- `claude plugin validate .` : marketplace et plugin

```
app/              appli Wear OS : push/ (FCM), net/ (API du serveur), notify/, pet/, sync/, ui/
server/           serveur Go : hooks → FCM, API d'appairage, Docker + Caddy
plugins/          plugin Claude Code watch-companion (marketplace : .claude-plugin/marketplace.json)
keystore/         clé de debug (même signature d'un build à l'autre)
```

## Idées pour la suite

- Tuile Wear OS et complication avec l'humeur de la mascotte
