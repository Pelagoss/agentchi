# Héberger ton propre serveur Agentchi

Par défaut, l'appli et les commandes du [README](../../README.md) passent par le serveur public **https://agentchi.dev**. Ce guide sert à faire tourner ta propre instance : ton projet Firebase, ton serveur, et une montre compilée pour lui.

```
Claude Code (ordinateur, Remote Control) ──plugin agentchi──────────┐
Session cloud claude.ai/code ─────────────hook http du dépôt────────┼──HTTPS──▶ ton serveur ──FCM──▶ montre
Codex (CLI, appli, IDE) ──────────────────~/.codex/hooks.json───────┘   (server/)
```

Le serveur (`server/`) est un petit binaire Go, sans dépendance. Il relaie aussitôt les événements à la bonne montre par Firebase Cloud Messaging, sans les conserver : il ne garde que les montres enregistrées et l'empreinte de leurs codes. **Multi-utilisateur** : chaque montre s'y enregistre toute seule et ne reçoit que ses événements.

## 1. Firebase (une fois)

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

## 2. Le serveur

### Kubernetes (déploiement par défaut)

Même chaîne que Pelagoss/chess-analyzer : le workflow **Deploy** construit l'image `server/Dockerfile`, la pousse sur `harbor.codingpacer.fr/prod/agentchi-server`, déchiffre `k8s/*.secrets.yaml` avec sops puis applique `k8s/` dans le namespace `claude-companion` (nom d'origine, conservé : le volume `state.json` et le Secret chiffré y sont rattachés). Il part à chaque tag `X.Y.Z`, ou à la main (*Actions › Deploy › Run workflow*).

| Fichier | Contenu |
|---|---|
| `k8s/0.secrets.yaml` | Secret `firebase-service-account` (chiffré sops), monté en `/secrets/firebase-service-account.json` |
| `k8s/config.yaml` | Configuration du serveur, dont `CC_PUBLIC_URL` |
| `k8s/server.yaml` | Deployment (une réplique), volume persistant pour `state.json`, Service |
| `k8s/ingress.yaml` | Ingress Traefik + cert-manager sur `agentchi.dev` |

Secrets GitHub du dépôt nécessaires : `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `KUBE_CONFIG` et `DECRYPTING_KEY`. Pour un autre domaine, change-le dans `k8s/ingress.yaml`, `k8s/config.yaml` et `app/build.secrets.env`.

### Docker Compose (autre serveur)

Il faut un nom de domaine qui pointe vers ton serveur, avec les ports 80 et 443 ouverts. Caddy obtient le certificat HTTPS tout seul.

```bash
git clone https://github.com/Pelagoss/agentchi && cd agentchi
sops decrypt --extract '["stringData"]["firebase-service-account.json"]' k8s/0.secrets.yaml > server/firebase-service-account.json
chmod 644 server/firebase-service-account.json    # lisible par le conteneur
cd server && CC_DOMAIN=agentchi.example.fr docker compose up -d
```

### Sans Docker

`go build` dans `server/`, puis lance le binaire derrière ton reverse proxy. Variables :

| Variable | Rôle |
|---|---|
| `CC_FCM_CREDENTIALS` | Chemin de la clé du compte de service, **obligatoire** pour les pushs |
| `CC_ADDR` | Adresse d'écoute (défaut `:8080`) |
| `CC_DATA` | Fichier d'état (défaut `data/state.json`) |
| `CC_PUBLIC_URL` | Adresse publique, affichée sur la page d'accueil et dans les commandes (sinon déduite de la requête) |
| `CC_TRUST_PROXY` | `true` derrière un reverse proxy (`X-Forwarded-For`, `X-Forwarded-Proto`) |
| `CC_MAX_DEVICES` | Nombre maximal de montres (défaut 1000, 0 = illimité) |

Derrière un reverse proxy, laisse les requêtes `POST /ask` ouvertes 5 minutes (Traefik et Caddy le font par défaut).

### La page d'accueil

Ouvre l'adresse de ton serveur : sa page d'accueil présente Agentchi et donne toutes les commandes du guide d'installation, prêtes à copier, avec **son** adresse. Elle est embarquée dans le binaire (`server/web/`) avec ses polices, les scripts de la mascotte (copiés depuis `Stores/source`) et la vidéo de présentation.

## 3. La montre

Tant que l'appli n'est pas sur Google Play, ou pour une montre reliée à ton serveur, installe l'APK de la CI. Onglet **Actions** du dépôt → dernier build vert → artefact `agentchi-debug`, qui contient `agentchi-montre.apk` (compilé avec les valeurs Firebase de l'étape 1) et le cadran `agentchi-cadran.apk` :

```bash
gh run download -R Pelagoss/agentchi -n agentchi-debug
```

1. Active le débogage : *Paramètres → À propos de la montre → Informations sur le logiciel*, touche 7 fois *Version du logiciel*. Ensuite, dans *Options de développement*, active *Débogage ADB* et *Débogage sans fil* (montre et ordinateur sur le même Wi-Fi).
2. Installe :
   ```bash
   adb pair <ip>:<port-appairage>      # code affiché sur la montre
   adb connect <ip>:<port>
   adb install agentchi-montre.apk
   adb install agentchi-cadran.apk     # facultatif : le cadran Agentchi
   ```
3. Ouvre **Agentchi** sur la montre, accepte les notifications, puis **Paramètres › Serveur** : saisis l'adresse de ton serveur et touche **Connecter la montre**.

La montre doit avoir les services Google Play (c'est le cas des Galaxy Watch). Elle reçoit les pushs en Wi-Fi, en LTE ou via le téléphone auquel elle est appairée.

Pour compiler toi-même : `./gradlew :app:assembleDebug -PcompanionServer=https://agentchi.example.fr -PfirebaseAppId=… -PfirebaseApiKey=… -PfirebaseProjectId=… -PfirebaseSenderId=…`.

## 4. Brancher tes agents

Suis l'[installation du README](../../README.md#installer-agentchi) en remplaçant `https://agentchi.dev` par l'adresse de ton serveur, ou copie directement les commandes de sa page d'accueil :

- **Claude Code** : `--config server_url=https://agentchi.example.fr` ;
- **Sessions cloud** : le bloc de `https://agentchi.example.fr/v1/cloud-settings.json`, et l'accès réseau qui autorise ton domaine ;
- **Codex** : `curl -fsS https://agentchi.example.fr/v1/codex-hooks.json -o ~/.codex/hooks.json`. Avec le plugin `agentchi-codex`, définis `AGENTCHI_SERVER=https://agentchi.example.fr`.

Puis **Paramètres › Serveur › Tester le push** : le serveur envoie une notification par FCM et affiche l'erreur sur la montre si elle ne part pas (clé du compte de service absente, jeton refusé…).
