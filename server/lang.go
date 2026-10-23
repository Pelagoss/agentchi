package main

import "strings"

// Langue des textes que le serveur écrit pour la montre (événements sans message, push de test…),
// transmise par l'appli à l'enregistrement et à chaque envoi de son jeton FCM.

const (
	langFR = "fr"
	langEN = "en"
)

// watchLang normalise la langue envoyée par la montre : "fr", "en", ou "" si elle n'en a pas envoyé.
func watchLang(s string) string {
	switch s = strings.ToLower(strings.TrimSpace(s)); {
	case s == "":
		return ""
	case strings.HasPrefix(s, langFR):
		return langFR
	default:
		return langEN
	}
}

// text choisit le texte selon la langue de la montre. Sans langue (appli d'avant la traduction), français.
func text(lang, fr, en string) string {
	if lang == langEN {
		return en
	}
	return fr
}
