package main

import (
	"bytes"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

// getHome charge la page d'accueil avec les en-têtes donnés (« Nom: valeur »).
func getHome(t *testing.T, url string, headers ...string) (*http.Response, string) {
	t.Helper()
	req, _ := http.NewRequest(http.MethodGet, url, nil)
	for _, h := range headers {
		k, v, _ := strings.Cut(h, ": ")
		req.Header.Set(k, v)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	return resp, string(body)
}

func TestPageAccueil(t *testing.T) {
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	// Les informations de l'ancienne page d'accueil restent toutes présentes, dans les deux langues, avec l'adresse du serveur.
	common := []string{
		env.url,
		"claude plugin install agentchi --marketplace Pelagoss/agentchi",
		"--config server_url=" + env.url,
		`href="/v1/cloud-settings.json"`,
		"&#34;hooks&#34;",
		"curl -fsS " + env.url + "/v1/codex-hooks.json -o ~/.codex/hooks.json",
		"/static/site.css?v=" + assetVersion,
	}
	for lang, wants := range map[string][]string{
		"en": {`<html lang="en"`, "Settings › Server › New code", "AGENTCHI_CODE=&lt;your code&gt;", "approve Agentchi's", "not affiliated with Anthropic or OpenAI",
			"/static/img/en/01-mascotte.png", "/static/video/en/agentchi-companion.mp4", "docs/PRIVACY.md", `href="/?lang=fr"`},
		"fr": {`<html lang="fr"`, "Paramètres › Serveur › Nouveau code", "AGENTCHI_CODE=&lt;ton code&gt;", "approuve ceux d'Agentchi", "sans lien avec Anthropic ni OpenAI",
			"/static/img/fr/01-mascotte.png", "/static/video/fr/agentchi-companion.mp4", "docs/PRIVACY.fr.md", `href="/?lang=en"`},
	} {
		resp, page := getHome(t, env.url+"/?lang="+lang)
		if resp.StatusCode != http.StatusOK || !strings.HasPrefix(resp.Header.Get("Content-Type"), "text/html") {
			t.Fatalf("%s : statut %d, type %q", lang, resp.StatusCode, resp.Header.Get("Content-Type"))
		}
		if got := resp.Header.Get("Content-Language"); got != lang {
			t.Errorf("%s : Content-Language %q", lang, got)
		}
		for _, want := range append(common, wants...) {
			if !strings.Contains(page, want) {
				t.Errorf("la page d'accueil (%s) ne contient pas %q", lang, want)
			}
		}
		// Chaque fichier statique cité par la page existe.
		for _, m := range regexp.MustCompile(`/static/[^"?\s]+`).FindAllString(page, -1) {
			if _, err := webFiles.ReadFile("web" + m); err != nil {
				t.Errorf("la page d'accueil (%s) cite %s, absent de web/static", lang, m)
			}
		}
	}
}

func TestLanguePageAccueil(t *testing.T) {
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	for _, c := range []struct {
		name    string
		path    string
		headers []string
		want    string
	}{
		{"anglais par défaut", "/", nil, "en"},
		{"navigateur français", "/", []string{"Accept-Language: fr-FR,fr;q=0.9,en;q=0.8"}, "fr"},
		{"anglais préféré", "/", []string{"Accept-Language: en-US,en;q=0.9,fr;q=0.8"}, "en"},
		{"q décide", "/", []string{"Accept-Language: de;q=1, en;q=0.5, fr;q=0.7"}, "fr"},
		{"langue indisponible", "/", []string{"Accept-Language: de-DE"}, "en"},
		{"choix explicite", "/?lang=fr", []string{"Accept-Language: en"}, "fr"},
		{"choix inconnu ignoré", "/?lang=xx", []string{"Accept-Language: fr"}, "fr"},
		{"cookie retenu", "/", []string{"Accept-Language: en", "Cookie: " + langCookie + "=fr"}, "fr"},
		{"choix plus fort que le cookie", "/?lang=en", []string{"Cookie: " + langCookie + "=fr"}, "en"},
	} {
		resp, page := getHome(t, env.url+c.path, c.headers...)
		if got := resp.Header.Get("Content-Language"); got != c.want || !strings.Contains(page, `<html lang="`+c.want+`"`) {
			t.Errorf("%s : langue %q, attendu %q", c.name, got, c.want)
		}
		if !strings.Contains(resp.Header.Get("Vary"), "Accept-Language") {
			t.Errorf("%s : Vary %q", c.name, resp.Header.Get("Vary"))
		}
		chosen := false
		for _, ck := range resp.Cookies() {
			chosen = chosen || (ck.Name == langCookie && ck.Value == c.want)
		}
		if chosen != strings.Contains(c.path, "lang="+c.want) {
			t.Errorf("%s : cookie de langue posé = %v", c.name, chosen)
		}
	}
}

func TestFichiersStatiques(t *testing.T) {
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	for path, typ := range map[string]string{
		"/static/site.css":                        "text/css",
		"/static/home.js":                         "text/javascript",
		"/static/js/mascot.js":                    "text/javascript",
		"/static/fonts/inter.woff2":               "font/woff2",
		"/static/img/icon-512.png":                "image/png",
		"/static/video/en/agentchi-companion.mp4": "video/mp4",
		"/static/video/fr/agentchi-companion.mp4": "video/mp4",
		"/static/img/en/og.png":                   "image/png",
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
