# Politique de confidentialité — Claude Companion

*Dernière mise à jour : 20 octobre 2026*

Claude Companion est une application non officielle, sans lien avec Anthropic. Elle est composée d'une appli téléphone (Android) et d'une appli montre (Wear OS).

## Données traitées

**Appli téléphone.** Avec ton autorisation explicite (accès aux notifications d'Android), l'appli lit **uniquement** les notifications de l'appli Claude (`com.anthropic.claude`) : titre, texte et nom de la conversation. Elle les transmet à ta montre par la liaison Bluetooth/Wear OS de Google Play services, entre tes propres appareils. Les notifications des autres applis sont ignorées.

**Appli montre.** Elle affiche ces notifications et conserve sur la montre un historique des 30 dernières, ainsi que l'état de la mascotte et tes réglages.

**Serveur Claude Companion.** La montre se connecte au serveur dont tu lui donnes l'adresse, en général le tien (dossier `server/` du projet). Les hooks Claude Code de tes ordinateurs et de tes sessions cloud y envoient leurs événements : nom du dossier, identifiant court de session, nom donné à l'ordinateur, 300 premiers caractères du dernier message de Claude. Le serveur les garde 48 heures au plus et ne les transmet qu'à ta montre. Il ne conserve qu'une empreinte des codes d'appairage et du jeton de la montre. *Ordinateurs et cloud › Déconnecter la montre* supprime la montre, ses codes et ses événements du serveur.

## Ce que nous ne faisons pas

- Aucune donnée n'est envoyée au développeur de l'application.
- Aucune analyse statistique, publicité ni traceur.
- Aucune donnée n'est vendue ni partagée à des fins commerciales.

## Conservation et suppression

Les données restent sur tes appareils et sur le serveur que tu as choisi. *Historique → Tout effacer* vide l'historique de la montre. Désinstaller les applis supprime l'ensemble des données. Tu peux retirer l'accès aux notifications à tout moment dans les paramètres Android.

## Contact

Pour toute question : ouvre un ticket sur https://github.com/Pelagoss/claude-companion/issues
