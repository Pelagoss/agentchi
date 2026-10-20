# Publier Claude Companion sur le Google Play Store

Ce guide couvre la publication des **deux applis** (téléphone + montre Wear OS) sous une **seule fiche** Play Store, comme le prévoit Google pour une appli Wear OS accompagnée de son appli téléphone.

> ⏱️ Prévois **3 à 4 semaines** pour un nouveau compte personnel : environ 1 jour de préparation, **14 jours de test fermé obligatoire**, puis quelques jours d'examen.

---

## 0. À régler avant tout (points bloquants)

### 0.1 Le nom et l'icône : risque de refus

« **Claude** » est une marque d'Anthropic. Le règlement Google Play (*usurpation d'identité* et *propriété intellectuelle*) refuse les applis qui laissent croire à un lien officiel avec une marque, et Anthropic peut demander le retrait.

Tu as trois options, de la plus sûre à la plus risquée :

| Option | Exemple | Risque |
|---|---|---|
| Nom sans la marque, compatibilité dans la description | « **Pixel Buddy — notifications IA pour ta montre** », avec la mention « compatible avec l'appli Claude » | Faible |
| Demander l'autorisation à Anthropic | Contact via https://www.anthropic.com | Nul si accepté |
| Garder « Claude Companion » + avertissement « non officiel » | — | **Élevé** : refus ou retrait possible |

Même chose pour **l'icône et la mascotte** : la créature orange en pixel-art ne doit pas reprendre un visuel d'Anthropic. Si tu doutes, change sa couleur ou sa forme.

N'écris pas non plus « **Tamagotchi** » dans la fiche : c'est une marque de Bandai. Préfère « animal virtuel » ou « compagnon virtuel ».

### 0.2 L'identifiant de l'appli est définitif

`applicationId = "com.claudecompanion"`, défini dans `app/build.gradle.kts` et `phone/build.gradle.kts`, **ne pourra plus changer** après la première publication. Si tu renommes l'appli, change-le **maintenant**, avec **la même valeur dans les deux modules**. C'est obligatoire pour que la montre et le téléphone communiquent. Exemple : `fr.pelagoss.pixelbuddy`.

### 0.3 Le compte développeur

1. Crée un compte sur https://play.google.com/console : **25 $ une seule fois**, puis vérification d'identité (pièce d'identité, adresse, téléphone).
2. Un **compte personnel récent** doit faire passer chaque nouvelle appli par un **test fermé avec au moins 12 testeurs pendant 14 jours consécutifs** avant de pouvoir publier en production. Commence à recruter tes testeurs dès maintenant (amis, collègues, forums Wear OS).

### 0.4 Les versions d'Android ciblées

Google impose un *targetSdk* minimal, relevé chaque année en août. Au moment où ce guide est écrit, le projet cible :

- téléphone : `targetSdk = 35`
- montre : `targetSdk = 34`

Vérifie l'exigence en vigueur dans la Play Console (*Tableau de bord → Exigences relatives au niveau d'API cible*) et ajuste `targetSdk` dans les deux `build.gradle.kts` si besoin.

---

## 1. Créer la clé de publication

Les APK de test utilisent `keystore/debug.keystore`, qui est public : **il ne doit jamais servir à publier.** Crée ta propre clé d'*upload* :

```bash
keytool -genkeypair -v \
  -keystore ~/claude-companion-upload.jks \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Puis crée `keystore/release.properties`. Ce fichier est déjà exclu de git : **ne le commite jamais**.

```properties
storeFile=/home/toi/claude-companion-upload.jks
storePassword=ton-mot-de-passe
keyAlias=upload
keyPassword=ton-mot-de-passe
```

> 💾 **Sauvegarde la clé `.jks` et ses mots de passe** dans un gestionnaire de mots de passe. Grâce à *Play App Signing* (activé par défaut), une clé d'upload perdue peut être réinitialisée, mais la procédure prend plusieurs jours.

**Play App Signing** : Google re-signe les deux applis avec **la même clé de signature d'application**. C'est indispensable pour que la montre et le téléphone continuent de communiquer une fois installés depuis le Play Store. Accepte-le quand la Console le propose.

Pour une build en CI, les mêmes valeurs peuvent venir de variables d'environnement : `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

---

## 2. Adapter l'appli aux règles du Play Store

### 2.1 Retirer l'alerte « plein écran »

Depuis Android 14, Google réserve la permission `USE_FULL_SCREEN_INTENT` aux applis de **réveil et d'appel**. Une autre appli qui la déclare risque d'être refusée, et elle n'est de toute façon pas accordée par défaut.

Supprime cette ligne de `app/src/main/AndroidManifest.xml` :

```xml
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

Le style « Plein écran » se comporte alors comme une notification prioritaire : la montre vibre et la notification s'affiche par-dessus. Rien ne plante.

### 2.2 Préparer la déclaration du service au premier plan

La montre utilise un service au premier plan de type `remoteMessaging` pour le **mode instantané** du hook. La Console te demandera :

- une **description** : *« Garde une connexion ouverte avec le serveur de notifications choisi par l'utilisateur pour afficher instantanément sur la montre les événements de ses sessions Claude Code. Activé uniquement par l'utilisateur, désactivable à tout moment. »*
- une **vidéo de démonstration** de 30 s, sur YouTube en non répertorié : on active « Mode instantané » dans les paramètres, la notification permanente apparaît, un événement arrive en quelques secondes.

Si tu préfères éviter cette déclaration, retire le mode instantané (`InstantSyncService` et la permission `FOREGROUND_SERVICE_REMOTE_MESSAGING`). Il reste la synchronisation toutes les 15 minutes.

### 2.3 Accès aux notifications : informer clairement

L'appli téléphone lit des notifications : ce sont des données personnelles. Google exige :

- une **information visible avant l'autorisation**. C'est déjà fait : l'étape 1 de l'écran du téléphone explique ce qui est lu et pourquoi ;
- une **politique de confidentialité** publique, déjà rédigée dans [`docs/PRIVACY.md`](PRIVACY.md).

Aucun formulaire de déclaration spécifique n'est demandé pour `NotificationListenerService`. En revanche, l'examinateur vérifiera que l'usage correspond bien à la fonction principale de l'appli : dis-le clairement dans la description.

---

## 3. Construire les bundles (AAB)

```bash
./gradlew :phone:bundleRelease :app:bundleRelease
```

Fichiers produits :

- téléphone : `phone/build/outputs/bundle/release/phone-release.aab`
- montre : `app/build/outputs/bundle/release/app-release.aab`

**Teste la version de publication avant de l'envoyer.** R8 réduit le code de l'appli montre, et une règle manquante peut provoquer un plantage au démarrage :

```bash
./gradlew :phone:assembleRelease :app:assembleRelease
adb uninstall com.claudecompanion   # sur chaque appareil : la signature diffère des builds de test
adb install phone/build/outputs/apk/release/phone-release.apk
adb -s <montre> install app/build/outputs/apk/release/app-release.apk
```

Vérifie au minimum : ouverture de l'appli montre, notification de test depuis le téléphone, bouton « Ouvrir sur le téléphone », QR code de l'écran « Hook Claude Code ».

**Codes de version.** Les deux applis partagent la fiche, leurs `versionCode` doivent donc être **différents**. Le projet utilise `1_000_001` pour le téléphone et `2_000_001` pour la montre. À **chaque mise à jour**, incrémente le ou les modules concernés, par exemple `1_000_002`. Un code déjà envoyé au Play Store ne peut jamais être réutilisé.

---

## 4. Créer l'appli dans la Play Console

1. **Créer une application.**
   - Nom de l'appli (30 caractères max).
   - Langue par défaut : **Français (France)**.
   - Type : **Application**, **Gratuite**.
   - Coche les déclarations (règlement et lois d'export).
2. **Formats** : *Test et publication → Paramètres avancés → Formats → Ajouter un format → **Wear OS***. Accepte les conditions Wear OS. Une **piste dédiée « Wear OS uniquement »** apparaît alors : c'est là que va le bundle de la montre.

---

## 5. Remplir « Contenu de l'appli »

*Règlement et programmes → Contenu de l'appli*, rubrique par rubrique :

| Rubrique | Réponse |
|---|---|
| **Règles de confidentialité** | URL publique, par ex. `https://github.com/Pelagoss/claude-companion/blob/master/docs/PRIVACY.md` (le dépôt doit rester public). Mieux : une page GitHub Pages |
| **Accès aux applis** | « Toutes les fonctionnalités sont disponibles sans restriction » (pas de compte) |
| **Annonces** | Non |
| **Classification du contenu** | Questionnaire, catégorie *Utilitaire / productivité* ; aucune violence ni contenu sensible → **Tout public / PEGI 3** |
| **Public cible** | **18 ans et plus**, ou 13+. Évite les moins de 13 ans : règles supplémentaires (Familles) |
| **Appli d'actualités** | Non |
| **Applis de santé** | Non |
| **Service au premier plan** | Voir § 2.2 |
| **Identifiant publicitaire** | Non utilisé |

### Sécurité des données

Réponds selon ce que fait réellement l'appli. Pour la version actuelle :

- **L'appli collecte-t-elle ou partage-t-elle des données ?**
  - Le **relais téléphone → montre** reste entre les appareils de l'utilisateur : ce n'est **ni une collecte ni un partage** au sens de Google.
  - Le **hook facultatif** envoie des données chiffrées de bout en bout à ntfy.sh, un service tiers. Par prudence, déclare : *Messages → Autres messages intégrés à l'appli → **Partagées** → Fonctionnalité de l'appli*, avec *« Données chiffrées en transit »* = **Oui** et *« Suppression possible »* = **Oui**.
- **Données chiffrées en transit** : Oui.
- **Possibilité de demander la suppression** : Oui (désinstallation, « Tout effacer »).

> Si tu retires le hook de la version Play Store, tu peux répondre « Aucune donnée collectée ni partagée ».

---

## 6. La fiche Play Store

*Croissance → Présence sur le Play Store → Fiche principale* :

| Élément | Exigence | Astuce |
|---|---|---|
| Titre | 30 caractères | Voir § 0.1 |
| Description courte | 80 caractères | *« Les notifications de tes sessions IA sur ta montre, avec un compagnon pixel-art. »* |
| Description complète | 4000 caractères | Fonctions, compatibilité (« fonctionne avec l'appli Claude »), **avertissement « appli non officielle, sans lien avec Anthropic »**, explication de l'accès aux notifications |
| Icône | PNG 512 × 512, 32 bits | Exporte `ic_launcher_foreground` sur fond `#1F1E1D` |
| Image de présentation | PNG/JPG 1024 × 500 | Mascotte + montre |
| Captures téléphone | 2 à 8, 16:9 ou 9:16, côté min. 320 px | Écran du relais |
| **Captures Wear OS** | **Au moins 1**, **carrées (1:1)**, min. 384 × 384, **sans cadre de montre** ni masque rond | Voir ci-dessous |

Captures depuis la montre :

```bash
adb -s <montre> exec-out screencap -p > montre-mascotte.png
```

Prends la mascotte, les paramètres, une notification et l'historique. Pour Wear OS, ajoute aussi une **description propre au format** (*Fiche principale → Wear OS*).

---

## 7. Envoyer les bundles et tester

### 7.1 Test interne (immédiat, jusqu'à 100 testeurs)

1. *Test → Test interne → Créer une release* :
   - bundle **téléphone** dans la piste standard ;
   - bundle **montre** dans la piste « **Wear OS uniquement – Test interne** ».
2. Ajoute ton adresse dans la liste de testeurs, ouvre le lien d'inscription sur ton téléphone et installe.
3. Sur la montre : *Play Store → Applications sur votre téléphone*, ou recherche directe. Vérifie que la montre et le téléphone communiquent : bouton *Envoyer un test à la montre*.

### 7.2 Test fermé (obligatoire pour un compte personnel récent)

1. *Test → Test fermé → Créer un canal*, ajoute **au moins 12 testeurs**, par adresses e-mail ou via un groupe Google.
2. Envoie la même release (téléphone et montre).
3. Les testeurs doivent **rester inscrits 14 jours consécutifs**. Demande-leur de vraiment utiliser l'appli : Google regarde l'engagement et te posera des questions sur les retours reçus.

### 7.3 Production

*Tableau de bord → Demander l'accès à la production*, puis réponds au questionnaire sur ton test fermé. Une fois l'accès accordé :

1. *Production → Créer une release* (téléphone et montre).
2. Pays : France, ou le monde entier.
3. **Envoyer pour examen.** Compte de quelques jours à une semaine. L'appli **Wear OS** est examinée séparément, selon les [consignes de qualité Wear OS](https://developer.android.com/docs/quality-guidelines/wear-app-quality) : écran rond, fond noir, balayage pour revenir, texte lisible. L'appli respecte déjà ces points.

---

## 8. Mises à jour

1. Modifie le code, puis incrémente `versionCode` (et `versionName`) dans le ou les modules modifiés.
2. Lance `./gradlew :phone:bundleRelease :app:bundleRelease`.
3. *Production → Créer une release* → envoie le ou les nouveaux bundles.

Pour automatiser, une GitHub Action comme `r0adkll/upload-google-play` peut publier à chaque tag. Il faut alors un compte de service Google Cloud lié à la Play Console, avec la clé d'upload en secret GitHub.

---

## ✅ Checklist

- [ ] Nom, icône et mascotte sans risque de marque (§ 0.1)
- [ ] `applicationId` définitif, identique dans les deux modules (§ 0.2)
- [ ] Compte développeur vérifié ; 12 testeurs recrutés (§ 0.3)
- [ ] `targetSdk` conforme à l'exigence en vigueur (§ 0.4)
- [ ] Clé d'upload créée **et sauvegardée** ; `keystore/release.properties` non commité (§ 1)
- [ ] `USE_FULL_SCREEN_INTENT` retiré (§ 2.1)
- [ ] Mode instantané déclaré avec vidéo, ou retiré (§ 2.2)
- [ ] Build de publication testée sur le téléphone **et** la montre (§ 3)
- [ ] Format Wear OS ajouté dans la Console (§ 4)
- [ ] Contenu de l'appli et sécurité des données remplis (§ 5)
- [ ] Fiche complète, avec captures Wear OS carrées (§ 6)
- [ ] Test interne → test fermé 14 jours → production (§ 7)
