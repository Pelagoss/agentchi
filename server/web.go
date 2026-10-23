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
	"strings"
)

// Page d'accueil et ses fichiers (polices, mascotte, captures, vidéo), embarqués dans le binaire.
// Les scripts de web/static/js sont des copies de Stores/source (voir TestScriptsMascotteSynchronises).
//
//go:embed web
var webFiles embed.FS

var homeTemplate = template.Must(template.ParseFS(webFiles, "web/home.html"))

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
	var buf bytes.Buffer
	err := homeTemplate.Execute(&buf, map[string]string{
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
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
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
