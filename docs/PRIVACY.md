# Politique de confidentialité — Agentchi

*Dernière mise à jour : 21 octobre 2026*

Agentchi est une application non officielle, sans lien avec Anthropic. C'est une appli montre (Wear OS), sans appli téléphone.

## Données traitées

**Appli montre.** Elle affiche les événements de tes sessions Claude Code et conserve sur la montre un historique des 30 derniers, ainsi que l'état de la mascotte et tes réglages.

**Serveur Agentchi.** La montre se connecte au serveur dont tu lui donnes l'adresse, en général le tien (dossier `server/` du projet). Les hooks Claude Code de tes ordinateurs et de tes sessions cloud y envoient leurs événements : nom du dossier, identifiant court de session, nom donné à l'ordinateur, 300 premiers caractères du dernier message de Claude. Le serveur ne les stocke pas : il les transmet aussitôt à ta montre par **Firebase Cloud Messaging** (service de Google), qui les garde au plus 24 heures si la montre est hors ligne. Pour cela, la montre communique au serveur son jeton FCM. Le serveur ne conserve qu'une empreinte des codes d'appairage et du jeton d'accès de la montre. *Ordinateurs et cloud › Déconnecter la montre* supprime du serveur la montre, ses codes et son jeton FCM.

**Réponses depuis la montre.** Si tu actives *Répondre depuis la montre*, les questions de Claude passent aussi par le serveur et Firebase : outil et commande concernés, questions et options proposées, plan ou fin du dernier message de Claude (1 500 caractères au plus). Ta réponse (choix, texte saisi ou dicté) repart de la montre vers le serveur, qui la transmet à Claude Code. Une question en attente ne reste qu'en mémoire du serveur, cinq minutes au plus, et Firebase ne la garde pas au-delà.

## Ce que nous ne faisons pas

- Aucune donnée n'est envoyée au développeur de l'application.
- Aucune analyse statistique, publicité ni traceur.
- Aucune donnée n'est vendue ni partagée à des fins commerciales.

## Conservation et suppression

Les données restent sur tes appareils et sur le serveur que tu as choisi. *Historique → Tout effacer* vide l'historique de la montre. Désinstaller l'appli supprime les données de la montre.

## Contact

Pour toute question : ouvre un ticket sur https://github.com/Pelagoss/claude-companion/issues
