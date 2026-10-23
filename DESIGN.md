---
name: Agentchi
description: Tes agents de code au poignet, avec un compagnon pixel-art.
colors:
  coral: "#d97757"
  coral-hi: "#e8896a"
  on-coral: "#1f1e1d"
  ink-0: "#0e0d0c"
  ink: "#161514"
  ink-2: "#1e1c1a"
  ink-3: "#2b2826"
  ink-4: "#3a3633"
  screen: "#000000"
  bezel: "#2e2b29"
  bezel-edge: "#45403c"
  cream: "#f0eee6"
  muted: "#b8b2a7"
  faint: "#a29c91"
  line: "rgba(240, 238, 230, 0.1)"
  line-2: "rgba(240, 238, 230, 0.18)"
typography:
  display:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "clamp(2.6rem, 4.6vw, 4.3rem)"
    fontWeight: 800
    lineHeight: 0.98
    letterSpacing: "-0.04em"
  headline:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "clamp(2.2rem, 5vw, 3.6rem)"
    fontWeight: 800
    lineHeight: 1.02
    letterSpacing: "-0.035em"
  title:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "clamp(1.7rem, 3.2vw, 2.5rem)"
    fontWeight: 800
    lineHeight: 1.08
    letterSpacing: "-0.03em"
  title-sm:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.4rem"
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  lead:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "clamp(1.1rem, 1.7vw, 1.3rem)"
    fontWeight: 400
    lineHeight: 1.6
  body:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.6
  button:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.02rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.01em"
  heading-xs:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.15rem"
    fontWeight: 700
    lineHeight: 1.35
    letterSpacing: "-0.015em"
  body-sm:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "0.92rem"
    fontWeight: 600
    lineHeight: 1
  caption:
    fontFamily: "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "0.82rem"
    fontWeight: 500
    lineHeight: 1.4
  mono:
    fontFamily: "'JetBrains Mono', ui-monospace, 'Cascadia Mono', Menlo, monospace"
    fontSize: "0.86em"
    fontWeight: 500
    lineHeight: 1.65
rounded:
  sm: "6px"
  md: "8px"
  tile: "12px"
  lg: "14px"
  xl: "16px"
  card: "22px"
  pill: "999px"
spacing:
  xs: "6px"
  sm: "8px"
  md: "12px"
  lg: "18px"
  xl: "28px"
  gutter: "clamp(16px, 4vw, 40px)"
  section: "clamp(64px, 9vw, 120px)"
components:
  button-primary:
    backgroundColor: "{colors.coral}"
    textColor: "{colors.on-coral}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: "17px 26px"
  button-primary-hover:
    backgroundColor: "{colors.coral-hi}"
    textColor: "{colors.on-coral}"
  button-small:
    backgroundColor: "{colors.coral}"
    textColor: "{colors.on-coral}"
    rounded: "{rounded.pill}"
    padding: "10px 18px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.cream}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: "17px 26px"
  button-ghost-hover:
    backgroundColor: "{colors.ink-3}"
    textColor: "{colors.cream}"
  chip-care:
    backgroundColor: "{colors.ink-3}"
    textColor: "{colors.cream}"
    rounded: "{rounded.pill}"
    padding: "9px 14px 9px 10px"
  chip-care-pressed:
    backgroundColor: "{colors.coral}"
    textColor: "{colors.on-coral}"
  option-tile:
    backgroundColor: "{colors.ink-2}"
    textColor: "{colors.muted}"
    rounded: "{rounded.lg}"
    padding: "8px 14px 8px 8px"
  option-tile-checked:
    backgroundColor: "{colors.ink-3}"
    textColor: "{colors.cream}"
  tab:
    backgroundColor: "transparent"
    textColor: "{colors.muted}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "10px 16px"
  tab-selected:
    backgroundColor: "{colors.coral}"
    textColor: "{colors.on-coral}"
  nav-link:
    textColor: "{colors.muted}"
    rounded: "{rounded.pill}"
    padding: "8px 12px"
  nav-link-hover:
    backgroundColor: "{colors.ink-3}"
    textColor: "{colors.cream}"
  card-guide:
    backgroundColor: "{colors.ink-2}"
    rounded: "{rounded.card}"
    padding: "26px 28px 26px 84px"
  panel:
    backgroundColor: "{colors.ink-2}"
    rounded: "{rounded.xl}"
    padding: "14px 18px"
  code-block:
    backgroundColor: "{colors.ink-0}"
    textColor: "{colors.cream}"
    typography: "{typography.mono}"
    rounded: "{rounded.lg}"
    padding: "48px 18px 16px"
---

# Design System: Agentchi

## Overview

**Creative North Star: "Le poignet en direct"**

Le site prolonge les visuels des stores : une nuit d'encre chaude, une montre ronde qui brille d'un halo corail au milieu, et une mascotte pixel-art qui vit dessus. La montre n'est pas une illustration : elle est dessinée en canvas à partir des vraies grilles de l'appli (`mascot.js`, `screens.js`, `watch.js`, copiés de `Stores/source`). Tout le reste de la page sert de décor sobre à cet objet vivant.

Le ton visuel est chaleureux, sûr de lui et joueur sans être enfantin. Les titres sont gras et serrés, le texte courant est calme et crème sur encre, le corail ne sert qu'à agir et à choisir. Deux matières cohabitent : l'interface (pilules, cartes aux coins doux, filets fins) et le pixel (carrés nets, rendu `pixelated`, jamais lissé). La densité est aérée : grandes sections séparées par un filet, colonnes en grille asymétrique, montre collante qui accompagne le défilement.

La profondeur vient de la lumière et des tons d'encre, pas des ombres : halos corail radiaux derrière la montre et en haut de l'installation, fond de trame pixel discrète (un point 2×2 tous les 28 px), surfaces qui montent d'un cran d'encre.

**Key Characteristics:**
- Encre chaude, jamais de gris neutre ni de noir pur (sauf l'écran de la montre).
- Un seul accent, le corail, réservé aux actions, aux sélections et à la seconde ligne des titres.
- Inter 800 serrée pour les titres, Inter 400 pour lire, JetBrains Mono seulement pour les commandes.
- Pilules pour tout ce qui se touche, coins doux pour ce qui contient, carrés nets pour le pixel.
- Montres canvas fidèles à l'appli, au centre de chaque section clé.

## Colors

Une palette de nuit chaude : quatre crans d'encre brune, du crème pour le texte, et un corail unique qui porte toute l'énergie.

### Primary
- **Corail Agentchi** (coral) : fond des boutons principaux, onglet et pastille sélectionnés, numéros d'étapes du guide, pixel qui voyage sur les fils du schéma, contour 2 px des choix cochés, seconde ligne du titre du héros. Aussi la teinte des halos radiaux (à 12-30 % d'opacité).
- **Corail clair** (coral-hi) : survol des boutons corail, liens dans le texte, valeurs à copier (adresse du serveur, `code.k`), anneau de focus.
- **Encre sur corail** (on-coral) : texte et icônes posés sur le corail. Jamais de blanc sur le corail.

### Neutral
- **Encre profonde** (ink-0) : fond des blocs de code, des listes d'onglets, de la vidéo, de la section serveur et du pied de page. Le cran le plus bas.
- **Encre** (ink) : fond de page, avec la trame pixel par-dessus. Aussi la `theme-color`.
- **Encre relevée** (ink-2) : cartes du guide, nœuds du schéma, encart d'adresse, tuiles d'options au repos.
- **Encre haute** (ink-3) : pastilles de soin, boutons Copier, survol de la navigation et des boutons fantômes, code en ligne, tuile cochée.
- **Encre de survol** (ink-4) : survol des pastilles qui reposent déjà sur ink-3 (soin, Copier).
- **Lunette** (bezel) et **Arête de lunette** (bezel-edge) : boîtier des montres rondes en image et dans le schéma, tracé en anneaux de `box-shadow` autour d'un cercle.
- **Crème** (cream) : titres, texte fort, texte des boutons fantômes.
- **Crème atténué** (muted) : chapeaux, paragraphes secondaires, liens de navigation au repos.
- **Crème pâle** (faint) : légendes, lignes de compatibilité, sous-libellés, indices.
- **Filet** (line) et **Filet marqué** (line-2) : séparateurs de sections, contours intérieurs des surfaces (line), contour des boutons fantômes et lignes de la liste de caractéristiques (line-2).

### Named Rules
**The Coral Voice Rule.** Le corail veut dire « agis ici » ou « c'est choisi ». Il ne sert jamais de décor plat, seulement de lumière diffuse (halo) ou d'état. Un écran porte un bouton corail plein à la fois, plus les états sélectionnés.

**The Warm Ink Rule.** Tous les neutres tirent vers le brun chaud (teinte autour de 60-95° en OKLCH). Pas de gris froid, pas de `#000` hors de l'écran de la montre, qui est l'objet lui-même.

## Typography

**Display Font:** Inter variable, auto-hébergée (avec system-ui, -apple-system, Segoe UI)
**Body Font:** Inter
**Label/Mono Font:** JetBrains Mono 500 (avec ui-monospace, Cascadia Mono, Menlo)

**Character:** Une seule famille, poussée aux extrêmes : 800 très serré pour les titres (le découpage optique d'Inter prend le relais aux grandes tailles via `font-optical-sizing: auto`), 400 aéré pour lire. La mono n'apparaît que là où l'on copie-colle.

### Hierarchy
- **Display** (800, clamp(2.6rem, 4.6vw, 4.3rem), 0.98) : le seul titre du héros, en deux temps (crème puis corail sur une nouvelle ligne).
- **Headline** (800, clamp(2.2rem, 5vw, 3.6rem), 1.02) : titres de section.
- **Title** (800, clamp(1.7rem, 3.2vw, 2.5rem), 1.08) : étapes de la journée. **Title-sm** (800, 1.4rem, 1.25) : étapes du guide d'installation.
- **Heading-xs** (700, 1.15rem, 1.35) : intitulés des listes (fonctions, engagements), nom de la marque, numéros du guide.
- **Lead** (400, clamp(1.1rem, 1.7vw, 1.3rem), 1.6) : chapeau du héros (max 34em) et chapeaux de section (max 60ch).
- **Body** (400, 17px, 1.6) : texte courant, en crème atténué pour les paragraphes secondaires, max 34em dans les étapes. **Body-sm** (400, 1rem) : descriptions des listes et du guide.
- **Button** (700, 1.02rem, -0.01em) et **Label** (600, 0.92rem) : boutons, pastilles, onglets, navigation, légendes courtes. Pas de majuscules forcées.
- **Caption** (500, 0.82rem, 1.4) : mentions secondaires (niveau d'un accessoire, sous-titres du schéma, étiquette de l'adresse, boutons Copier).
- Pas d'autre taille : une valeur hors de cette échelle se ramène à la plus proche.
- **Mono** (500, 0.86em, 1.65) : commandes, chemins, codes. En ligne sur une pastille ink-3 aux coins de 6 px.

### Named Rules
**The Heavy Tight Rule.** Tout titre est en 800 avec un interlettrage négatif (-0.02em à -0.04em) et `text-wrap: balance`. Pas de titre en graisse légère.

**The Mono Means Command Rule.** JetBrains Mono est réservée à ce que l'utilisateur tape ou copie. Jamais pour un titre, une étiquette ou un effet « tech ».

## Layout

Conteneur centré de 1180 px avec une gouttière fluide (clamp(16px, 4vw, 40px)). Chaque section a un grand souffle vertical (clamp(64px, 9vw, 120px)) et s'ouvre sur un filet `line` en haut ; certaines descendent d'un cran d'encre (serveur, pied) pour rythmer.

Les sections sont des grilles à deux colonnes asymétriques (rapports 1.1/0.9, 0.9/1.1, 0.8/1.2), texte d'un côté, montre ou détail de l'autre, alignées au centre ou en haut. La colonne courte devient collante (`position: sticky`, sous l'en-tête de 64 px) quand l'autre défile : montre de la journée, vignettes de cadrans, titre de l'installation.

Points de rupture : 980 px (une colonne, navigation masquée, schéma vertical), 760 px (la montre de la journée se colle en haut sous l'en-tête, la montre du héros passe avant le texte), 420 px (boutons d'action pleine largeur). Écarts récurrents : 6, 8, 12, 18 et 28 px.

## Elevation & Depth

Le système est plat et tonal. Une surface monte d'un cran d'encre (ink → ink-2 → ink-3) et se détoure par un filet intérieur, pas par une ombre. La lumière vient des halos corail radiaux, posés derrière les montres et en coin de section. Les rares ombres sont douces, à étalement négatif, et tombent sous un objet physique : la pilule corail, la montre, le cadre vidéo.

### Shadow Vocabulary
- **Pilule posée** (`box-shadow: 0 8px 18px -10px rgba(0, 0, 0, 0.7)`) : bouton corail au repos ; au survol `0 12px 24px -12px rgba(0, 0, 0, 0.8)` avec une montée de 2 px.
- **Objet lourd** (`box-shadow: 0 40px 80px -40px rgba(0, 0, 0, 0.9)`) : cadre vidéo, et en version 24-30 px sous les montres en image.
- **Filet intérieur** (`box-shadow: inset 0 0 0 1px var(--line)`) : contour de toutes les surfaces ; 1.5px `line-2` pour le bouton fantôme ; 2px corail pour l'état choisi.
- **Lunette** (`box-shadow: 0 0 0 10px #2e2b29, 0 0 0 11px #45403c`) : boîtier de montre autour d'un cercle (épaisseur 6 à 14 px selon la taille).

### Named Rules
**The Halo Not Shadow Rule.** Pour faire ressortir quelque chose, éclaire-le d'un halo corail ou monte-le d'un cran d'encre. Les ombres portées restent sous les objets qu'on pourrait prendre en main.

**The Inset Border Rule.** Les contours sont des `box-shadow` intérieurs, jamais des `border` qui décalent la mise en page. Les `border` ne servent qu'aux filets de séparation.

## Shapes

Trois formes, chacune avec son rôle. La pilule (999 px) pour tout ce qui se touche : boutons, pastilles, onglets, liens de navigation. Les coins doux pour ce qui contient : 6 px (code en ligne, focus), 8 px (pastilles de couleur), 12 px (numéro d'étape), 14 px (tuiles d'options, blocs de code), 16 px (nœuds, encart d'adresse), 22 px (cartes du guide), clamp(14px, 2vw, 24px) pour la vidéo. Le cercle pour les montres.

Le pixel, lui, reste carré : mascottes et icônes dessinées en canvas avec `image-rendering: pixelated`, pixel voyageur de 8 px sans arrondi sur les fils pointillés du schéma.

### Named Rules
**The Pixel Stays Square Rule.** Ce qui vient de la grille pixel (mascotte, icônes 16 px des pastilles, pixel voyageur, trame de fond) ne s'arrondit pas et ne se lisse pas. L'arrondi est réservé à l'interface.

## Components

### Buttons
Tactiles et francs : une pilule pleine qui se soulève un peu sous le doigt.
- **Shape:** pilule (999 px).
- **Primary:** fond corail, texte encre sur corail, 700, padding 17px 26px, icône SVG trait 16 px à droite ou à gauche (flèche, lecture).
- **Hover / Focus:** fond corail clair, montée de 2 px avec l'ombre qui s'allonge (0.25s, `cubic-bezier(0.16, 1, 0.3, 1)`) ; à l'appui, retour à 0 et léger écrasement (scale 0.98). Focus : anneau corail clair de 2 px, décalé de 3 px.
- **Ghost:** fond transparent, texte crème, filet intérieur 1.5 px line-2 ; survol en ink-3. Toujours à côté d'un bouton principal.
- **Small:** padding 10px 18px, 0.92rem, sans ombre (bouton « Installer » de l'en-tête).
- **Copier:** petite pilule ink-3, 600 0.8rem, posée dans le coin des blocs de code et de l'encart d'adresse ; passe au corail une fois copié.

### Chips
- **Style:** pastilles de soin en pilule ink-3, texte crème 600 0.88rem, icône pixel 16 px en tête.
- **State:** survol ink-4, appui scale 0.95, état actif (`aria-pressed`) en corail plein.

### Option Tiles
- **Style:** tuile ink-2 aux coins de 14 px, filet intérieur `line`, texte crème atténué, aperçu pixel 36×30 ou pastille de couleur 26 px à gauche.
- **State:** survol ink-3 et texte crème ; cochée, contour intérieur corail 2 px. Le choix s'applique en direct à toutes les mascottes de la page.

### Tabs
- **Style:** rail pilule ink-0 avec filet intérieur, onglets pilule transparents en crème atténué.
- **State:** survol en crème, sélectionné en corail plein.

### Cards / Containers
- **Corner Style:** 22 px pour les cartes du guide, 16 px pour les nœuds et encarts.
- **Background:** ink-2.
- **Shadow Strategy:** aucune ombre, filet intérieur `line` (voir Elevation & Depth).
- **Border:** aucun `border`.
- **Internal Padding:** 26-28 px pour les cartes du guide (84 px à gauche pour laisser la place au numéro carré corail de 40 px), 14-18 px pour les nœuds.

### Code Blocks
- **Style:** fond ink-0, coins de 14 px, filet intérieur, JetBrains Mono 0.84rem / 1.65, 48 px de marge haute réservés au bouton Copier, défilement horizontal plutôt que retour à la ligne.

### Navigation
- **Style:** en-tête collant de 64 px, fond encre à 92 %, filet bas qui n'apparaît qu'au défilement. Logo : mini-mascotte canvas 40×34 et « Agentchi » en 800.
- **Links:** pilules crème atténué 500 0.94rem, survol crème sur ink-3. Masqués sous 980 px (le bouton Installer reste).

### Montre vivante (signature)
La montre ronde dessinée en canvas : boîtier, écran noir, vraies grilles de l'appli, posée sur un halo corail radial et fondue en haut et en bas par un masque dégradé. Elle change d'écran au défilement (journée), réagit aux pastilles de soin (héros) et reflète la mascotte composée (studio). Sans JavaScript, une capture de l'appli prend sa place dans le même cercle. Les mini-versions (logo, nœud « Ta montre ») reprennent la mascotte en 40×34 ou 44×38.

## Do's and Don'ts

### Do:
- **Do** dessine chaque écran de montre à partir des grilles réelles de `mascot.js` et `screens.js`, en canvas `pixelated`, avec une capture de l'appli en repli.
- **Do** réserve le corail plein aux actions et aux états choisis, et éclaire les objets clés par un halo corail radial (12-30 % d'opacité).
- **Do** détoure les surfaces par un filet intérieur `inset 0 0 0 1px` en `line`, et marque la sélection par un contour intérieur corail de 2 px.
- **Do** écris les titres en Inter 800 avec un interlettrage de -0.02em à -0.04em et `text-wrap: balance`.
- **Do** mets du texte encre sur corail (on-coral), jamais du blanc.
- **Do** respecte `prefers-reduced-motion` : transitions à 0.01 ms et pixel voyageur masqué.

### Don't:
- **Don't** utilise de gris froid ni de `#000` hors de l'écran de la montre.
- **Don't** arrondis ni ne lisses le pixel-art (mascottes, icônes, pixel voyageur).
- **Don't** utilise JetBrains Mono pour autre chose que des commandes, des chemins ou des codes.
- **Don't** pose une ombre portée sur une carte ou un nœud : monte d'un cran d'encre à la place.
- **Don't** dessine une montre ou un écran inventé qui n'existe pas dans l'appli.
