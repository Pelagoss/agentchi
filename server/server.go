package main

import (
	"context"
	"encoding/json"
	"errors"
	"html/template"
	"io"
	"log"
	"net"
	"net/http"
	"path"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

type Config struct {
	// URL publique du serveur, affichée dans la page d'accueil et les extraits de configuration.
	PublicURL string
	// Faire confiance à X-Forwarded-For / X-Forwarded-Proto (derrière un reverse proxy).
	TrustProxy bool
	MaxDevices int
	// Attente d'une réponse de la montre, à la place du réglage de chaque montre (tests).
	AskTimeout time.Duration
}

type Server struct {
	cfg    Config
	store  *Store
	pusher Pusher
	mux    *http.ServeMux
	// Envois FCM en cours : le hook répond sans attendre Firebase.
	sending sync.WaitGroup
	// Questions qui attendent la réponse de la montre (ask.go).
	asks askRegistry

	registerLimit *limiter // création de montres, par IP
	hookLimit     *limiter // événements, par code
	authFailLimit *limiter // jetons ou codes invalides, par IP
	apiLimit      *limiter // API de la montre, par IP
}

const pushTimeout = 15 * time.Second

func NewServer(cfg Config, store *Store, pusher Pusher) *Server {
	s := &Server{
		cfg:           cfg,
		store:         store,
		pusher:        pusher,
		mux:           http.NewServeMux(),
		registerLimit: newLimiter(2, 5),
		hookLimit:     newLimiter(120, 60),
		authFailLimit: newLimiter(10, 20),
		apiLimit:      newLimiter(120, 60),
		asks:          askRegistry{m: map[string]*pendingAsk{}},
	}
	s.mux.HandleFunc("GET /{$}", s.handleHome)
	s.mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { io.WriteString(w, "ok\n") })
	s.mux.HandleFunc("GET /v1/cloud-settings.json", s.handleCloudSettings)

	// Hook Claude Code : le code d'appairage sert de jeton.
	s.mux.HandleFunc("POST /h", s.handleHook)
	// Hook qui attend la réponse de la montre (autorisation, question, plan, réponse à Claude).
	s.mux.HandleFunc("POST /ask", s.handleAsk)

	// API de la montre
	s.mux.HandleFunc("POST /v1/devices", s.handleRegister)
	s.mux.HandleFunc("GET /v1/device", s.device(s.handleGetDevice))
	s.mux.HandleFunc("PUT /v1/device/push", s.device(s.handleSetPush))
	s.mux.HandleFunc("PUT /v1/device/answers", s.device(s.handleSetAnswers))
	s.mux.HandleFunc("DELETE /v1/device", s.device(s.handleDeleteDevice))
	s.mux.HandleFunc("POST /v1/asks/{id}", s.device(s.handleAnswer))
	s.mux.HandleFunc("POST /v1/codes", s.device(s.handleCreateCode))
	s.mux.HandleFunc("GET /v1/codes", s.device(s.handleListCodes))
	s.mux.HandleFunc("DELETE /v1/codes/{id}", s.device(s.handleDeleteCode))
	s.mux.HandleFunc("POST /v1/test", s.device(s.handleTest))
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
	in, c, ok := s.hookRequest(w, r)
	if !ok {
		return
	}
	payload, show := ToPayload(in, c.Label, r.Header.Get("X-Claude-Remote-Session"))
	if show {
		s.pushAsync(c.DeviceID, payload)
	}
	// Corps vide : Claude Code n'en tire aucune décision ni aucun contexte.
	w.WriteHeader(http.StatusNoContent)
}

// hookRequest lit l'événement d'un hook et authentifie son code d'appairage.
// ok vaut false si la réponse est déjà écrite (sans code, code refusé, JSON invalide…).
func (s *Server) hookRequest(w http.ResponseWriter, r *http.Request) (HookInput, Code, bool) {
	var in HookInput
	code := bearer(r)
	if code == "" {
		// Hook versionné dans un dépôt, sans code pour cette personne : rien à faire, sans erreur.
		w.WriteHeader(http.StatusNoContent)
		return in, Code{}, false
	}
	ip := s.clientIP(r)
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&in); err != nil {
		httpError(w, http.StatusBadRequest, "JSON invalide")
		return in, Code{}, false
	}
	c, ok := s.store.UseCode(code, projectOf(in.Cwd))
	if !ok {
		s.authFailed(w, ip, "code d'appairage inconnu ou révoqué")
		return in, Code{}, false
	}
	if !s.hookLimit.Allow(c.ID) {
		httpError(w, http.StatusTooManyRequests, "trop d'événements")
		return in, Code{}, false
	}
	return in, c, true
}

// pushAsync envoie l'événement sans faire attendre le hook.
func (s *Server) pushAsync(deviceID string, p Payload) {
	s.sending.Add(1)
	go func() {
		defer s.sending.Done()
		ctx, cancel := context.WithTimeout(context.Background(), pushTimeout)
		defer cancel()
		if err := s.push(ctx, deviceID, p, 0); err != nil {
			log.Printf("push vers %s : %v", deviceID, err)
		}
	}()
}

// --- API de la montre ---

// push envoie un événement à la montre par FCM. Un jeton refusé par Firebase est oublié.
// ttl : durée de vie du message chez Firebase, 0 pour la valeur par défaut (24 h).
func (s *Server) push(ctx context.Context, deviceID string, p Payload, ttl time.Duration) error {
	token := s.store.PushToken(deviceID)
	if token == "" {
		return errNoPushToken
	}
	err := s.pusher.Send(ctx, token, Push{ID: "e" + randomToken(12), Time: time.Now().Unix(), Payload: p.JSON(), TTL: ttl})
	if errors.Is(err, errUnregistered) {
		_ = s.store.ForgetPushToken(deviceID, token)
	}
	return err
}

var errNoPushToken = errors.New("la montre n'a pas encore transmis son jeton FCM")

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
		next(w, r, &d)
	}
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	if !s.registerLimit.Allow(s.clientIP(r)) {
		httpError(w, http.StatusTooManyRequests, "trop de montres créées depuis cette adresse")
		return
	}
	var body struct {
		Name      string `json:"name"`
		PushToken string `json:"push_token"`
	}
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&body)
	if s.cfg.MaxDevices > 0 && s.store.DeviceCount() >= s.cfg.MaxDevices {
		httpError(w, http.StatusForbidden, "ce serveur n'accepte plus de nouvelles montres")
		return
	}
	d, token, err := s.store.CreateDevice(cleanLabel(body.Name, "Montre"), strings.TrimSpace(body.PushToken))
	if err != nil {
		serverError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, map[string]string{"id": d.ID, "token": token})
}

func (s *Server) handleGetDevice(w http.ResponseWriter, _ *http.Request, d *Device) {
	writeJSON(w, http.StatusOK, map[string]any{"id": d.ID, "name": d.Name, "created": d.Created, "push": d.PushToken != "", "answers": d.Answers, "answer_timeout": d.AnswerTimeout})
}

func (s *Server) handleSetPush(w http.ResponseWriter, r *http.Request, d *Device) {
	var body struct {
		Token string `json:"token"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&body); err != nil || strings.TrimSpace(body.Token) == "" {
		httpError(w, http.StatusBadRequest, "jeton FCM manquant")
		return
	}
	if err := s.store.SetPushToken(d.ID, strings.TrimSpace(body.Token)); err != nil {
		serverError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
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

// handleTest envoie un push de test et attend la réponse de Firebase, pour afficher l'erreur sur la montre.
func (s *Server) handleTest(w http.ResponseWriter, r *http.Request, d *Device) {
	p := Payload{Type: "done", Message: "Push FCM reçu : tout fonctionne 🎉", Account: "Serveur"}
	ctx, cancel := context.WithTimeout(r.Context(), pushTimeout)
	defer cancel()
	if err := s.push(ctx, d.ID, p, 0); err != nil {
		log.Printf("test vers %s : %v", d.ID, err)
		httpError(w, http.StatusBadGateway, err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
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
var cloudEvents = []string{"Notification", "StopFailure", "SubagentStop", "SessionEnd", "PreCompact"}

// askTimeoutHook : délai du hook /ask, un peu au-delà de maxAskTimeout.
const askTimeoutHook = 310

// CloudSettings : bloc à ajouter au .claude/settings.json d'un dépôt pour ses sessions cloud.
func CloudSettings(publicURL string) map[string]any {
	hook := func(path string, timeout int) map[string]any {
		return map[string]any{
			"type":    "http",
			"url":     publicURL + path,
			"timeout": timeout,
			"headers": map[string]string{
				"Authorization":           "Bearer $CLAUDE_COMPANION_CODE",
				"X-Claude-Remote-Session": "$CLAUDE_CODE_REMOTE_SESSION_ID",
			},
			"allowedEnvVars": []string{"CLAUDE_COMPANION_CODE", "CLAUDE_CODE_REMOTE_SESSION_ID"},
		}
	}
	hooks := map[string]any{}
	for _, e := range cloudEvents {
		hooks[e] = []any{map[string]any{"hooks": []any{hook("/h", 5)}}}
	}
	// Questions auxquelles la montre peut répondre : le hook attend sa réponse.
	ask := hook("/ask", askTimeoutHook)
	hooks["Stop"] = []any{map[string]any{"hooks": []any{ask}}}
	hooks["PermissionRequest"] = []any{map[string]any{"hooks": []any{ask}}}
	hooks["PreToolUse"] = []any{map[string]any{"matcher": "AskUserQuestion|ExitPlanMode", "hooks": []any{ask}}}
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
