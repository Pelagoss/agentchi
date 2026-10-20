# Politique de confidentialité — Claude Companion

*Dernière mise à jour : 20 octobre 2026*

Claude Companion est une application non officielle, sans lien avec Anthropic. Elle est composée d'une appli téléphone (Android) et d'une appli montre (Wear OS).

## Données traitées

**Appli téléphone.** Avec ton autorisation explicite (accès aux notifications d'Android), l'appli lit **uniquement** les notifications de l'appli Claude (`com.anthropic.claude`) : titre, texte et nom de la conversation. Elle les transmet à ta montre par la liaison Bluetooth/Wear OS de Google Play services, entre tes propres appareils. Les notifications des autres applis sont ignorées.

**Appli montre.** Elle affiche ces notifications et conserve sur la montre un historique des 30 dernières, ainsi que l'état de la mascotte et tes réglages.

**Hook Claude Code (facultatif).** Si tu installes le hook sur ton ordinateur, les événements de tes sessions Claude Code (nom du dossier, extrait du dernier message, adresse e-mail du compte Claude) sont **chiffrés de bout en bout** (AES-256 + HMAC-SHA256) avant d'être publiés sur le service ntfy (ntfy.sh par défaut, ou ton propre serveur). Seule ta montre possède la clé pour les déchiffrer.

## Ce que nous ne faisons pas

- Aucune donnée n'est envoyée au développeur de l'application.
- Aucune analyse statistique, publicité ni traceur.
- Aucune donnée n'est vendue ni partagée à des fins commerciales.

## Conservation et suppression

Toutes les données restent sur tes appareils. *Historique → Tout effacer* vide l'historique de la montre. Désinstaller les applis supprime l'ensemble des données. Tu peux retirer l'accès aux notifications à tout moment dans les paramètres Android.

## Contact

Pour toute question : ouvre un ticket sur https://github.com/Pelagoss/claude-companion/issues
