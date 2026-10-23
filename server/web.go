package main

import (
	"bytes"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"encoding/json"
	"encoding/xml"
	"html/template"
	"io"
	"io/fs"
	"log"
	"net/http"
	"strconv"
	"strings"
)

// Page d'accueil et ses fichiers (polices, mascotte, captures, vidéo), embarqués dans le binaire.
// Les scripts de web/static/js sont des copies de Stores/source (voir TestScriptsMascotteSynchronises).
//
//go:embed web
var webFiles embed.FS

// Page d'accueil : en anglais sur /, en français sur /fr/. Captures, vidéo et image de partage suivent la langue
// (web/static/img/<langue>, web/static/video/<langue>) ; web/seo.html porte les balises communes (canonique, hreflang…).
var homeTemplates = map[string]*template.Template{
	"en": template.Must(template.ParseFS(webFiles, "web/home.en.html", "web/seo.html")),
	"fr": template.Must(template.ParseFS(webFiles, "web/home.fr.html", "web/seo.html")),
}

const (
	defaultLang = "en"
	langCookie  = "agentchi_lang"
)

// homePath : adresse de la page d'accueil dans une langue.
func homePath(lang string) string {
	if lang == defaultLang {
		return "/"
	}
	return "/" + lang + "/"
}

// preferredLang : langue retenue par le lien FR/EN (cookie), sinon celle du navigateur, sinon l'anglais.
// Les robots n'envoient ni l'un ni l'autre : ils voient toujours chaque page dans sa propre langue.
func preferredLang(r *http.Request) string {
	if c, err := r.Cookie(langCookie); err == nil && homeTemplates[c.Value] != nil {
		return c.Value
	}
	if l := acceptLanguage(r.Header.Get("Accept-Language")); l != "" {
		return l
	}
	return defaultLang
}

// acceptLanguage renvoie la langue disponible la mieux notée de l'en-tête Accept-Language (« fr-FR,fr;q=0.9,en;q=0.8 »), ou "".
func acceptLanguage(header string) string {
	best, bestQ := "", 0.0
	for _, part := range strings.Split(header, ",") {
		tag, params, _ := strings.Cut(strings.TrimSpace(part), ";")
		q := 1.0
		if v, ok := strings.CutPrefix(strings.TrimSpace(params), "q="); ok {
			if f, err := strconv.ParseFloat(v, 64); err == nil {
				q = f
			}
		}
		base, _, _ := strings.Cut(strings.ToLower(strings.TrimSpace(tag)), "-")
		if homeTemplates[base] != nil && q > bestQ {
			best, bestQ = base, q
		}
	}
	return best
}

// assetVersion change à chaque modification de web/static : les URL versionnées contournent le cache.
var assetVersion = func() string {
	h := sha256.New()
	_ = fs.WalkDir(webFiles, "web/static", func(p string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		b, err := webFiles.ReadFile(p)
		h.Write([]byte(p))
		h.Write(b)
		return err
	})
	return hex.EncodeToString(h.Sum(nil))[:10]
}()

// handleHome sert / (anglais). ?lang= retient le choix du lien FR/EN dans un cookie et redirige vers la page de
// cette langue ; un visiteur qui préfère le français (cookie ou navigateur) est redirigé vers /fr/.
func (s *Server) handleHome(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Vary", "Accept-Language, Cookie")
	if q := r.URL.Query(); q.Has("lang") {
		l := q.Get("lang")
		if homeTemplates[l] != nil {
			http.SetCookie(w, &http.Cookie{Name: langCookie, Value: l, Path: "/", MaxAge: 365 * 24 * 3600, SameSite: http.SameSiteLaxMode})
		} else {
			l = preferredLang(r)
		}
		http.Redirect(w, r, homePath(l), http.StatusFound)
		return
	}
	if l := preferredLang(r); l != defaultLang {
		http.Redirect(w, r, homePath(l), http.StatusFound)
		return
	}
	s.renderHome(w, r, defaultLang)
}

func (s *Server) handleHomeFR(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/fr/" {
		http.NotFound(w, r)
		return
	}
	s.renderHome(w, r, "fr")
}

// homePage : données des gabarits web/home.<langue>.html et web/seo.html.
type homePage struct {
	Lang      string
	URL       string // adresse publique du serveur, sans / final
	Host      string
	Cloud     string // bloc de .claude/settings.json pour les sessions cloud
	V         string // version des fichiers statiques
	Canonical string
	Indexable bool
	JSONLD    template.JS
}

func (s *Server) renderHome(w http.ResponseWriter, r *http.Request, lang string) {
	url := s.publicURL(r)
	pretty, _ := json.MarshalIndent(CloudSettings(url), "", "  ")
	page := homePage{
		Lang:      lang,
		URL:       url,
		Host:      strings.TrimPrefix(strings.TrimPrefix(url, "https://"), "http://"),
		Cloud:     string(pretty),
		V:         assetVersion,
		Canonical: url + homePath(lang),
		Indexable: s.cfg.Indexable,
		JSONLD:    structuredData(url, lang),
	}
	var buf bytes.Buffer
	if err := homeTemplates[lang].Execute(&buf, page); err != nil {
		log.Printf("page d'accueil : %v", err)
		httpError(w, http.StatusInternalServerError, "page unavailable")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Content-Language", lang)
	if !s.cfg.Indexable {
		w.Header().Set("X-Robots-Tag", "noindex")
	}
	_, _ = buf.WriteTo(w)
}

// structuredData : données structurées schema.org (appli et vidéo de présentation) pour les moteurs de recherche.
func structuredData(url, lang string) template.JS {
	t := func(fr, en string) string { return text(lang, fr, en) }
	page := url + homePath(lang)
	data := map[string]any{
		"@context": "https://schema.org",
		"@graph": []map[string]any{
			{
				"@type":               "SoftwareApplication",
				"@id":                 url + "/#app",
				"name":                "Agentchi",
				"url":                 page,
				"inLanguage":          lang,
				"applicationCategory": "DeveloperApplication",
				"operatingSystem":     "Wear OS",
				"description": t(
					"Les demandes de Claude Code et de Codex arrivent sur ta montre Wear OS, tu y réponds d'un geste, et une mascotte pixel-art vit au rythme de tes sessions.",
					"Claude Code and Codex requests land on your Wear OS watch, you answer with a tap, and a pixel-art mascot lives in sync with your sessions.",
				),
				"image":               url + "/static/img/" + lang + "/og.png",
				"screenshot":          url + "/static/img/" + lang + "/01-mascotte.png",
				"isAccessibleForFree": true,
				"offers":              map[string]any{"@type": "Offer", "price": "0", "priceCurrency": "USD"},
				"author":              map[string]any{"@type": "Organization", "name": "Pelagoss", "url": "https://github.com/Pelagoss"},
				"sameAs":              []string{"https://github.com/Pelagoss/agentchi"},
			},
			{
				"@type":        "VideoObject",
				"name":         t("Agentchi : Chi prend vie", "Agentchi: watch Chi come to life"),
				"description":  t("La mascotte pixel-art d'Agentchi sur une montre Wear OS : soins, fête d'une tâche terminée, personnalisation.", "Agentchi's pixel-art mascot on a Wear OS watch: care, a finished task celebration, customization."),
				"inLanguage":   lang,
				"thumbnailUrl": url + "/static/video/" + lang + "/poster.jpg",
				"contentUrl":   url + "/static/video/" + lang + "/agentchi-companion.mp4",
				"uploadDate":   "2026-10-22",
				"duration":     "PT43S",
			},
		},
	}
	b, _ := json.Marshal(data)
	return template.JS(b)
}

// handleRobots : tout est explorable sauf l'API ; le plan du site n'est annoncé que par un serveur indexable.
func (s *Server) handleRobots(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	body := "User-agent: *\nDisallow: /v1/\nDisallow: /*?lang=\nAllow: /\n"
	if s.cfg.Indexable {
		body += "\nSitemap: " + s.publicURL(r) + "/sitemap.xml\n"
	}
	_, _ = io.WriteString(w, body)
}

// handleSitemap : les deux pages d'accueil, chacune avec ses versions dans les autres langues (hreflang).
func (s *Server) handleSitemap(w http.ResponseWriter, r *http.Request) {
	if !s.cfg.Indexable {
		http.NotFound(w, r)
		return
	}
	url := s.publicURL(r)
	var b strings.Builder
	b.WriteString(xml.Header + `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">` + "\n")
	for _, lang := range []string{"en", "fr"} {
		b.WriteString("  <url>\n    <loc>" + url + homePath(lang) + "</loc>\n")
		for _, alt := range []string{"en", "fr"} {
			b.WriteString(`    <xhtml:link rel="alternate" hreflang="` + alt + `" href="` + url + homePath(alt) + `"/>` + "\n")
		}
		b.WriteString(`    <xhtml:link rel="alternate" hreflang="x-default" href="` + url + `/"/>` + "\n  </url>\n")
	}
	b.WriteString("</urlset>\n")
	w.Header().Set("Content-Type", "application/xml; charset=utf-8")
	_, _ = io.WriteString(w, b.String())
}

// staticHandler sert web/static sous /static/, avec un cache d'un jour (les fichiers ne changent qu'au déploiement).
func staticHandler() http.Handler {
	sub, err := fs.Sub(webFiles, "web/static")
	if err != nil {
		panic(err)
	}
	files := http.StripPrefix("/static/", http.FileServerFS(sub))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/") {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Cache-Control", "public, max-age=86400")
		files.ServeHTTP(w, r)
	})
}
