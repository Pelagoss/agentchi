# Claude Companion ⌚️

Application **Wear OS** (Galaxy Watch 4 et plus récentes) qui t'accompagne pendant que Claude travaille :

- 🔔 **Toutes les notifications de l'appli Claude** sur la montre : sessions cloud (claude.ai/code), conversations, Remote Control. Ça marche qu'un dépôt GitHub soit branché ou non.
- 🦀 **Une mascotte façon tamagotchi** : nourris-la, joue avec elle, mets-la au lit. Elle réagit en direct : elle fait la fête quand Claude a fini et s'affole quand il t'attend.
- ⚙️ **Des paramètres** pour choisir quoi recevoir et comment : plein écran, notification normale ou discrète, motif de vibration, son, heures calmes.

> Application non officielle, sans lien avec Anthropic.

## Comment ça marche

```
Appli Claude (téléphone) ──notification──▶ Claude Companion (téléphone) ──Bluetooth / Wear OS──▶ Claude Companion (montre)
                                                     ▲                                                   │
                                                     └──────────── « 📱 Ouvrir sur le téléphone » ◀──────┘
```

Le projet contient **deux petites applis** portant le même nom :

- **Sur le téléphone**, un relais sans écran à utiliser au quotidien. Il lit les notifications de l'appli Claude (accès aux notifications d'Android) et les transmet à la montre par la liaison Wear OS. Il ne passe par aucun serveur, et il n'y a ni hook, ni compte, ni configuration par projet.
- **Sur la montre**, l'appli principale : elle reçoit, trie et affiche les notifications, et héberge la mascotte. Le bouton **📱 Ouvrir sur le téléphone** ouvre la notification d'origine dans l'appli Claude, pour répondre.

Le type de chaque notification (🔐 autorisation, 💬 réponse attendue, ✅ terminé, ⚠️ erreur) est **déduit de son texte**. Ce que la montre reçoit dépend donc de ce que l'appli Claude notifie sur ton téléphone.

## Installation

### 1. Récupérer les APK

Onglet **Actions** du dépôt → dernier build vert → artefact `claude-companion-debug`. Il contient `claude-companion-montre.apk` et `claude-companion-telephone.apk`.

```bash
gh run download -R Pelagoss/claude-companion -n claude-companion-debug
```

Les deux APK sont signés avec la même clé (`keystore/debug.keystore`). C'est une condition imposée par Wear OS pour qu'ils communiquent : installe toujours les deux APK **du même build**.

### 2. Téléphone

```bash
adb install claude-companion-telephone.apk
```

Ouvre **Claude Companion** sur le téléphone :
1. **Accès aux notifications** → *Autoriser*, puis active *Relais Claude Companion*. Si Android affiche « paramètre restreint » : *Paramètres → Applications → Claude Companion → ⋮ → Autoriser les paramètres restreints*, puis recommence.
2. Vérifie que **Montre connectée** affiche ta Galaxy Watch.
3. Touche **Envoyer un test à la montre**.

Pour éviter les doublons, désactive l'appli **Claude** dans **Galaxy Wearable → Notifications** : sinon la montre affiche aussi la copie standard de chaque notification.

### 3. Montre

1. Active le débogage : *Paramètres → À propos de la montre → Informations sur le logiciel*, touche 7 fois *Version du logiciel*. Ensuite, dans *Options de développement*, active *Débogage ADB* et *Débogage sans fil* (montre et ordinateur sur le même Wi-Fi).
2. Installe :
   ```bash
   adb pair <ip>:<port-appairage>      # code affiché sur la montre
   adb connect <ip>:<port>
   adb install claude-companion-montre.apk
   ```
3. Ouvre **Claude Companion** sur la montre et accepte les notifications.

C'est tout. Lance une session dans l'appli Claude ou sur claude.ai/code : les notifications que l'appli Claude affiche sur ton téléphone arrivent sur la montre.

## Option : sessions Claude Code dans un terminal

Une session Claude Code lancée dans un terminal ne passe pas par l'appli Claude, sauf avec Remote Control. Pour la recevoir quand même, installe le hook **une fois par ordinateur**, il couvre tous tes projets :

```bash
git clone https://github.com/Pelagoss/claude-companion && cd claude-companion
./hooks/install.sh      # demande le code affiché sur la montre : Paramètres › Hook Claude Code
```

Le hook publie les événements **chiffrés de bout en bout** sur [ntfy](https://ntfy.sh). La montre les récupère toutes les 15 minutes, à l'ouverture de l'appli, ou en quelques secondes avec **⚡ Mode instantané**. Il faut `jq`, `curl` et `openssl`. Les options `--server` et `--token` permettent d'utiliser un serveur ntfy auto-hébergé, et `--account` de nommer l'ordinateur.

## La mascotte

| Jauge | Baisse | Remonte |
|---|---|---|
| 🍪 Satiété | −6 / h | Nourrir (+25) |
| ⚡ Énergie | −5 / h | Dormir (+15 / h) |
| 💖 Bonheur | −4 / h | Jouer (+20), la caresser (tap), chaque tâche terminée par Claude (+10) |

Chaque tâche terminée rapporte aussi de l'XP (+10). La mascotte monte d'un niveau tous les 100 XP. Tu peux la renommer dans les paramètres.

## Confidentialité

- **Relais téléphone** : seules les notifications de l'appli Claude (`com.anthropic.claude`) sont lues. Elles passent directement du téléphone à la montre par la liaison Wear OS, sans serveur.
- **Hook (facultatif)** : chiffrement AES-256-CBC + HMAC-SHA256. Le serveur ntfy ne voit qu'un bloc chiffré, et la montre rejette tout message qui n'a pas été produit avec sa clé. Le code d'appairage n'est jamais écrit dans ce dépôt.

## Développement

- Kotlin, Jetpack Compose (Wear OS Material sur la montre, Material 3 sur le téléphone), Wearable Data Layer, DataStore, WorkManager, OkHttp
- `./gradlew :app:testDebugUnitTest :phone:testDebugUnitTest` : mascotte, déduction du type des notifications, filtres, chiffrement du hook

```
app/      appli montre : relay/ (réception téléphone), notify/, pet/, ui/, net/ + sync/ (hook via ntfy)
phone/    appli téléphone : lecture des notifications de l'appli Claude et relais vers la montre
hooks/    hook Claude Code facultatif + installateur
keystore/ clé de debug partagée (requise pour que montre et téléphone communiquent)
```

## Idées pour la suite

- Répondre (*Autoriser / Refuser*) directement depuis la montre via les actions des notifications de l'appli Claude
- Tuile Wear OS et complication avec l'humeur de la mascotte
