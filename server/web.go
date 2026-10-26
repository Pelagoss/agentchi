package main

import (
	"bytes"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"encoding/json"
	"encoding/xml"
	"html"
	"html/template"
	"io"
	"io/fs"
	"log"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// Pages du site et leurs fichiers (polices, mascotte, captures, vidéo), embarqués dans le binaire.
// Les scripts de web/static/js sont des copies de Stores/source (voir TestScriptsMascotteSynchronises).
//
//go:embed web
var webFiles embed.FS

// langs : langues du site, l'anglais d'abord (langue par défaut et x-default).
var langs = []string{"en", "fr"}

// parsePage lit les gabarits d'une page avec les balises communes (web/seo.html) et le cadre de sa langue
// (web/layout.<langue>.html : en-tête et pied des guides, pied de l'accueil). Le premier fichier est la page servie.
func parsePage(lang string, files ...string) *template.Template {
	return template.Must(template.ParseFS(webFiles, append(files, "web/seo.html", "web/layout."+lang+".html")...))
}

// Page d'accueil : en anglais sur /, en français sur /fr/. Captures, vidéo et image de partage suivent la langue
// (web/static/img/<langue>, web/static/video/<langue>).
var homeTemplates = map[string]*template.Template{
	"en": parsePage("en", "web/home.en.html"),
	"fr": parsePage("fr", "web/home.fr.html"),
}

// homeUpdated : dernière modification du contenu de la page d'accueil (sitemap).
const homeUpdated = "2026-10-26"

// playStoreURL : fiche Google Play de l'appli, à renseigner à sa publication (données structurées).
const playStoreURL = ""

// guide : page de contenu, en anglais sur /<slug>, en français sur /fr/<slug>. Son texte est dans
// web/pages/<slug>.<langue>.html (gabarits title, description et content), mis en page par web/guide.html.
type guide struct {
	slug    string
	updated string // AAAA-MM-JJ : affichée sur la page, dans le sitemap et les données structurées
	article bool   // article technique (TechArticle) plutôt que simple page (WebPage)
	tmpl    map[string]*template.Template
}

var guides = func() []*guide {
	gs := []*guide{
		{slug: "claude-code-notifications", updated: "2026-10-26", article: true},
		{slug: "codex-notifications", updated: "2026-10-26", article: true},
		{slug: "alternatives", updated: "2026-10-26", article: true},
		{slug: "self-hosted", updated: "2026-10-26", article: true},
		{slug: "privacy", updated: "2026-10-21"},
	}
	for _, g := range gs {
		g.tmpl = map[string]*template.Template{}
		for _, l := range langs {
			g.tmpl[l] = parsePage(l, "web/guide.html", "web/pages/"+g.slug+"."+l+".html")
		}
	}
	return gs
}()

func findGuide(slug string) *guide {
	for _, g := range guides {
		if g.slug == slug {
			return g
		}
	}
	return nil
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

// pagePath : adresse d'un guide dans une langue ("" : la page d'accueil).
func pagePath(lang, slug string) string {
	return homePath(lang) + slug
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

// handleFR sert la page d'accueil française (/fr/) et les guides en français (/fr/<slug>).
func (s *Server) handleFR(w http.ResponseWriter, r *http.Request) {
	slug := strings.TrimPrefix(r.URL.Path, "/fr/")
	if slug == "" {
		s.renderHome(w, r, "fr")
		return
	}
	if g := findGuide(slug); g != nil {
		s.renderGuide(w, r, g, "fr")
		return
	}
	http.NotFound(w, r)
}

// handleGuide sert un guide en anglais (/<slug>).
func (s *Server) handleGuide(g *guide) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) { s.renderGuide(w, r, g, defaultLang) }
}

// page : données des gabarits de page (accueil et guides), de web/seo.html et de web/layout.<langue>.html.
type page struct {
	Lang      string
	URL       string // adresse publique du serveur, sans / final
	Host      string
	V         string // version des fichiers statiques
	Home      string // page d'accueil dans la langue de la page
	Canonical string
	Alt       map[string]string // la même page dans chaque langue (hreflang)
	Indexable bool
	JSONLD    template.JS

	Cloud   string // accueil : bloc de .claude/settings.json pour les sessions cloud
	FAQ     []faqItem
	Updated string // guide : date de mise à jour, écrite dans la langue de la page
}

func (s *Server) newPage(r *http.Request, lang, slug string) *page {
	url := s.publicURL(r)
	alt := map[string]string{}
	for _, l := range langs {
		alt[l] = url + pagePath(l, slug)
	}
	return &page{
		Lang:      lang,
		URL:       url,
		Host:      strings.TrimPrefix(strings.TrimPrefix(url, "https://"), "http://"),
		V:         assetVersion,
		Home:      homePath(lang),
		Canonical: alt[lang],
		Alt:       alt,
		Indexable: s.cfg.Indexable,
	}
}

func (s *Server) renderHome(w http.ResponseWriter, r *http.Request, lang string) {
	p := s.newPage(r, lang, "")
	pretty, _ := json.MarshalIndent(CloudSettings(p.URL), "", "  ")
	p.Cloud = string(pretty)
	p.FAQ = homeFAQ(lang)
	p.JSONLD = homeData(p.URL, lang, p.FAQ)
	s.render(w, homeTemplates[lang], p)
}

func (s *Server) renderGuide(w http.ResponseWriter, r *http.Request, g *guide, lang string) {
	p := s.newPage(r, lang, g.slug)
	p.Updated = dateLabel(lang, g.updated)
	t := g.tmpl[lang]
	p.JSONLD = guideData(p.URL, lang, g, plainTemplate(t, "title", p), plainTemplate(t, "description", p))
	s.render(w, t, p)
}

func (s *Server) render(w http.ResponseWriter, t *template.Template, p *page) {
	var buf bytes.Buffer
	if err := t.Execute(&buf, p); err != nil {
		log.Printf("page %s : %v", p.Canonical, err)
		httpError(w, http.StatusInternalServerError, "page unavailable")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Content-Language", p.Lang)
	if !s.cfg.Indexable {
		w.Header().Set("X-Robots-Tag", "noindex")
	}
	_, _ = buf.WriteTo(w)
}

var tags = regexp.MustCompile(`<[^>]*>`)

// plainText : texte brut d'un fragment HTML, pour les données structurées.
func plainText(s string) string {
	return strings.Join(strings.Fields(html.UnescapeString(tags.ReplaceAllString(s, ""))), " ")
}

// plainTemplate : texte brut d'un gabarit nommé (titre, description d'un guide).
func plainTemplate(t *template.Template, name string, data any) string {
	var b strings.Builder
	if err := t.ExecuteTemplate(&b, name, data); err != nil {
		log.Printf("gabarit %s : %v", name, err)
	}
	return plainText(b.String())
}

var monthsFR = []string{"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"}

// dateLabel écrit une date AAAA-MM-JJ dans la langue de la page : « 26 octobre 2026 », « October 26, 2026 ».
func dateLabel(lang, iso string) string {
	d, err := time.Parse(time.DateOnly, iso)
	if err != nil {
		return iso
	}
	if lang == "fr" {
		return strconv.Itoa(d.Day()) + " " + monthsFR[d.Month()-1] + " " + strconv.Itoa(d.Year())
	}
	return d.Format("January 2, 2006")
}

// faqItem : une question de la FAQ de l'accueil, affichée sur la page et reprise dans les données structurées.
type faqItem struct {
	Q string
	A template.HTML
}

// nbspFR : espaces insécables de la typographie française, comme dans les pages (« &nbsp;: »).
var nbspFR = strings.NewReplacer(" :", "\u00a0:", " ?", "\u00a0?", " !", "\u00a0!", "« ", "«\u00a0", " »", "\u00a0»")

func homeFAQ(lang string) []faqItem {
	t := func(fr, en string) string { return text(lang, fr, en) }
	link := func(slug, label string) string { return `<a href="` + pagePath(lang, slug) + `">` + label + `</a>` }
	items := []struct{ q, a string }{
		{
			t("Agentchi marche-t-il sur ma Galaxy Watch ?", "Does Agentchi work on my Galaxy Watch?"),
			t("Oui, sur toutes les montres Wear OS 3 ou plus récent qui ont les services Google Play : Galaxy Watch 4 et plus récentes, Pixel Watch, et la plupart des montres Wear OS actuelles.",
				"Yes, on every watch running Wear OS 3 or later with Google Play services: Galaxy Watch 4 and newer, Pixel Watch, and most current Wear OS watches."),
		},
		{
			t("Faut-il installer une appli sur le téléphone ?", "Do I need an app on my phone?"),
			t("Non. La montre reçoit les notifications directement, en Wi-Fi, en LTE ou via le téléphone auquel elle est appairée. Aucun compte à créer.",
				"No. The watch receives notifications directly, over Wi-Fi, LTE or through the phone it's paired with. No account to create."),
		},
		{
			t("Peut-on accepter les demandes d'autorisation de Claude Code depuis la montre ?", "Can I approve Claude Code permission requests from the watch?"),
			t("Oui, avec Répondre depuis la montre activé : tu autorises, refuses avec ou sans raison, ou choisis « toujours ». Tu réponds aussi aux questions à choix multiples, approuves un plan, et peux écrire à l'agent quand il a fini. Détails : "+link("claude-code-notifications", "notifications Claude Code")+".",
				"Yes, with Reply from the watch turned on: allow, deny with or without a reason, or pick “always”. You can also answer multiple-choice questions, approve a plan, and write back to the agent when it's done. Details: "+link("claude-code-notifications", "Claude Code notifications")+"."),
		},
		{
			t("Est-ce que ça marche avec Codex ?", "Does it work with Codex?"),
			t("Oui, avec le CLI, l'appli et l'extension IDE de Codex : la montre reçoit les fins de tâche et les demandes d'autorisation, et peut y répondre. Les questions à choix multiples restent sur l'ordinateur, Codex ne les passe pas aux hooks. Voir "+link("codex-notifications", "notifications Codex")+".",
				"Yes, with the Codex CLI, app and IDE extension: the watch receives task completions and permission requests, and can answer them. Multiple-choice questions stay on the computer, as Codex doesn't pass them to hooks. See "+link("codex-notifications", "Codex notifications")+"."),
		},
		{
			t("Et les sessions cloud de claude.ai/code ?", "What about claude.ai/code cloud sessions?"),
			t("Elles ne chargent pas les plugins : un hook http dans le .claude/settings.json du dépôt les relie à ta montre, avec ton code dans une variable d'environnement de l'environnement cloud.",
				"They don't load plugins: an http hook in the repo's .claude/settings.json connects them to your watch, with your code in an environment variable of the cloud environment."),
		},
		{
			t("Quelles données passent par le serveur ?", "What data goes through the server?"),
			t("Le nom du dossier, un identifiant court de session, le début du dernier message de l'agent et, pour répondre, la question posée. Le serveur ne les stocke pas : il les relaie aussitôt à ta montre. Voir la "+link("privacy", "politique de confidentialité")+".",
				"The folder name, a short session ID, the beginning of the agent's last message and, to answer, the question being asked. The server doesn't store them: it relays them right away to your watch. See the "+link("privacy", "privacy policy")+"."),
		},
		{
			t("Est-ce gratuit ?", "Is it free?"),
			t("Oui : gratuit, sans compte, sans publicité ni traceur, et open source.",
				"Yes: free, no account, no ads or trackers, and open source."),
		},
		{
			t("Est-ce une appli officielle d'Anthropic ou d'OpenAI ?", "Is this an official Anthropic or OpenAI app?"),
			t("Non. Agentchi est une appli indépendante qui passe par les hooks de Claude Code et de Codex.",
				"No. Agentchi is an independent app that works through Claude Code and Codex hooks."),
		},
		{
			t("Puis-je héberger mon propre serveur ?", "Can I host my own server?"),
			t("Oui : le serveur est un petit binaire Go open source, avec Docker Compose ou sans. Voir "+link("self-hosted", "héberger ton serveur")+".",
				"Yes: the server is a small open-source Go binary, with Docker Compose or without. See "+link("self-hosted", "hosting your own server")+"."),
		},
	}
	faq := make([]faqItem, len(items))
	for i, it := range items {
		if lang == "fr" {
			it.q, it.a = nbspFR.Replace(it.q), nbspFR.Replace(it.a)
		}
		faq[i] = faqItem{Q: it.q, A: template.HTML(it.a)}
	}
	return faq
}

// siteNodes : le site et son éditeur, communs aux données structurées de toutes les pages.
func siteNodes(url string) []map[string]any {
	return []map[string]any{
		{
			"@type":      "WebSite",
			"@id":        url + "/#website",
			"url":        url + "/",
			"name":       "Agentchi",
			"inLanguage": langs,
			"publisher":  map[string]any{"@id": url + "/#publisher"},
		},
		{
			"@type": "Organization",
			"@id":   url + "/#publisher",
			"name":  "Pelagoss",
			"url":   "https://github.com/Pelagoss",
			"logo":  url + "/static/img/icon-512.png",
		},
	}
}

// homeData : données structurées schema.org de l'accueil (site, appli, vidéo de présentation, FAQ).
func homeData(url, lang string, faq []faqItem) template.JS {
	t := func(fr, en string) string { return text(lang, fr, en) }
	page := url + homePath(lang)
	var screenshots []string
	for _, name := range []string{"01-mascotte", "03-jouer", "04-dodo", "05-personnalisation"} {
		screenshots = append(screenshots, url+"/static/img/"+lang+"/"+name+".webp")
	}
	app := map[string]any{
		"@type":                "SoftwareApplication",
		"@id":                  url + "/#app",
		"name":                 "Agentchi",
		"url":                  page,
		"inLanguage":           lang,
		"applicationCategory":  "DeveloperApplication",
		"operatingSystem":      "Wear OS",
		"softwareRequirements": t("Wear OS 3 ou plus récent, avec les services Google Play", "Wear OS 3 or later, with Google Play services"),
		"description": t(
			"Les demandes de Claude Code et de Codex arrivent sur ta montre Wear OS, tu y réponds d'un geste, et une mascotte pixel-art vit au rythme de tes sessions.",
			"Claude Code and Codex requests land on your Wear OS watch, you answer with a tap, and a pixel-art mascot lives in sync with your sessions.",
		),
		"featureList": []string{
			t("Notifications Claude Code et Codex sur la montre", "Claude Code and Codex notifications on the watch"),
			t("Autoriser ou refuser les demandes d'autorisation depuis la montre", "Approve or deny permission requests from the watch"),
			t("Répondre aux questions et approuver les plans", "Answer questions and approve plans"),
			t("Sessions en cours d'un coup d'œil", "Active sessions at a glance"),
			t("Mascotte pixel-art à nourrir et personnaliser", "Pixel-art mascot to feed and customize"),
			t("Heures calmes et résumé des événements non urgents", "Quiet hours and a summary of non-urgent events"),
			t("Tuile, complication et cadran", "Tile, complication and watch face"),
			t("Serveur relais open source, auto-hébergeable", "Open-source, self-hostable relay server"),
		},
		"image":               url + "/static/img/" + lang + "/og.jpg",
		"screenshot":          screenshots,
		"isAccessibleForFree": true,
		"offers":              map[string]any{"@type": "Offer", "price": "0", "priceCurrency": "USD"},
		"author":              map[string]any{"@id": url + "/#publisher"},
		"publisher":           map[string]any{"@id": url + "/#publisher"},
		"sameAs":              []string{"https://github.com/Pelagoss/agentchi"},
	}
	if playStoreURL != "" {
		app["installUrl"] = playStoreURL
		app["downloadUrl"] = playStoreURL
		app["sameAs"] = append(app["sameAs"].([]string), playStoreURL)
	}
	questions := make([]map[string]any, len(faq))
	for i, f := range faq {
		questions[i] = map[string]any{
			"@type":          "Question",
			"name":           f.Q,
			"acceptedAnswer": map[string]any{"@type": "Answer", "text": plainText(string(f.A))},
		}
	}
	graph := append(siteNodes(url),
		app,
		map[string]any{
			"@type":        "VideoObject",
			"name":         t("Agentchi : Chi prend vie", "Agentchi: watch Chi come to life"),
			"description":  t("La mascotte pixel-art d'Agentchi sur une montre Wear OS : soins, fête d'une tâche terminée, personnalisation.", "Agentchi's pixel-art mascot on a Wear OS watch: care, a finished task celebration, customization."),
			"inLanguage":   lang,
			"thumbnailUrl": url + "/static/video/" + lang + "/poster.jpg",
			"contentUrl":   url + "/static/video/" + lang + "/agentchi-companion.mp4",
			"uploadDate":   "2026-10-22",
			"duration":     "PT43S",
		},
		map[string]any{
			"@type":      "FAQPage",
			"@id":        page + "#faq",
			"url":        page,
			"inLanguage": lang,
			"mainEntity": questions,
		},
	)
	return jsonLD(graph)
}

// guideData : données structurées d'un guide (article ou page, et son fil d'Ariane).
func guideData(url, lang string, g *guide, title, description string) template.JS {
	page := url + pagePath(lang, g.slug)
	typ := "WebPage"
	if g.article {
		typ = "TechArticle"
	}
	doc := map[string]any{
		"@type":            typ,
		"@id":              page + "#page",
		"url":              page,
		"name":             title,
		"headline":         title,
		"description":      description,
		"inLanguage":       lang,
		"dateModified":     g.updated,
		"image":            url + "/static/img/" + lang + "/og.jpg",
		"isPartOf":         map[string]any{"@id": url + "/#website"},
		"about":            map[string]any{"@id": url + "/#app"},
		"author":           map[string]any{"@id": url + "/#publisher"},
		"publisher":        map[string]any{"@id": url + "/#publisher"},
		"mainEntityOfPage": page,
	}
	crumbs := map[string]any{
		"@type": "BreadcrumbList",
		"itemListElement": []map[string]any{
			{"@type": "ListItem", "position": 1, "name": "Agentchi", "item": url + homePath(lang)},
			{"@type": "ListItem", "position": 2, "name": title, "item": page},
		},
	}
	return jsonLD(append(siteNodes(url), doc, crumbs))
}

func jsonLD(graph []map[string]any) template.JS {
	b, _ := json.Marshal(map[string]any{"@context": "https://schema.org", "@graph": graph})
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

// handleSitemap : l'accueil et les guides dans chaque langue, avec leurs autres versions (hreflang) et leur date.
func (s *Server) handleSitemap(w http.ResponseWriter, r *http.Request) {
	if !s.cfg.Indexable {
		http.NotFound(w, r)
		return
	}
	url := s.publicURL(r)
	pages := []struct{ slug, updated string }{{"", homeUpdated}}
	for _, g := range guides {
		pages = append(pages, struct{ slug, updated string }{g.slug, g.updated})
	}
	var b strings.Builder
	b.WriteString(xml.Header + `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">` + "\n")
	for _, p := range pages {
		for _, lang := range langs {
			b.WriteString("  <url>\n    <loc>" + url + pagePath(lang, p.slug) + "</loc>\n    <lastmod>" + p.updated + "</lastmod>\n")
			for _, alt := range langs {
				b.WriteString(`    <xhtml:link rel="alternate" hreflang="` + alt + `" href="` + url + pagePath(alt, p.slug) + `"/>` + "\n")
			}
			b.WriteString(`    <xhtml:link rel="alternate" hreflang="x-default" href="` + url + pagePath(defaultLang, p.slug) + `"/>` + "\n  </url>\n")
		}
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
