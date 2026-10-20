// Serveur Claude Companion : reçoit les hooks Claude Code (plugin ou hook http)
// et les met à disposition de la montre, au format de l'API d'abonnement de ntfy.
//
// Configuration par variables d'environnement :
//
//	CC_ADDR         adresse d'écoute (défaut :8080)
//	CC_DATA         fichier d'état JSON (défaut data/state.json)
//	CC_PUBLIC_URL   URL publique, par ex. https://cc.example.fr (sinon déduite de la requête)
//	CC_TRUST_PROXY  « true » derrière un reverse proxy (X-Forwarded-For / -Proto)
//	CC_MAX_DEVICES  nombre maximal de montres (défaut 1000, 0 = illimité)
package main

import (
	"errors"
	"log"
	"net/http"
	"os"
	"strconv"
	"time"
)

func main() {
	maxDevices := 1000
	if v := os.Getenv("CC_MAX_DEVICES"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil {
			log.Fatalf("CC_MAX_DEVICES invalide : %v", err)
		}
		maxDevices = n
	}
	store, err := OpenStore(env("CC_DATA", "data/state.json"))
	if err != nil {
		log.Fatalf("lecture de l'état : %v", err)
	}
	srv := NewServer(Config{
		PublicURL:  os.Getenv("CC_PUBLIC_URL"),
		TrustProxy: os.Getenv("CC_TRUST_PROXY") == "true",
		MaxDevices: maxDevices,
	}, store)

	addr := env("CC_ADDR", ":8080")
	httpServer := &http.Server{
		Addr:              addr,
		Handler:           srv,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		// Pas de WriteTimeout : le flux du mode instantané reste ouvert.
		IdleTimeout: 120 * time.Second,
	}
	log.Printf("Claude Companion écoute sur %s", addr)
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
