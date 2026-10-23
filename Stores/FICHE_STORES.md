# Agentchi : fiches des stores

Tout ce qu'il faut copier-coller pour publier Agentchi, champ par champ, avec les visuels à envoyer. Les limites de caractères ont été vérifiées. La procédure complète (compte, clé, test fermé, contenu de l'appli) est dans [`docs/PUBLICATION_PLAY_STORE.md`](../docs/PUBLICATION_PLAY_STORE.md).

> ⚠️ **Marques** : pas de « Claude », « Codex », « OpenAI » ni « Tamagotchi » dans le **titre** ni dans l'**icône**. On les cite seulement pour la compatibilité, avec l'avertissement « appli non officielle ». Les textes ci-dessous respectent déjà ces règles.

## Sommaire

1. [Visuels fournis](#1-visuels-fournis)
2. [Google Play : fiche en français](#2-google-play--fiche-principale-français-fr-fr)
3. [Google Play : fiche en anglais](#3-google-play--traduction-anglais-en-us)
4. [Google Play : autres champs de la Console](#4-google-play--autres-champs-de-la-console)
5. [Samsung Galaxy Store](#5-samsung-galaxy-store-facultatif)
6. [Régénérer les visuels et la vidéo](#6-régénérer-les-visuels-et-la-vidéo)

---

## 1. Visuels fournis

Tous les fichiers sont dans [`assets/`](assets/). Ils sont dessinés à partir des vraies grilles de la mascotte (`MascotSprite.kt`, `PixelIcon.kt`) et de la mise en page des écrans Compose.

Les mêmes visuels existent en anglais dans [`assets/en/`](assets/en/) (mêmes noms de fichiers, sauf les icônes, sans texte) : à envoyer avec la fiche en anglais (section 3).

| Fichier | Format | Où l'envoyer |
|---|---|---|
| `icone/icon-512.png` | 512 × 512, PNG | **Icône de l'appli** (Play et Galaxy Store) : Crabe Océan, une couleur sans lien avec une marque |
| `icone/icon-512-classique.png` | 512 × 512, PNG | Variante : l'icône classique du lanceur (Crabe Corail) |
| `presentation/feature-graphic-1024x500.png` | 1024 × 500, PNG | **Image de présentation** (Play) |
| `captures-wear/01-mascotte.png` … `08-succes.png` | 900 × 900, carrées, sans cadre | **Captures Wear OS** (Play : 1 minimum, 8 au maximum) |
| `promo/01-compagnon.png` … `04-reponses.png` | 1080 × 1920 (9:16) | Emplacement **téléphone** si la Console l'exige, Galaxy Store, réseaux sociaux |
| `video/agentchi-companion.mp4` | 1920 × 1080, 43 s, H.264 + AAC | **Vidéo promotionnelle** : à mettre sur YouTube, puis coller l'URL dans la fiche |
| `video/poster.png` | 1920 × 1080 | Miniature YouTube |

**Ordre conseillé des captures Wear OS** (la première est la plus vue) :

| # | Fichier | Ce qu'elle montre |
|---|---|---|
| 1 | `01-mascotte.png` | La mascotte, ses trois jauges et ses actions |
| 2 | `06-autorisation.png` | Autoriser une commande depuis le poignet |
| 3 | `02-nourrir.png` | Nourrir la mascotte (« Miam ! ») |
| 4 | `05-personnalisation.png` | Espèce, couleurs et accessoire |
| 5 | `07-sessions.png` | Les sessions en cours des agents |
| 6 | `03-jouer.png` | Jouer avec elle |
| 7 | `08-succes.png` | Séries et succès |
| 8 | `04-dodo.png` | La mettre au lit |

**La vidéo (43 s)** : intro où la mascotte s'assemble pixel par pixel → « Il vit sur ta montre » → **« Prends soin de lui »** (nourrir, jouer, caresser, mettre au lit, avec les jauges qui remontent) → « Il fête tes victoires » (tâche terminée, +10 XP, niveau 4) → **« Fais-le à ton image »** (base : Crabe, Points, Fantôme ; les 6 couleurs ; chapeau de fête, nœud, lunettes, haut-de-forme, couronne) → mosaïque des 108 combinaisons → logo et mention « appli non officielle ». La musique chiptune et les bruitages sont synthétisés par `source/music.py` : aucun droit à payer.

> 📸 Les captures sont des reconstitutions fidèles, pas des copies d'écran de la montre. Google demande que les captures montrent l'appli réelle. C'est le cas ici (mêmes écrans, mêmes textes, mêmes sprites), mais tu peux les remplacer par de vraies captures : `adb -s <montre> exec-out screencap -p > 01-mascotte.png`.

---

## 2. Google Play : fiche principale (français, fr-FR)

*Play Console › Croissance › Présence sur le Play Store › Fiche principale*

### Nom de l'appli (30 caractères max)

```
Agentchi : agents au poignet
```

(28 caractères) Autres choix possibles : `Agentchi` (8, le plus prudent côté marques) ou `Agentchi – Compagnon de code` (28).

### Description courte (80 caractères max)

```
Tes agents de code sur ta montre, avec un compagnon pixel-art à chouchouter.
```

(76 caractères)

### Description complète (4 000 caractères max)

```
Agentchi t'accompagne pendant que tes agents de code travaillent : leurs demandes arrivent sur ta montre Wear OS, tu leur réponds d'un geste, et une petite mascotte pixel-art vit au rythme de tes sessions.

🔔 LEURS ÉVÉNEMENTS AU POIGNET
• Demande d'autorisation, réponse attendue, tâche terminée, erreur : rien ne t'échappe, même loin de ton écran.
• Le texte des agents est mis en forme : gras, italique, code, titres, listes.
• Choisis comment être prévenu : plein écran, notification normale ou discrète, motif de vibration, son.
• Résumé groupé des événements non urgents, heures calmes, et respect du mode Ne pas déranger et du mode Coucher de la montre.

⌚ RÉPONDS SANS QUITTER TA MONTRE
• Autorise ou refuse une commande, avec ou sans explication.
• Choisis parmi les options proposées, une ou plusieurs.
• Approuve un plan ou demande des changements.
• Réponds au clavier, à la voix ou avec une réponse rapide quand l'agent a fini.

🦀 UN COMPAGNON À CHOUCHOUTER
• Nourris-le, joue avec lui, caresse-le et mets-le au lit : ses jauges de satiété, d'énergie et de bonheur baissent avec le temps.
• Il fait la fête quand ton agent termine une tâche et s'affole quand on a besoin de toi.
• Chaque tâche terminée lui rapporte de l'expérience : il monte de niveau et débloque de nouveaux accessoires.

🎨 FAIS-LE À TON IMAGE
• 3 espèces : Crabe, Points ou Fantôme.
• 6 couleurs : Corail, Encre, Océan, Menthe, Lilas, Rose.
• 5 accessoires : chapeau de fête, nœud, lunettes de soleil, haut-de-forme, couronne.
• Icônes d'actions en pixel-art ou en emojis, et une icône d'appli assortie à ta mascotte.

📋 TOUT D'UN COUP D'ŒIL
• Sessions en cours : au travail, en attente de ta réponse, terminées.
• Tuile, complication et cadran Agentchi, où ta mascotte s'affiche en grand.
• Séries et succès : jours d'affilée avec des tâches terminées, réponses données depuis la montre…

🔒 TON SERVEUR, TES DONNÉES
Agentchi fonctionne avec un petit serveur open source que tu héberges toi-même. Il relaie les événements de tes agents jusqu'à ta montre par Firebase Cloud Messaging, sans rien stocker. Chaque ordinateur est appairé avec son propre code, révocable depuis la montre. Pas de compte, pas de publicité.

✅ COMPATIBILITÉ
• Fonctionne avec Claude Code (terminal, IDE, appli Desktop, Remote Control, sessions cloud) et Codex (CLI, appli, extension IDE).
• Montres Wear OS avec les services Google Play, par exemple Galaxy Watch 4 et plus récentes.
• Nécessite un serveur Agentchi (instructions sur le dépôt GitHub du projet).

Agentchi est une appli non officielle, sans lien avec Anthropic ni OpenAI. Claude et Claude Code sont des marques d'Anthropic ; Codex et OpenAI sont des marques d'OpenAI.
```

### Description propre au format Wear OS

*Fiche principale › onglet **Wear OS*** : reprend la description complète, ou cette version courte :

```
Agentchi apporte tes agents de code sur ta montre : notifications, réponses d'un geste (autoriser, refuser, choisir, dicter) et une mascotte pixel-art à nourrir, faire jouer et personnaliser. Tuile, complication et cadran inclus. Fonctionne avec Claude Code et Codex via ton propre serveur. Appli non officielle, sans lien avec Anthropic ni OpenAI.
```

### Vidéo

```
https://www.youtube.com/watch?v=A_REMPLACER
```

Mets `assets/video/agentchi-companion.mp4` sur YouTube, en **public** ou **non répertoriée** (pas privée), sans publicité, puis colle son URL. La vidéo apparaît en tête de la fiche, devant les captures.

### Notes de version (« Nouveautés », 500 caractères max)

```
Première version d'Agentchi !
• Les événements de Claude Code et Codex sur ta montre
• Réponds depuis le poignet : autoriser, refuser, choisir, dicter
• Une mascotte pixel-art à nourrir, faire jouer et personnaliser
• Tuile, complication et cadran
• Séries et succès
```

---

## 3. Google Play : traduction anglais (en-US)

*Fiche principale › Gérer les traductions › Ajouter : anglais (États-Unis)*

**App name** (30 max)

```
Agentchi: Code Agent Buddy
```

**Short description** (80 max)

```
Your coding agents on your wrist, with a pixel-art buddy to look after.
```

**Full description** (4,000 max)

```
Agentchi keeps you company while your coding agents work: their requests reach your Wear OS watch, you answer with a tap, and a tiny pixel-art pet lives to the rhythm of your sessions.

🔔 THEIR EVENTS ON YOUR WRIST
• Permission requests, waiting for input, task done, errors: nothing slips by, even away from your desk.
• Agent messages are formatted: bold, italics, code, headings, lists.
• Choose how you're alerted: full screen, normal or silent notification, vibration pattern, sound.
• Grouped summary for non-urgent events, quiet hours, and support for the watch's Do Not Disturb and Bedtime modes.

⌚ REPLY WITHOUT LEAVING YOUR WATCH
• Allow or deny a command, with or without an explanation.
• Pick one or several of the offered options.
• Approve a plan or ask for changes.
• Reply by keyboard, voice or quick reply when the agent is done.

🦀 A BUDDY TO LOOK AFTER
• Feed it, play with it, pet it and put it to bed: its fullness, energy and happiness drop over time.
• It celebrates when your agent finishes a task and panics when you're needed.
• Every finished task earns experience: it levels up and unlocks new accessories.

🎨 MAKE IT YOURS
• 3 species: Crab, Dots or Ghost.
• 6 colors: Coral, Ink, Ocean, Mint, Lilac, Rose.
• 5 accessories: party hat, bow, sunglasses, top hat, crown.
• Pixel-art or emoji action icons, and an app icon that matches your pet.

📋 EVERYTHING AT A GLANCE
• Ongoing sessions: working, waiting for you, done.
• Tile, complication and Agentchi watch face with your pet front and center.
• Streaks and achievements for days in a row with finished tasks and replies sent from the watch.

🔒 YOUR SERVER, YOUR DATA
Agentchi works with a small open-source server you host yourself. It relays your agents' events to your watch through Firebase Cloud Messaging and stores nothing. Each computer is paired with its own code, revocable from the watch. No account, no ads.

✅ COMPATIBILITY
• Works with Claude Code (terminal, IDE, Desktop app, Remote Control, cloud sessions) and Codex (CLI, app, IDE extension).
• Wear OS watches with Google Play services, such as Galaxy Watch 4 and newer.
• Requires an Agentchi server (instructions in the project's GitHub repository).

The app is currently in French.

Agentchi is an unofficial app, not affiliated with Anthropic or OpenAI. Claude and Claude Code are trademarks of Anthropic; Codex and OpenAI are trademarks of OpenAI.
```

> L'appli n'est traduite qu'en français : la phrase « The app is currently in French » évite les avis négatifs. Retire-la si tu traduis l'appli.

---

## 4. Google Play : autres champs de la Console

| Champ | Valeur |
|---|---|
| Type d'appli | Application |
| Catégorie | **Productivité** (ou *Outils*) |
| Tags (jusqu'à 5) | Productivité, Outils pour développeurs, Notifications, Animaux virtuels, Montre connectée |
| Gratuite ou payante | Gratuite |
| Langue par défaut | Français (France) – fr-FR |
| Adresse e-mail de contact | `A_REMPLACER` (obligatoire, publique) |
| Site Web | `https://github.com/Pelagoss/agentchi` |
| Téléphone | Facultatif, laisser vide |
| Règles de confidentialité | `https://github.com/Pelagoss/agentchi/blob/master/docs/PRIVACY.md` (le dépôt doit être public), ou une page GitHub Pages |
| Format | **Wear OS** uniquement (piste dédiée) |
| Pays | France, ou tous les pays |
| Annonces | Non |
| Achats intégrés | Non |
| Classification | Questionnaire IARC → Tout public / PEGI 3 |
| Public cible | 18 ans et plus (ou 13+) |
| Sécurité des données | Voir le § 5 de `docs/PUBLICATION_PLAY_STORE.md` |
| Accès aux applis | Toutes les fonctionnalités sans restriction (pas de compte) ; mentionne le serveur auto-hébergé et le bouton *Tester le push* pour les examinateurs |

**Instructions pour l'examinateur** (*Contenu de l'appli › Accès aux applis*, champ libre) :

```
Agentchi n'a pas de compte. La mascotte, ses soins (Nourrir, Jouer, Dormir), sa personnalisation (Paramètres › Mascotte), la tuile, la complication et les succès marchent sans configuration. Pour les notifications : Paramètres › Serveur › Connecter la montre (serveur de démonstration préconfiguré), puis « Tester le push ».
```

> Vérifie que le serveur de démonstration (`COMPANION_SERVER` du build) est bien en ligne pendant l'examen.

---

## 5. Samsung Galaxy Store (facultatif)

Les Galaxy Watch ont aussi le **Galaxy Store**. La publication se fait sur https://seller.samsungapps.com : compte vendeur gratuit, puis *Add New Application › Android › Wear OS*. Vérifie les exigences au moment de publier : elles changent souvent.

| Champ (Seller Portal) | Valeur |
|---|---|
| Default language | French |
| App title | `Agentchi` |
| Description | Description complète française du § 2 (4 000 caractères max) |
| New features | Notes de version du § 2 |
| Tags | `wear os, notifications, productivité, mascotte, pixel art` |
| Category | Productivity (ou Tools) |
| Icon | `assets/icone/icon-512.png` (512 × 512) |
| Screenshots | 4 minimum : `captures-wear/` (montre, carrées) et, si un format portrait est demandé, `promo/` (1080 × 1920) |
| YouTube URL | La même vidéo que sur Google Play |
| Support e-mail | `A_REMPLACER` |
| Privacy policy URL | La même que sur Google Play |
| Age rating | All ages / 3+ |
| Country / Region | France, ou tous |
| Price | Free |
| Binary | **APK signé** (le Galaxy Store n'accepte pas toujours les AAB) : `./gradlew :app:assembleRelease …` |

Traduction anglaise : *Add language › English*, avec les textes du § 3.

---

## 6. Régénérer les visuels et la vidéo

Les sources sont dans [`source/`](source/) :

| Fichier | Rôle |
|---|---|
| `mascot.js` | Portage JS de `MascotSprite.kt` et `PixelIcon.kt` (mêmes grilles et couleurs). **À mettre à jour** si une espèce, une couleur ou un accessoire change dans l'appli |
| `screens.js` | Les écrans Wear OS redessinés (mascotte, Paramètres › Mascotte, autorisation, sessions, succès, tuile) |
| `watch.js` | La montre générique et le décor |
| `shots.html` | Captures, image de présentation et visuels 9:16 |
| `video.html` | La vidéo : chronologie et scènes. Ouvre-la dans un navigateur pour la regarder en boucle |
| `music.py` | Musique et bruitages synthétisés, calés sur la vidéo |

```bash
cd Stores/source
npm install                 # Playwright (utilise Chromium : npx playwright install chromium si besoin)
npm run assets              # → assets/icone, presentation, captures-wear, promo (et assets/en/ en anglais)
npm run video               # → assets/video/ et assets/en/video/ : agentchi-companion.mp4 et poster.png (ffmpeg et python3 requis, ~2 min par langue)
```

L'icône de la fiche est lue directement dans `app/src/main/res/drawable/` (`ic_mascot_crab_ocean_foreground.xml` et `ic_launcher_foreground.xml`) : elle reste identique à celle de la montre.

**Page d'accueil du serveur** : `server/web/static/` embarque une copie de `mascot.js`, `screens.js` et `watch.js` (les montres de la page sont dessinées en direct), ainsi que l'icône et, pour chaque langue (`img/fr`, `img/en`, `video/fr`, `video/en`), l'image de présentation, quatre captures réduites à 480 px, la vidéo et son poster en JPEG. Après une modification de ces scripts, recopie-les : `cp Stores/source/{mascot,screens,watch}.js server/web/static/js/` (le test `TestScriptsMascotteSynchronises` le vérifie).
