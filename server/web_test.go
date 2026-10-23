package main

import (
	"bytes"
	"encoding/json"
	"encoding/xml"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

// getHome charge une page sans suivre les redirections, avec les en-têtes donnés (« Nom: valeur »).
func getHome(t *testing.T, url string, headers ...string) (*http.Response, string) {
	t.Helper()
	req, _ := http.NewRequest(http.MethodGet, url, nil)
	for _, h := range headers {
		k, v, _ := strings.Cut(h, ": ")
		req.Header.Set(k, v)
	}
	client := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, err := client.Do(req)
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
	for path, c := range map[string]struct {
		lang  string
		wants []string
	}{
		"/": {"en", []string{`<html lang="en"`, "Settings › Server › New code", "AGENTCHI_CODE=&lt;your code&gt;", "approve Agentchi's", "not affiliated with Anthropic or OpenAI",
			"/static/img/en/01-mascotte.png", "/static/video/en/agentchi-companion.mp4", "docs/PRIVACY.md", `href="/?lang=fr"`}},
		"/fr/": {"fr", []string{`<html lang="fr"`, "Paramètres › Serveur › Nouveau code", "AGENTCHI_CODE=&lt;ton code&gt;", "approuve ceux d'Agentchi", "sans lien avec Anthropic ni OpenAI",
			"/static/img/fr/01-mascotte.png", "/static/video/fr/agentchi-companion.mp4", "docs/PRIVACY.fr.md", `href="/?lang=en"`}},
	} {
		resp, page := getHome(t, env.url+path)
		if resp.StatusCode != http.StatusOK || !strings.HasPrefix(resp.Header.Get("Content-Type"), "text/html") {
			t.Fatalf("%s : statut %d, type %q", path, resp.StatusCode, resp.Header.Get("Content-Type"))
		}
		if got := resp.Header.Get("Content-Language"); got != c.lang {
			t.Errorf("%s : Content-Language %q", path, got)
		}
		for _, want := range append(common, c.wants...) {
			if !strings.Contains(page, want) {
				t.Errorf("la page d'accueil (%s) ne contient pas %q", path, want)
			}
		}
		// Chaque fichier statique cité par la page existe.
		for _, m := range regexp.MustCompile(`/static/[^"?\s]+`).FindAllString(page, -1) {
			if _, err := webFiles.ReadFile("web" + m); err != nil {
				t.Errorf("la page d'accueil (%s) cite %s, absent de web/static", path, m)
			}
		}
	}
}

// Une adresse par langue (/ et /fr/) : les robots, sans cookie ni Accept-Language, voient chacune dans sa langue ;
// un visiteur est envoyé vers la page de sa langue, et le lien FR/EN (?lang=) retient son choix.
func TestLanguePageAccueil(t *testing.T) {
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	for _, c := range []struct {
		name     string
		path     string
		headers  []string
		location string // redirection attendue, "" : page servie
		cookie   string // cookie de langue posé
	}{
		{"robot sur /", "/", nil, "", ""},
		{"robot sur /fr/", "/fr/", nil, "", ""},
		{"navigateur anglais", "/", []string{"Accept-Language: en-US,en;q=0.9,fr;q=0.8"}, "", ""},
		{"navigateur français", "/", []string{"Accept-Language: fr-FR,fr;q=0.9,en;q=0.8"}, "/fr/", ""},
		{"q décide", "/", []string{"Accept-Language: de;q=1, en;q=0.5, fr;q=0.7"}, "/fr/", ""},
		{"langue indisponible", "/", []string{"Accept-Language: de-DE"}, "", ""},
		{"navigateur anglais sur /fr/", "/fr/", []string{"Accept-Language: en"}, "", ""},
		{"lien FR", "/?lang=fr", []string{"Accept-Language: en"}, "/fr/", "fr"},
		{"lien EN", "/?lang=en", []string{"Accept-Language: fr", "Cookie: " + langCookie + "=fr"}, "/", "en"},
		{"choix inconnu", "/?lang=xx", []string{"Accept-Language: fr"}, "/fr/", ""},
		{"cookie retenu", "/", []string{"Accept-Language: en", "Cookie: " + langCookie + "=fr"}, "/fr/", ""},
		{"anglais retenu", "/", []string{"Accept-Language: fr", "Cookie: " + langCookie + "=en"}, "", ""},
		{"/fr sans barre", "/fr", nil, "/fr/", ""},
		{"paramètres de suivi conservés", "/?utm_source=x", nil, "", ""},
	} {
		resp, _ := getHome(t, env.url+c.path, c.headers...)
		if c.location == "" && resp.StatusCode != http.StatusOK || c.location != "" && resp.Header.Get("Location") != c.location {
			t.Errorf("%s : statut %d, Location %q, attendu %q", c.name, resp.StatusCode, resp.Header.Get("Location"), c.location)
		}
		cookie := ""
		for _, ck := range resp.Cookies() {
			if ck.Name == langCookie {
				cookie = ck.Value
			}
		}
		if cookie != c.cookie {
			t.Errorf("%s : cookie %q, attendu %q", c.name, cookie, c.cookie)
		}
	}
	if resp, _ := getHome(t, env.url+"/fr/x"); resp.StatusCode != http.StatusNotFound {
		t.Errorf("/fr/x : statut %d", resp.StatusCode)
	}
}

// Serveur public : canonique, hreflang, données structurées, plan du site. Auto-hébergé : noindex.
func TestReferencement(t *testing.T) {
	store, err := OpenStore(filepath.Join(t.TempDir(), "state.json"))
	if err != nil {
		t.Fatal(err)
	}
	public := httptest.NewServer(NewServer(Config{PublicURL: "https://agentchi.dev", Indexable: true}, store, &fakePusher{}))
	t.Cleanup(public.Close)

	for path, canonical := range map[string]string{"/": "https://agentchi.dev/", "/fr/": "https://agentchi.dev/fr/"} {
		resp, page := getHome(t, public.URL+path)
		for _, want := range []string{
			`<link rel="canonical" href="` + canonical + `">`,
			`<meta property="og:url" content="` + canonical + `">`,
			`<link rel="alternate" hreflang="en" href="https://agentchi.dev/">`,
			`<link rel="alternate" hreflang="fr" href="https://agentchi.dev/fr/">`,
			`<link rel="alternate" hreflang="x-default" href="https://agentchi.dev/">`,
		} {
			if !strings.Contains(page, want) {
				t.Errorf("%s ne contient pas %q", path, want)
			}
		}
		if strings.Contains(page, "noindex") || resp.Header.Get("X-Robots-Tag") != "" {
			t.Errorf("%s : serveur public en noindex", path)
		}
		m := regexp.MustCompile(`(?s)<script type="application/ld\+json">(.*?)</script>`).FindStringSubmatch(page)
		var ld struct {
			Graph []map[string]any `json:"@graph"`
		}
		if m == nil || json.Unmarshal([]byte(m[1]), &ld) != nil || len(ld.Graph) != 2 || ld.Graph[0]["url"] != canonical {
			t.Errorf("%s : données structurées invalides : %v", path, m)
		}
	}

	_, robots := getHome(t, public.URL+"/robots.txt")
	if !strings.Contains(robots, "Disallow: /v1/") || !strings.Contains(robots, "Sitemap: https://agentchi.dev/sitemap.xml") {
		t.Errorf("robots.txt : %q", robots)
	}
	resp, sitemap := getHome(t, public.URL+"/sitemap.xml")
	if resp.StatusCode != http.StatusOK || xml.Unmarshal([]byte(sitemap), new(struct{})) != nil ||
		strings.Count(sitemap, "<loc>") != 2 || !strings.Contains(sitemap, `hreflang="fr" href="https://agentchi.dev/fr/"`) {
		t.Errorf("sitemap.xml : %d %s", resp.StatusCode, sitemap)
	}

	// Instance auto-hébergée (par défaut) : explorable, mais ni indexée ni annoncée.
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	resp, page := getHome(t, env.url+"/")
	if resp.Header.Get("X-Robots-Tag") != "noindex" || !strings.Contains(page, `<meta name="robots" content="noindex">`) {
		t.Errorf("instance auto-hébergée indexable : %q", resp.Header.Get("X-Robots-Tag"))
	}
	if _, robots := getHome(t, env.url+"/robots.txt"); strings.Contains(robots, "Sitemap") {
		t.Errorf("robots.txt auto-hébergé : %q", robots)
	}
	if resp, _ := getHome(t, env.url+"/sitemap.xml"); resp.StatusCode != http.StatusNotFound {
		t.Errorf("sitemap.xml auto-hébergé : %d", resp.StatusCode)
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
