package main

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"sync"
	"testing"
)

// fakePusher enregistre les pushs au lieu de les envoyer à Firebase.
type fakePusher struct {
	mu     sync.Mutex
	sent   map[string][]Push // par jeton FCM
	reject map[string]bool   // jetons refusés (UNREGISTERED)
}

func (f *fakePusher) Send(_ context.Context, token string, p Push) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.reject[token] {
		return errUnregistered
	}
	if f.sent == nil {
		f.sent = map[string][]Push{}
	}
	f.sent[token] = append(f.sent[token], p)
	return nil
}

func (f *fakePusher) payloads(t *testing.T, token string) []Payload {
	t.Helper()
	f.mu.Lock()
	defer f.mu.Unlock()
	var out []Payload
	for _, p := range f.sent[token] {
		var pl Payload
		if err := json.Unmarshal([]byte(p.Payload), &pl); err != nil {
			t.Fatalf("payload invalide %q", p.Payload)
		}
		// Seuls les messages envoyés à l'agent partent sans id (voir Server.push).
		if (p.ID == "" && pl.Type != typePrompt) || p.Time == 0 {
			t.Fatalf("push sans id ni date : %+v", p)
		}
		out = append(out, pl)
	}
	return out
}

type testEnv struct {
	url    string
	srv    *Server
	pusher *fakePusher
}

func newTestEnv(t *testing.T, dataFile string) *testEnv {
	t.Helper()
	store, err := OpenStore(dataFile)
	if err != nil {
		t.Fatal(err)
	}
	pusher := &fakePusher{reject: map[string]bool{}}
	srv := NewServer(Config{MaxDevices: 10}, store, pusher)
	ts := httptest.NewServer(srv)
	t.Cleanup(ts.Close)
	return &testEnv{url: ts.URL, srv: srv, pusher: pusher}
}

// hook envoie un événement comme le ferait Claude Code, puis attend la fin du push.
func (e *testEnv) hook(t *testing.T, code, body string, headers ...string) *http.Response {
	t.Helper()
	req, _ := http.NewRequest("POST", e.url+"/h", strings.NewReader(body))
	if code != "" {
		req.Header.Set("Authorization", "Bearer "+code)
	}
	for i := 0; i+1 < len(headers); i += 2 {
		req.Header.Set(headers[i], headers[i+1])
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	e.srv.sending.Wait()
	return resp
}

func call(t *testing.T, method, url, token, body string) (*http.Response, string) {
	t.Helper()
	req, _ := http.NewRequest(method, url, strings.NewReader(body))
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	return resp, string(b)
}

func register(t *testing.T, base, pushToken string) (id, token string) {
	t.Helper()
	resp, body := call(t, "POST", base+"/v1/devices", "", `{"name":"Galaxy Watch","push_token":"`+pushToken+`"}`)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("enregistrement : %d %s", resp.StatusCode, body)
	}
	var out struct{ ID, Token string }
	_ = json.Unmarshal([]byte(body), &out)
	return out.ID, out.Token
}

func newCode(t *testing.T, base, device, label string) codeView {
	t.Helper()
	resp, body := call(t, "POST", base+"/v1/codes", device, `{"label":"`+label+`"}`)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("création du code : %d %s", resp.StatusCode, body)
	}
	var c codeView
	_ = json.Unmarshal([]byte(body), &c)
	return c
}

func TestParcoursComplet(t *testing.T) {
	e := newTestEnv(t, "")
	_, token := register(t, e.url, "fcm-A")
	c := newCode(t, e.url, token, "MacBook")
	if !strings.Contains(c.Code, "-") || len(normalizeCode(c.Code)) != 12 {
		t.Fatalf("code inattendu %q", c.Code)
	}

	// Le code se saisit en minuscules, sans tirets, avec des espaces : peu importe. « //h » aussi.
	sloppy := strings.ToLower(strings.ReplaceAll(c.Code, "-", " "))
	resp, body := call(t, "POST", e.url+"//h", sloppy, `{"hook_event_name":"Notification","notification_type":"permission_prompt","message":"Claude needs your permission to use Bash","cwd":"/home/cyril/mon-projet","session_id":"abcdef1234567890"}`)
	e.srv.sending.Wait()
	if resp.StatusCode != http.StatusNoContent || body != "" {
		t.Fatalf("hook : %d %q", resp.StatusCode, body)
	}
	got := e.pusher.payloads(t, "fcm-A")
	want := Payload{Type: "permission", Message: "Claude needs your permission to use Bash", Project: "mon-projet", Session: "abcdef12", Account: "MacBook"}
	if len(got) != 1 || got[0] != want {
		t.Fatalf("pushs %+v, attendu %+v", got, want)
	}

	// La montre voit l'utilisation du code
	_, body = call(t, "GET", e.url+"/v1/codes", token, "")
	var codes []codeView
	_ = json.Unmarshal([]byte(body), &codes)
	if len(codes) != 1 || codes[0].LastUsed == nil || codes[0].LastProject != "mon-projet" || codes[0].Code != "" {
		t.Fatalf("codes : %s", body)
	}

	// Révocation
	if resp, _ := call(t, "DELETE", e.url+"/v1/codes/"+c.ID, token, ""); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("révocation : %d", resp.StatusCode)
	}
	if resp := e.hook(t, c.Code, `{"hook_event_name":"Stop"}`); resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("code révoqué accepté : %d", resp.StatusCode)
	}
	if n := len(e.pusher.payloads(t, "fcm-A")); n != 1 {
		t.Fatalf("%d pushs après révocation", n)
	}
}

// Message envoyé à l'agent : sans id, pour que les anciennes appli l'ignorent, et sans réveiller la montre.
func TestMessageEnvoye(t *testing.T) {
	e := newTestEnv(t, "")
	_, token := register(t, e.url, "fcm-A")
	c := newCode(t, e.url, token, "MacBook")
	e.hook(t, c.Code, `{"hook_event_name":"UserPromptSubmit","prompt":"mon secret","session_id":"abcdef1234567890","cwd":"/p/projet"}`)
	e.pusher.mu.Lock()
	sent := e.pusher.sent["fcm-A"]
	e.pusher.mu.Unlock()
	if len(sent) != 1 || sent[0].ID != "" || !sent[0].Normal || sent[0].TTL != promptTTL || strings.Contains(sent[0].Payload, "secret") {
		t.Fatalf("pushs %+v", sent)
	}
	if got := e.pusher.payloads(t, "fcm-A"); got[0].Type != "prompt" || got[0].Session != "abcdef12" || got[0].Project != "projet" {
		t.Fatalf("%+v", got)
	}
}

func TestIsolationEntreMontres(t *testing.T) {
	e := newTestEnv(t, "")
	_, tokA := register(t, e.url, "fcm-A")
	_, tokB := register(t, e.url, "fcm-B")
	codeA := newCode(t, e.url, tokA, "A")

	e.hook(t, codeA.Code, `{"hook_event_name":"Stop","last_assistant_message":"fini"}`)

	if got := e.pusher.payloads(t, "fcm-B"); len(got) != 0 {
		t.Fatalf("B reçoit les événements de A : %+v", got)
	}
	if got := e.pusher.payloads(t, "fcm-A"); len(got) != 1 || got[0].Message != "fini" {
		t.Fatalf("A : %+v", got)
	}
	if resp, _ := call(t, "DELETE", e.url+"/v1/codes/"+codeA.ID, tokB, ""); resp.StatusCode != http.StatusNotFound {
		t.Fatalf("B révoque le code de A : %d", resp.StatusCode)
	}
	if resp, _ := call(t, "GET", e.url+"/v1/codes", "ccd_faux", ""); resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("jeton invalide accepté : %d", resp.StatusCode)
	}
}

func TestHookSansCodeEtSessionCloud(t *testing.T) {
	e := newTestEnv(t, "")
	_, token := register(t, e.url, "fcm-A")
	c := newCode(t, e.url, token, "Cloud perso")

	// Hook versionné dans un dépôt, variable non définie : « Bearer » vide, ignoré sans erreur.
	if resp := e.hook(t, "", `{"hook_event_name":"Stop"}`); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("hook sans code : %d", resp.StatusCode)
	}
	e.hook(t, c.Code, `{"hook_event_name":"Stop","cwd":"/home/user/mon-depot","last_assistant_message":"PR prête"}`,
		"X-Claude-Remote-Session", "cse_016aBUrCQFd6JdJcmHdCzaBN")
	got := e.pusher.payloads(t, "fcm-A")
	if len(got) != 1 || !got[0].Cloud || got[0].URL != "https://claude.ai/code/session_016aBUrCQFd6JdJcmHdCzaBN" || got[0].Project != "mon-depot" {
		t.Fatalf("payload cloud %+v", got)
	}
}

func TestJetonFCM(t *testing.T) {
	e := newTestEnv(t, "")
	_, token := register(t, e.url, "")
	c := newCode(t, e.url, token, "PC")

	// Pas encore de jeton FCM : le test le signale à la montre.
	resp, body := call(t, "POST", e.url+"/v1/test", token, "")
	if resp.StatusCode != http.StatusBadGateway || !strings.Contains(body, "FCM token") {
		t.Fatalf("test sans jeton : %d %s", resp.StatusCode, body)
	}
	if resp, _ := call(t, "PUT", e.url+"/v1/device/push", token, `{"token":"fcm-1"}`); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("enregistrement du jeton : %d", resp.StatusCode)
	}
	if resp, _ := call(t, "POST", e.url+"/v1/test", token, ""); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("test : %d", resp.StatusCode)
	}
	if got := e.pusher.payloads(t, "fcm-1"); len(got) != 1 || got[0].Account != "Serveur" {
		t.Fatalf("push de test : %+v", got)
	}

	// Firebase refuse le jeton (appli réinstallée) : le serveur l'oublie.
	e.pusher.mu.Lock()
	e.pusher.reject["fcm-1"] = true
	e.pusher.mu.Unlock()
	e.hook(t, c.Code, `{"hook_event_name":"Stop"}`)
	_, body = call(t, "GET", e.url+"/v1/device", token, "")
	if !strings.Contains(body, `"push":false`) {
		t.Fatalf("jeton refusé conservé : %s", body)
	}
}

func TestPersistance(t *testing.T) {
	file := filepath.Join(t.TempDir(), "state.json")
	e := newTestEnv(t, file)
	_, token := register(t, e.url, "fcm-A")
	c := newCode(t, e.url, token, "PC")

	e2 := newTestEnv(t, file)
	e2.hook(t, c.Code, `{"hook_event_name":"SessionEnd","reason":"logout"}`)
	if got := e2.pusher.payloads(t, "fcm-A"); len(got) != 1 || got[0].Message != "Session terminée (logout)" {
		t.Fatalf("après redémarrage : %+v", got)
	}
}

func TestToPayload(t *testing.T) {
	cases := []struct {
		name string
		in   HookInput
		want Payload
		show bool
	}{
		{"attente", HookInput{HookEventName: "Notification", NotificationType: "idle_prompt", Message: "Claude is waiting for your input"}, Payload{Type: "input", Message: "Claude is waiting for your input"}, true},
		{"ancienne notification sans type", HookInput{HookEventName: "Notification", Message: "Claude needs your permission to use Bash"}, Payload{Type: "permission", Message: "Claude needs your permission to use Bash"}, true},
		{"connexion réussie ignorée", HookInput{HookEventName: "Notification", NotificationType: "auth_success"}, Payload{}, false},
		{"fin sans texte", HookInput{HookEventName: "Stop"}, Payload{Type: "done", Message: "Claude a terminé sa tâche."}, true},
		{"erreur API", HookInput{HookEventName: "StopFailure", Error: "rate_limit", LastAssistantMessage: "API Error: Rate limit reached"}, Payload{Type: "error", Message: "API Error: Rate limit reached"}, true},
		{"erreur API sans texte", HookInput{HookEventName: "StopFailure", Error: "overloaded"}, Payload{Type: "error", Message: "Erreur API (overloaded)"}, true},
		{"compaction", HookInput{HookEventName: "PreCompact", Trigger: "auto"}, Payload{Type: "compact", Message: "Compaction du contexte (auto)"}, true},
		{"chemin Windows", HookInput{HookEventName: "SubagentStop", Cwd: `C:\Users\cyril\projet-win\`}, Payload{Type: "subagent", Message: "Un sous-agent a terminé.", Project: "projet-win"}, true},
		{"message envoyé, sans son texte", HookInput{HookEventName: "UserPromptSubmit", Message: "secret"}, Payload{Type: "prompt", Message: "Message envoyé à Claude"}, true},
		{"sans événement", HookInput{}, Payload{}, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, show := ToPayload(tc.in, "", "", "")
			if show != tc.show || (show && got != tc.want) {
				t.Fatalf("reçu %+v (%v), attendu %+v (%v)", got, show, tc.want, tc.show)
			}
		})
	}

	long := strings.Repeat("é", 400) + "\n\n  fin"
	got, _ := ToPayload(HookInput{HookEventName: "Stop", LastAssistantMessage: long}, "", "", "")
	if n := len([]rune(got.Message)); n != maxMessageRunes {
		t.Fatalf("message de %d caractères", n)
	}
	got, _ = ToPayload(HookInput{HookEventName: "Stop", LastAssistantMessage: "a\n\tb   c"}, "", "pas un id/valide", "")
	if got.Message != "a\n  b c" || got.Cloud {
		t.Fatalf("%+v", got)
	}

	// Markdown de Claude : lignes et indentation gardées, une seule ligne vide entre paragraphes.
	md := "## Fini  \r\n\n\n\n- **un**\n  - deux   mots\n\n```\nx := 1\n```\n  "
	got, _ = ToPayload(HookInput{HookEventName: "Stop", LastAssistantMessage: md}, "", "", "")
	if want := "## Fini\n\n- **un**\n  - deux mots\n\n```\nx := 1\n```"; got.Message != want {
		t.Fatalf("message %q, attendu %q", got.Message, want)
	}
}

func TestCodes(t *testing.T) {
	seen := map[string]bool{}
	for i := 0; i < 200; i++ {
		c := newPairingCode()
		if len(c) != 14 || c[4] != '-' || c[9] != '-' {
			t.Fatalf("format %q", c)
		}
		for _, r := range normalizeCode(c) {
			if !strings.ContainsRune(codeAlphabet, r) {
				t.Fatalf("caractère %q hors alphabet", r)
			}
		}
		if seen[c] {
			t.Fatalf("doublon %q", c)
		}
		seen[c] = true
	}
}
