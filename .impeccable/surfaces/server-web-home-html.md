---
version: 1
slug: "server-web-home-html"
primary_target: "server/web/home.html"
related_targets: ["server"]
---

# Surface : page d'accueil du serveur (`GET /`)

Mode : **Persuade**. Visiteurs : nouveaux développeurs à convaincre d'abord, utilisateurs équipés ensuite (lien direct vers le guide). Action : « Installer Agentchi » mène au guide d'installation de la même page (pas de lien Play Store publié). Contraintes : toutes les infos de l'ancienne page restent présentes (adresse du serveur, 4 étapes, bloc cloud-settings, liens JSON) ; assets de `Stores/` ; vidéo servie par le serveur ; marques citées seulement pour la compatibilité.

## Direction contract

THESIS : la page rejoue une vraie journée avec ton agent sur une montre vivante, au lieu de la landing « hero + grille de cartes de fonctions » de la catégorie.

OWN-WORLD : monde des visuels des stores. Encre chaude #161514 et halo corail #D97757, trame de points pixel, crème #F0EEE6, Inter / Inter Display 800 serrée, JetBrains Mono pour les commandes. Montres rondes dessinées en canvas avec les vraies grilles (mascot.js, screens.js, watch.js), icônes pixel 8×8, boutons pilule corail.

STORY : je comprends en un écran que mes agents arrivent au poignet ; je vois une journée (autorisation, question, sessions, tâche fêtée, mascotte nourrie, série, nuit) ; je fais la mascotte à mon image ; j'apprends que le serveur est à moi ; j'installe.

FIRST VIEWPORT : à gauche, titre « Tes agents de code au poignet, / avec un compagnon pixel-art. » (Inter Display 800, ~5rem), chapeau, CTA corail « Installer Agentchi » + « Voir la vidéo · 43 s », ligne de compatibilité. À droite, montre canvas en grand (≈ 440 px) avec Chi animé ; ses trois boutons sont touchables (nourrir, jouer, dormir).

FORM : « Le poignet en direct », n° 2 de la liste ordonnée, seed 9f336e61. Interaction signature : montre collante qui change d'écran au défilement de la journée ; personnalisation en direct qui s'applique à toutes les mascottes de la page.

FINISH : unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
