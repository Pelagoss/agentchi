# Host your own Agentchi server

By default, the app and the commands in the [README](../../README.md) go through the public server **https://agentchi.dev**. This guide is for running your own instance: your Firebase project, your server, and a watch build pointed at it.

```
Claude Code (computer, Remote Control) ──agentchi plugin──────────┐
claude.ai/code cloud session ───────────repo http hook────────────┼──HTTPS──▶ your server ──FCM──▶ watch
Codex (CLI, app, IDE) ──────────────────~/.codex/hooks.json───────┘   (server/)
```

The server (`server/`) is a small Go binary with no dependencies. It relays events right away to the right watch through Firebase Cloud Messaging, without keeping them: it only stores registered watches and a hash of their codes. **Multi-user**: each watch registers itself and only receives its own events.

## 1. Firebase (once)

Push goes through Firebase Cloud Messaging, which is free.

1. On https://console.firebase.google.com, create a project (or reuse yours: its name doesn't matter), then add an **Android app** with the package `fr.codingpacer.agentchi`.
2. Download `google-services.json`. It's only used to read four values and is not committed:

   | Variable (or Gradle property) | Field in `google-services.json` |
   |---|---|
   | `FIREBASE_APP_ID` (`-PfirebaseAppId`) | `client[0].client_info.mobilesdk_app_id` |
   | `FIREBASE_API_KEY` (`-PfirebaseApiKey`) | `client[0].api_key[0].current_key` |
   | `FIREBASE_PROJECT_ID` (`-PfirebaseProjectId`) | `project_info.project_id` |
   | `FIREBASE_SENDER_ID` (`-PfirebaseSenderId`) | `project_info.project_number` |

   Copy them, along with `COMPANION_SERVER` (the server address), into `app/build.secrets.env`, encrypted with [sops](https://github.com/getsops/sops):

   ```bash
   sops edit app/build.secrets.env
   ```

   CI decrypts it before building the watch app: it produces a ready-to-use APK. A value left as `A_REMPLACER` is ignored.
3. **Project settings › Service accounts › Generate new private key**: this is the key the server uses to send pushes. Paste its JSON content in place of the example in `k8s/0.secrets.yaml`, which is encrypted too:

   ```bash
   sops edit k8s/0.secrets.yaml
   ```

**sops**: both files are encrypted for the age key in `.sops.yaml`, the same one as Pelagoss/chess-analyzer. To edit them, you need the matching private key locally (`~/.config/sops/age/keys.txt` or `SOPS_AGE_KEY`), and in the GitHub repo the **`DECRYPTING_KEY`** secret so CI can read them.

## 2. The server

### Kubernetes (default deployment)

Same pipeline as Pelagoss/chess-analyzer: the **Deploy** workflow builds the `server/Dockerfile` image, pushes it to `harbor.codingpacer.fr/prod/agentchi-server`, decrypts `k8s/*.secrets.yaml` with sops, then applies `k8s/` in the `claude-companion` namespace (original name, kept because the `state.json` volume and the encrypted Secret are attached to it). It runs on every `X.Y.Z` tag, or manually (*Actions › Deploy › Run workflow*).

| File | Contents |
|---|---|
| `k8s/0.secrets.yaml` | `firebase-service-account` Secret (sops-encrypted), mounted at `/secrets/firebase-service-account.json` |
| `k8s/config.yaml` | Server configuration, including `CC_PUBLIC_URL` |
| `k8s/server.yaml` | Deployment (one replica), persistent volume for `state.json`, Service |
| `k8s/ingress.yaml` | Traefik Ingress + cert-manager on `agentchi.dev` |

Required GitHub repo secrets: `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `KUBE_CONFIG` and `DECRYPTING_KEY`. For another domain, change it in `k8s/ingress.yaml`, `k8s/config.yaml` and `app/build.secrets.env`.

### Docker Compose (any other server)

You need a domain name pointing at your server, with ports 80 and 443 open. Caddy obtains the HTTPS certificate on its own.

```bash
git clone https://github.com/Pelagoss/agentchi && cd agentchi
sops decrypt --extract '["stringData"]["firebase-service-account.json"]' k8s/0.secrets.yaml > server/firebase-service-account.json
chmod 644 server/firebase-service-account.json    # readable by the container
cd server && CC_DOMAIN=agentchi.example.com docker compose up -d
```

### Without Docker

Run `go build` in `server/`, then start the binary behind your reverse proxy. Variables:

| Variable | Role |
|---|---|
| `CC_FCM_CREDENTIALS` | Path to the service account key, **required** for pushes |
| `CC_ADDR` | Listen address (default `:8080`) |
| `CC_DATA` | State file (default `data/state.json`) |
| `CC_PUBLIC_URL` | Public address, shown on the home page and in the commands (otherwise inferred from the request) |
| `CC_TRUST_PROXY` | `true` behind a reverse proxy (`X-Forwarded-For`, `X-Forwarded-Proto`) |
| `CC_MAX_DEVICES` | Maximum number of watches (default 1000, 0 = unlimited) |

Behind a reverse proxy, let `POST /ask` requests stay open for 5 minutes (Traefik and Caddy do so by default).

### The home page

Open your server's address: its home page introduces Agentchi and gives every command from the install guide, ready to copy, with **its** address. It's in English by default and in French for browsers that prefer French (`Accept-Language`); the **FR** / **EN** link in the header switches language (`/?lang=fr`, `/?lang=en`) and the choice is remembered in a cookie. Screenshots, video and share image follow the language. It's embedded in the binary (`server/web/`: `home.en.html`, `home.fr.html`) along with its fonts, the mascot scripts (copied from `Stores/source`) and the intro video.

## 3. The watch

Until the app is on Google Play, or for a watch tied to your server, install the APK built by CI. **Actions** tab of the repo → latest green build → `agentchi-debug` artifact, which contains `agentchi-montre.apk` (built with the Firebase values from step 1) and the `agentchi-cadran.apk` watch face:

```bash
gh run download -R Pelagoss/agentchi -n agentchi-debug
```

1. Enable debugging: *Settings → About watch → Software information*, tap *Software version* 7 times. Then, in *Developer options*, turn on *ADB debugging* and *Wireless debugging* (watch and computer on the same Wi-Fi).
2. Install:
   ```bash
   adb pair <ip>:<pairing-port>        # code shown on the watch
   adb connect <ip>:<port>
   adb install agentchi-montre.apk
   adb install agentchi-cadran.apk     # optional: the Agentchi watch face
   ```
3. Open **Agentchi** on the watch, allow notifications, then go to **Settings › Server**: enter your server's address and tap **Connect the watch**.

The watch needs Google Play services (Galaxy Watches have them). It receives pushes over Wi-Fi, LTE or through the phone it's paired with.

To build it yourself: `./gradlew :app:assembleDebug -PcompanionServer=https://agentchi.example.com -PfirebaseAppId=… -PfirebaseApiKey=… -PfirebaseProjectId=… -PfirebaseSenderId=…`.

## 4. Hook up your agents

Follow the [README install steps](../../README.md#install-agentchi), replacing `https://agentchi.dev` with your server's address, or copy the commands straight from its home page:

- **Claude Code**: `--config server_url=https://agentchi.example.com`;
- **Cloud sessions**: the block from `https://agentchi.example.com/v1/cloud-settings.json`, and network access that allows your domain;
- **Codex**: `curl -fsS https://agentchi.example.com/v1/codex-hooks.json -o ~/.codex/hooks.json`. With the `agentchi-codex` plugin, set `AGENTCHI_SERVER=https://agentchi.example.com`.

Then **Settings › Server › Test push**: the server sends a notification through FCM and shows the error on the watch if it doesn't go through (missing service account key, rejected token…).
