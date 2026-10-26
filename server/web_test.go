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
			"/static/img/en/01-mascotte.webp", "/static/video/en/agentchi-companion.mp4", `href="/privacy"`, `href="/?lang=fr"`,
			"<title>Claude Code &amp; Codex notifications on your Wear OS watch", `id="faq"`, "<summary>Does Agentchi work on my Galaxy Watch?</summary>"}},
		"/fr/": {"fr", []string{`<html lang="fr"`, "Paramètres › Serveur › Nouveau code", "AGENTCHI_CODE=&lt;ton code&gt;", "approuve ceux d'Agentchi", "sans lien avec Anthropic ni OpenAI",
			"/static/img/fr/01-mascotte.webp", "/static/video/fr/agentchi-companion.mp4", `href="/fr/privacy"`, `href="/?lang=en"`,
			"<title>Notifications Claude Code et Codex sur ta montre Wear OS", `id="faq"`, "<summary>Est-ce gratuit ?</summary>"}},
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
		ld := structuredNodes(t, page)
		if app := ld["SoftwareApplication"]; app == nil || app["url"] != canonical {
			t.Errorf("%s : appli absente des données structurées : %v", path, ld)
		}
		for _, typ := range []string{"WebSite", "Organization", "VideoObject"} {
			if ld[typ] == nil {
				t.Errorf("%s : %s absent des données structurées", path, typ)
			}
		}
		if faq, _ := ld["FAQPage"]["mainEntity"].([]any); len(faq) != len(homeFAQ("en")) {
			t.Errorf("%s : FAQPage avec %d questions", path, len(faq))
		}
	}

	_, robots := getHome(t, public.URL+"/robots.txt")
	if !strings.Contains(robots, "Disallow: /v1/") || !strings.Contains(robots, "Sitemap: https://agentchi.dev/sitemap.xml") {
		t.Errorf("robots.txt : %q", robots)
	}
	resp, sitemap := getHome(t, public.URL+"/sitemap.xml")
	if resp.StatusCode != http.StatusOK || xml.Unmarshal([]byte(sitemap), new(struct{})) != nil ||
		strings.Count(sitemap, "<loc>") != 2*(1+len(guides)) || strings.Count(sitemap, "<lastmod>") != 2*(1+len(guides)) ||
		!strings.Contains(sitemap, `hreflang="fr" href="https://agentchi.dev/fr/"`) ||
		!strings.Contains(sitemap, `<loc>https://agentchi.dev/fr/claude-code-notifications</loc>`) {
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

// structuredNodes : les nœuds schema.org d'une page, par type.
func structuredNodes(t *testing.T, page string) map[string]map[string]any {
	t.Helper()
	m := regexp.MustCompile(`(?s)<script type="application/ld\+json">(.*?)</script>`).FindStringSubmatch(page)
	var ld struct {
		Graph []map[string]any `json:"@graph"`
	}
	if m == nil || json.Unmarshal([]byte(m[1]), &ld) != nil {
		t.Fatalf("données structurées invalides : %v", m)
	}
	nodes := map[string]map[string]any{}
	for _, n := range ld.Graph {
		nodes[n["@type"].(string)] = n
	}
	return nodes
}

// Guides : une adresse par langue, balises de référencement, données structurées et commandes avec l'adresse du serveur.
func TestGuides(t *testing.T) {
	store, err := OpenStore(filepath.Join(t.TempDir(), "state.json"))
	if err != nil {
		t.Fatal(err)
	}
	public := httptest.NewServer(NewServer(Config{PublicURL: "https://agentchi.dev", Indexable: true}, store, &fakePusher{}))
	t.Cleanup(public.Close)

	for _, g := range guides {
		for _, lang := range langs {
			path := pagePath(lang, g.slug)
			resp, page := getHome(t, public.URL+path)
			if resp.StatusCode != http.StatusOK || resp.Header.Get("Content-Language") != lang {
				t.Fatalf("%s : statut %d, langue %q", path, resp.StatusCode, resp.Header.Get("Content-Language"))
			}
			for _, want := range []string{
				`<html lang="` + lang + `"`,
				`<link rel="canonical" href="https://agentchi.dev` + path + `">`,
				`<link rel="alternate" hreflang="en" href="https://agentchi.dev/` + g.slug + `">`,
				`<link rel="alternate" hreflang="fr" href="https://agentchi.dev/fr/` + g.slug + `">`,
				`<link rel="alternate" hreflang="x-default" href="https://agentchi.dev/` + g.slug + `">`,
				`<meta property="og:type" content="article">`,
				"<h1>",
				dateLabel(lang, g.updated),
			} {
				if !strings.Contains(page, want) {
					t.Errorf("%s ne contient pas %q", path, want)
				}
			}
			if strings.Contains(page, "{{") || strings.Contains(page, "noindex") {
				t.Errorf("%s : gabarit mal rendu ou noindex", path)
			}
			ld := structuredNodes(t, page)
			doc := ld["TechArticle"]
			if !g.article {
				doc = ld["WebPage"]
			}
			if doc == nil || doc["url"] != "https://agentchi.dev"+path || doc["headline"] == "" || doc["dateModified"] != g.updated ||
				strings.ContainsAny(doc["headline"].(string)+doc["description"].(string), "<>&") {
				t.Errorf("%s : page absente ou mal décrite dans les données structurées : %v", path, doc)
			}
			if ld["BreadcrumbList"] == nil {
				t.Errorf("%s : fil d'Ariane absent des données structurées", path)
			}
		}
	}
	_, page := getHome(t, public.URL+"/claude-code-notifications")
	if !strings.Contains(page, "--config server_url=https://agentchi.dev --config code=XXXX-XXXX-XXXX") {
		t.Errorf("la commande du plugin ne contient pas l'adresse du serveur")
	}
	if d := dateLabel("fr", "2026-10-21"); d != "21 octobre 2026" {
		t.Errorf("date française : %q", d)
	}
	for _, path := range []string{"/fr/x", "/claude-code-notifications/", "/fr/privacy/"} {
		if resp, _ := getHome(t, public.URL+path); resp.StatusCode != http.StatusNotFound {
			t.Errorf("%s : statut %d", path, resp.StatusCode)
		}
	}
}

// Chaque lien interne des pages (accueil et guides) mène à une page, et chaque fichier statique cité existe.
func TestLiensInternes(t *testing.T) {
	env := newTestEnv(t, filepath.Join(t.TempDir(), "state.json"))
	pages := []string{"/", "/fr/"}
	for _, g := range guides {
		for _, lang := range langs {
			pages = append(pages, pagePath(lang, g.slug))
		}
	}
	for _, path := range pages {
		_, page := getHome(t, env.url+path)
		for _, m := range regexp.MustCompile(`(?:href|src)="(/[^"#?]*)`).FindAllStringSubmatch(page, -1) {
			link := m[1]
			if strings.HasPrefix(link, "/static/") {
				if _, err := webFiles.ReadFile("web" + link); err != nil {
					t.Errorf("%s cite %s, absent de web/static", path, link)
				}
				continue
			}
			if resp, _ := getHome(t, env.url+link); resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusFound {
				t.Errorf("%s : lien %s, statut %d", path, link, resp.StatusCode)
			}
		}
	}
}

// La politique de confidentialité du site reprend docs/PRIVACY*.md : même date de mise à jour.
func TestConfidentialiteSynchronisee(t *testing.T) {
	g := findGuide("privacy")
	for lang, file := range map[string]string{"en": "PRIVACY.md", "fr": "PRIVACY.fr.md"} {
		doc, err := os.ReadFile(filepath.Join("..", "docs", file))
		if os.IsNotExist(err) {
			t.Skip("docs absent")
		}
		if err != nil {
			t.Fatal(err)
		}
		if date := dateLabel(lang, g.updated); !strings.Contains(string(doc), date) {
			t.Errorf("docs/%s n'est pas daté du %s : mets à jour web/pages/privacy.%s.html et la date du guide", file, date, lang)
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
		"/static/img/en/og.jpg":                   "image/jpeg",
		"/static/img/fr/03-jouer.webp":            "image/webp",
		"/static/page.js":                         "text/javascript",
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
