package main

import (
	"bytes"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestPageAccueil(t *testing.T) {
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	resp, err := http.Get(env.url + "/")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK || !strings.HasPrefix(resp.Header.Get("Content-Type"), "text/html") {
		t.Fatalf("statut %d, type %q", resp.StatusCode, resp.Header.Get("Content-Type"))
	}
	body, _ := io.ReadAll(resp.Body)
	page := string(body)
	// Les informations de l'ancienne page d'accueil restent toutes présentes, avec l'adresse du serveur.
	for _, want := range []string{
		env.url,
		"Paramètres › Serveur › Nouveau code",
		"claude plugin install agentchi --marketplace Pelagoss/agentchi",
		"--config server_url=" + env.url,
		`href="/v1/cloud-settings.json"`,
		"AGENTCHI_CODE=&lt;ton code&gt;",
		"&#34;hooks&#34;",
		"curl -fsS " + env.url + "/v1/codex-hooks.json -o ~/.codex/hooks.json",
		"approuve ceux d'Agentchi",
		"sans lien avec Anthropic ni OpenAI",
		"/static/site.css?v=" + assetVersion,
	} {
		if !strings.Contains(page, want) {
			t.Errorf("la page d'accueil ne contient pas %q", want)
		}
	}
}

func TestFichiersStatiques(t *testing.T) {
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	for path, typ := range map[string]string{
		"/static/site.css":                     "text/css",
		"/static/home.js":                      "text/javascript",
		"/static/js/mascot.js":                 "text/javascript",
		"/static/fonts/inter.woff2":            "font/woff2",
		"/static/img/icon-512.png":             "image/png",
		"/static/video/agentchi-companion.mp4": "video/mp4",
	} {
		resp, err := http.Get(env.url + path)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK || !strings.HasPrefix(resp.Header.Get("Content-Type"), typ) {
			t.Errorf("%s : statut %d, type %q", path, resp.StatusCode, resp.Header.Get("Content-Type"))
		}
		if resp.Header.Get("Cache-Control") == "" {
			t.Errorf("%s : pas de Cache-Control", path)
		}
	}
	for _, path := range []string{"/static/", "/static/js/", "/static/nope.css"} {
		resp, err := http.Get(env.url + path)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusNotFound {
			t.Errorf("%s : statut %d, attendu 404", path, resp.StatusCode)
		}
	}
}

// Les scripts de la mascotte sont copiés depuis Stores/source (le contexte Docker ne voit que server/).
func TestScriptsMascotteSynchronises(t *testing.T) {
	for _, name := range []string{"mascot.js", "screens.js", "watch.js"} {
		src, err := os.ReadFile(filepath.Join("..", "Stores", "source", name))
		if os.IsNotExist(err) {
			t.Skip("Stores/source absent")
		}
		if err != nil {
			t.Fatal(err)
		}
		embedded, err := webFiles.ReadFile("web/static/js/" + name)
		if err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(src, embedded) {
			t.Errorf("web/static/js/%s diffère de Stores/source/%s : recopie-le", name, name)
		}
	}
}
