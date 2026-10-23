package main

import (
	"bytes"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"encoding/json"
	"html/template"
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

// Page d'accueil en anglais par défaut, en français sur demande ; captures, vidéo et image de partage suivent la langue
// (web/static/img/<langue>, web/static/video/<langue>).
var homeTemplates = map[string]*template.Template{
	"en": template.Must(template.ParseFS(webFiles, "web/home.en.html")),
	"fr": template.Must(template.ParseFS(webFiles, "web/home.fr.html")),
}

const (
	defaultLang = "en"
	langCookie  = "agentchi_lang"
)

// homeLang choisit la langue de la page : ?lang=, puis le cookie posé par ce choix, puis Accept-Language, sinon l'anglais.
// chosen indique un choix explicite (?lang=), à retenir dans le cookie.
func homeLang(r *http.Request) (lang string, chosen bool) {
	if l := r.URL.Query().Get("lang"); homeTemplates[l] != nil {
		return l, true
	}
	if c, err := r.Cookie(langCookie); err == nil && homeTemplates[c.Value] != nil {
		return c.Value, false
	}
	if l := acceptLanguage(r.Header.Get("Accept-Language")); l != "" {
		return l, false
	}
	return defaultLang, false
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

func (s *Server) handleHome(w http.ResponseWriter, r *http.Request) {
	url := s.publicURL(r)
	pretty, _ := json.MarshalIndent(CloudSettings(url), "", "  ")
	lang, chosen := homeLang(r)
	var buf bytes.Buffer
	err := homeTemplates[lang].Execute(&buf, map[string]string{
		"URL":   url,
		"Host":  strings.TrimPrefix(strings.TrimPrefix(url, "https://"), "http://"),
		"Cloud": string(pretty),
		"V":     assetVersion,
	})
	if err != nil {
		log.Printf("page d'accueil : %v", err)
		httpError(w, http.StatusInternalServerError, "page indisponible")
		return
	}
	if chosen {
		http.SetCookie(w, &http.Cookie{Name: langCookie, Value: lang, Path: "/", MaxAge: 365 * 24 * 3600, SameSite: http.SameSiteLaxMode})
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Content-Language", lang)
	w.Header().Set("Vary", "Accept-Language, Cookie")
	_, _ = buf.WriteTo(w)
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
