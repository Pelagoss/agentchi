package main

import (
	"encoding/json"
	"errors"
	"html/template"
	"io"
	"log"
	"net"
	"net/http"
	"path"
	"strings"
	"time"
	"unicode/utf8"
)

type Config struct {
	// URL publique du serveur, affichée dans la page d'accueil et les extraits de configuration.
	PublicURL string
	// Faire confiance à X-Forwarded-For / X-Forwarded-Proto (derrière un reverse proxy).
	TrustProxy bool
	MaxDevices int
}

type Server struct {
	cfg   Config
	store *Store
	mux   *http.ServeMux

	registerLimit *limiter // création de montres, par IP
	hookLimit     *limiter // événements, par code
	authFailLimit *limiter // jetons ou codes invalides, par IP
	apiLimit      *limiter // API de la montre, par IP
}

const keepAliveInterval = 45 * time.Second

func NewServer(cfg Config, store *Store) *Server {
	s := &Server{
		cfg:           cfg,
		store:         store,
		mux:           http.NewServeMux(),
		registerLimit: newLimiter(2, 5),
		hookLimit:     newLimiter(120, 60),
		authFailLimit: newLimiter(10, 20),
		apiLimit:      newLimiter(120, 60),
	}
	s.mux.HandleFunc("GET /{$}", s.handleHome)
	s.mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { io.WriteString(w, "ok\n") })
	s.mux.HandleFunc("GET /v1/cloud-settings.json", s.handleCloudSettings)

	// Hook Claude Code : le code d'appairage sert de jeton.
	s.mux.HandleFunc("POST /h", s.handleHook)

	// API de la montre
	s.mux.HandleFunc("POST /v1/devices", s.handleRegister)
	s.mux.HandleFunc("GET /v1/device", s.device(s.handleGetDevice))
	s.mux.HandleFunc("DELETE /v1/device", s.device(s.handleDeleteDevice))
	s.mux.HandleFunc("POST /v1/codes", s.device(s.handleCreateCode))
	s.mux.HandleFunc("GET /v1/codes", s.device(s.handleListCodes))
	s.mux.HandleFunc("DELETE /v1/codes/{id}", s.device(s.handleDeleteCode))
	s.mux.HandleFunc("POST /v1/test", s.device(s.handleTest))
	// Abonnement au format ntfy (https://docs.ntfy.sh/subscribe/api/) : la montre réutilise son client.
	s.mux.HandleFunc("GET /{device}/json", s.device(s.handleSubscribe))
	return s
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// « https://serveur/ » + « /h » donne « //h » : on nettoie sans rediriger,
	// car curl ne suit pas une redirection de POST.
	r.URL.Path = path.Clean("/" + r.URL.Path)
	w.Header().Set("X-Content-Type-Options", "nosniff")
	s.mux.ServeHTTP(w, r)
}

// --- Hook ---

func (s *Server) handleHook(w http.ResponseWriter, r *http.Request) {
	code := bearer(r)
	if code == "" {
		// Hook versionné dans un dépôt, sans code pour cette personne : rien à faire, sans erreur.
		w.WriteHeader(http.StatusNoContent)
		return
	}
	ip := s.clientIP(r)
	var in HookInput
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&in); err != nil {
		httpError(w, http.StatusBadRequest, "JSON invalide")
		return
	}
	project := ""
	if in.Cwd != "" {
		project = path.Base(strings.ReplaceAll(strings.TrimRight(in.Cwd, `/\`), `\`, "/"))
	}
	c, ok := s.store.UseCode(code, project)
	if !ok {
		s.authFailed(w, ip, "code d'appairage inconnu ou révoqué")
		return
	}
	if !s.hookLimit.Allow(c.ID) {
		httpError(w, http.StatusTooManyRequests, "trop d'événements")
		return
	}
	payload, show := ToPayload(in, c.Label, r.Header.Get("X-Claude-Remote-Session"))
	if show {
		if _, err := s.store.Publish(c.DeviceID, payload.JSON()); err != nil && !errors.Is(err, errNotFound) {
			log.Printf("publication : %v", err)
		}
	}
	// Corps vide : Claude Code n'en tire aucune décision ni aucun contexte.
	w.WriteHeader(http.StatusNoContent)
}

// --- API de la montre ---

func (s *Server) device(next func(http.ResponseWriter, *http.Request, *Device)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ip := s.clientIP(r)
		if !s.apiLimit.Allow(ip) {
			httpError(w, http.StatusTooManyRequests, "trop de requêtes")
			return
		}
		d, ok := s.store.DeviceByToken(bearer(r))
		if !ok {
			s.authFailed(w, ip, "jeton de montre invalide")
			return
		}
		next(w, r, d)
	}
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	if !s.registerLimit.Allow(s.clientIP(r)) {
		httpError(w, http.StatusTooManyRequests, "trop de montres créées depuis cette adresse")
		return
	}
	var body struct {
		Name string `json:"name"`
	}
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&body)
	if s.cfg.MaxDevices > 0 && s.store.DeviceCount() >= s.cfg.MaxDevices {
		httpError(w, http.StatusForbidden, "ce serveur n'accepte plus de nouvelles montres")
		return
	}
	d, token, err := s.store.CreateDevice(cleanLabel(body.Name, "Montre"))
	if err != nil {
		serverError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, map[string]string{"id": d.ID, "token": token})
}

func (s *Server) handleGetDevice(w http.ResponseWriter, _ *http.Request, d *Device) {
	writeJSON(w, http.StatusOK, map[string]any{"id": d.ID, "name": d.Name, "created": d.Created})
}

func (s *Server) handleDeleteDevice(w http.ResponseWriter, _ *http.Request, d *Device) {
	if err := s.store.DeleteDevice(d.ID); err != nil {
		serverError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

type codeView struct {
	ID          string     `json:"id"`
	Label       string     `json:"label"`
	Created     time.Time  `json:"created"`
	LastUsed    *time.Time `json:"last_used,omitempty"`
	LastProject string     `json:"last_project,omitempty"`
	Code        string     `json:"code,omitempty"` // seulement à la création
}

func viewOf(c Code) codeView {
	return codeView{ID: c.ID, Label: c.Label, Created: c.Created, LastUsed: c.LastUsed, LastProject: c.LastProject}
}

func (s *Server) handleCreateCode(w http.ResponseWriter, r *http.Request, d *Device) {
	var body struct {
		Label string `json:"label"`
	}
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&body)
	c, plain, err := s.store.CreateCode(d.ID, cleanLabel(body.Label, "Ordinateur"))
	if errors.Is(err, errTooManyCode) {
		httpError(w, http.StatusConflict, err.Error())
		return
	}
	if err != nil {
		serverError(w, err)
		return
	}
	v := viewOf(*c)
	v.Code = plain
	writeJSON(w, http.StatusCreated, v)
}

func (s *Server) handleListCodes(w http.ResponseWriter, _ *http.Request, d *Device) {
	codes := s.store.Codes(d.ID)
	out := make([]codeView, 0, len(codes))
	for _, c := range codes {
		out = append(out, viewOf(c))
	}
	writeJSON(w, http.StatusOK, out)
}

func (s *Server) handleDeleteCode(w http.ResponseWriter, r *http.Request, d *Device) {
	if err := s.store.DeleteCode(d.ID, r.PathValue("id")); err != nil {
		httpError(w, http.StatusNotFound, "code introuvable")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleTest(w http.ResponseWriter, _ *http.Request, d *Device) {
	p := Payload{Type: "done", Message: "Connexion au serveur réussie 🎉", Account: "Serveur"}
	if _, err := s.store.Publish(d.ID, p.JSON()); err != nil {
		serverError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

type ntfyLine struct {
	ID      string `json:"id"`
	Time    int64  `json:"time"`
	Event   string `json:"event"`
	Topic   string `json:"topic"`
	Message string `json:"message,omitempty"`
}

func (s *Server) handleSubscribe(w http.ResponseWriter, r *http.Request, d *Device) {
	if r.PathValue("device") != d.ID {
		httpError(w, http.StatusForbidden, "cette montre ne peut pas lire ce flux")
		return
	}
	since := r.URL.Query().Get("since")
	w.Header().Set("Content-Type", "application/x-ndjson; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	enc := json.NewEncoder(w)
	write := func(ev Event) error {
		return enc.Encode(ntfyLine{ID: ev.ID, Time: ev.Time, Event: "message", Topic: d.ID, Message: ev.Message})
	}

	if q := r.URL.Query().Get("poll"); q == "1" || q == "true" {
		for _, ev := range s.store.Since(d.ID, since) {
			if write(ev) != nil {
				return
			}
		}
		return
	}

	// Flux : connexion gardée ouverte, comme ntfy (mode instantané de la montre).
	flusher, _ := w.(http.Flusher)
	flush := func() {
		if flusher != nil {
			flusher.Flush()
		}
	}
	backlog, ch, cancel := s.store.Subscribe(d.ID, since)
	defer cancel()
	_ = enc.Encode(ntfyLine{ID: randomToken(12), Time: time.Now().Unix(), Event: "open", Topic: d.ID})
	for _, ev := range backlog {
		if write(ev) != nil {
			return
		}
	}
	flush()
	ticker := time.NewTicker(keepAliveInterval)
	defer ticker.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case ev, ok := <-ch:
			if !ok { // montre supprimée
				return
			}
			if write(ev) != nil {
				return
			}
		case <-ticker.C:
			if enc.Encode(ntfyLine{ID: randomToken(12), Time: time.Now().Unix(), Event: "keepalive", Topic: d.ID}) != nil {
				return
			}
		}
		flush()
	}
}

// --- Pages d'aide ---

func (s *Server) publicURL(r *http.Request) string {
	if s.cfg.PublicURL != "" {
		return strings.TrimRight(s.cfg.PublicURL, "/")
	}
	scheme := "http"
	if r.TLS != nil || (s.cfg.TrustProxy && r.Header.Get("X-Forwarded-Proto") == "https") {
		scheme = "https"
	}
	return scheme + "://" + r.Host
}

// Événements relayés depuis une session cloud. SessionStart n'accepte pas les hooks http.
var cloudEvents = []string{"Notification", "Stop", "StopFailure", "SubagentStop", "SessionEnd", "PreCompact"}

// CloudSettings : bloc à ajouter au .claude/settings.json d'un dépôt pour ses sessions cloud.
func CloudSettings(publicURL string) map[string]any {
	hook := map[string]any{
		"type":    "http",
		"url":     publicURL + "/h",
		"timeout": 5,
		"headers": map[string]string{
			"Authorization":           "Bearer $CLAUDE_COMPANION_CODE",
			"X-Claude-Remote-Session": "$CLAUDE_CODE_REMOTE_SESSION_ID",
		},
		"allowedEnvVars": []string{"CLAUDE_COMPANION_CODE", "CLAUDE_CODE_REMOTE_SESSION_ID"},
	}
	hooks := map[string]any{}
	for _, e := range cloudEvents {
		hooks[e] = []any{map[string]any{"hooks": []any{hook}}}
	}
	return map[string]any{"hooks": hooks}
}

func (s *Server) handleCloudSettings(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	_ = enc.Encode(CloudSettings(s.publicURL(r)))
}

func (s *Server) handleHome(w http.ResponseWriter, r *http.Request) {
	pretty, _ := json.MarshalIndent(CloudSettings(s.publicURL(r)), "", "  ")
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = homeTemplate.Execute(w, map[string]string{"URL": s.publicURL(r), "Cloud": string(pretty)})
}

var homeTemplate = template.Must(template.New("home").Parse(`<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Claude Companion</title>
<style>
:root{color-scheme:light dark;--bg:#faf9f5;--fg:#1a1918;--muted:#5e5d59;--card:#fff;--line:#e5e3dc;--accent:#d97757}
@media (prefers-color-scheme:dark){:root{--bg:#1a1918;--fg:#f5f4ef;--muted:#a8a69e;--card:#262624;--line:#3a3936}}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.55 system-ui,sans-serif}
main{max-width:720px;margin:0 auto;padding:32px 16px}
h1{font-size:1.6rem;margin:0 0 4px}h2{font-size:1.1rem;margin:32px 0 8px}
p,li{color:var(--muted)}code,pre{font-family:ui-monospace,Menlo,monospace;font-size:.88rem}
pre{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:12px;overflow-x:auto;color:var(--fg)}
.k{color:var(--accent);font-weight:600}
</style></head><body><main>
<h1>⌚ Claude Companion</h1>
<p>Ce serveur relaie les hooks Claude Code vers ta montre. Adresse du serveur : <code class="k">{{.URL}}</code></p>

<h2>1. Sur la montre</h2>
<p><b>Paramètres › Ordinateurs et cloud › Nouveau code</b>. Note le code <code>XXXX-XXXX-XXXX</code>. Prévois un code par ordinateur ou par environnement cloud : chacun se révoque séparément.</p>

<h2>2. Sur chaque ordinateur (terminal, IDE, appli Desktop, Remote Control)</h2>
<p>Une seule commande, avec ton code :</p>
<pre>claude plugin install watch-companion --marketplace Pelagoss/claude-companion \
  --config server_url={{.URL}} --config code=XXXX-XXXX-XXXX</pre>
<p>Le code est rangé dans le trousseau du système, pas dans <code>settings.json</code>. Sans <code>--config</code>, Claude Code demande les deux valeurs (<code>/plugin</code> › watch-companion › Configure options). Il faut <code>curl</code>, présent par défaut sur macOS, Linux et Windows 10+.</p>

<h2>3. Sessions cloud (claude.ai/code)</h2>
<p>Les sessions cloud ne chargent pas les plugins. Ajoute ce bloc au <code>.claude/settings.json</code> du dépôt (<a href="/v1/cloud-settings.json">télécharger</a>), puis, dans les paramètres de ton environnement cloud :</p>
<ul>
<li>variable d'environnement <code>CLAUDE_COMPANION_CODE=&lt;ton code&gt;</code> ;</li>
<li>accès réseau personnalisé autorisant <code>{{.URL}}</code>.</li>
</ul>
<p>Chaque personne qui travaille sur le dépôt met son propre code : sans code, le hook ne fait rien.</p>
<pre>{{.Cloud}}</pre>
</main></body></html>
`))

// --- Utilitaires ---

func (s *Server) authFailed(w http.ResponseWriter, ip, msg string) {
	if !s.authFailLimit.Allow(ip) {
		httpError(w, http.StatusTooManyRequests, "trop d'essais")
		return
	}
	httpError(w, http.StatusUnauthorized, msg)
}

func (s *Server) clientIP(r *http.Request) string {
	if s.cfg.TrustProxy {
		if fwd := r.Header.Get("X-Forwarded-For"); fwd != "" {
			parts := strings.Split(fwd, ",")
			return strings.TrimSpace(parts[len(parts)-1])
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func bearer(r *http.Request) string {
	h := strings.TrimSpace(r.Header.Get("Authorization"))
	if len(h) >= 7 && strings.EqualFold(h[:7], "bearer ") {
		return strings.TrimSpace(h[7:])
	}
	return ""
}

func cleanLabel(s, fallback string) string {
	s = strings.TrimSpace(spaces.ReplaceAllString(s, " "))
	if s == "" || !utf8.ValidString(s) {
		return fallback
	}
	return truncate(s, 40)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func httpError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func serverError(w http.ResponseWriter, err error) {
	log.Printf("erreur : %v", err)
	httpError(w, http.StatusInternalServerError, "erreur interne")
}
