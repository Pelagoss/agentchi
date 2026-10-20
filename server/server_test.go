package main

import (
	"bufio"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func newTestServer(t *testing.T, dataFile string) (*httptest.Server, *Store) {
	t.Helper()
	store, err := OpenStore(dataFile)
	if err != nil {
		t.Fatal(err)
	}
	ts := httptest.NewServer(NewServer(Config{MaxDevices: 10}, store))
	t.Cleanup(ts.Close)
	return ts, store
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

func register(t *testing.T, base string) (id, token string) {
	t.Helper()
	resp, body := call(t, "POST", base+"/v1/devices", "", `{"name":"Galaxy Watch"}`)
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

func poll(t *testing.T, base, id, token, since string) []ntfyLine {
	t.Helper()
	resp, body := call(t, "GET", base+"/"+id+"/json?poll=1&since="+since, token, "")
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("sondage : %d %s", resp.StatusCode, body)
	}
	var lines []ntfyLine
	for _, l := range strings.Split(strings.TrimSpace(body), "\n") {
		if l == "" {
			continue
		}
		var n ntfyLine
		if err := json.Unmarshal([]byte(l), &n); err != nil {
			t.Fatalf("ligne invalide %q", l)
		}
		lines = append(lines, n)
	}
	return lines
}

func payloadOf(t *testing.T, l ntfyLine) Payload {
	t.Helper()
	var p Payload
	if err := json.Unmarshal([]byte(l.Message), &p); err != nil {
		t.Fatalf("message invalide %q", l.Message)
	}
	return p
}

func TestParcoursComplet(t *testing.T) {
	ts, _ := newTestServer(t, "")
	id, token := register(t, ts.URL)
	c := newCode(t, ts.URL, token, "MacBook")
	if !strings.Contains(c.Code, "-") || len(normalizeCode(c.Code)) != 12 {
		t.Fatalf("code inattendu %q", c.Code)
	}

	// Le code se saisit en minuscules, sans tirets, avec des espaces : peu importe.
	sloppy := strings.ToLower(strings.ReplaceAll(c.Code, "-", " "))
	resp, body := call(t, "POST", ts.URL+"//h", sloppy, `{"hook_event_name":"Notification","notification_type":"permission_prompt","message":"Claude needs your permission to use Bash","cwd":"/home/cyril/mon-projet","session_id":"abcdef1234567890"}`)
	if resp.StatusCode != http.StatusNoContent || body != "" {
		t.Fatalf("hook : %d %q", resp.StatusCode, body)
	}

	lines := poll(t, ts.URL, id, token, "all")
	if len(lines) != 1 || lines[0].Event != "message" || lines[0].Topic != id {
		t.Fatalf("événements : %+v", lines)
	}
	p := payloadOf(t, lines[0])
	want := Payload{Type: "permission", Message: "Claude needs your permission to use Bash", Project: "mon-projet", Session: "abcdef12", Account: "MacBook"}
	if p != want {
		t.Fatalf("payload %+v, attendu %+v", p, want)
	}

	// since = dernier id reçu : plus rien
	if got := poll(t, ts.URL, id, token, lines[0].ID); len(got) != 0 {
		t.Fatalf("attendu aucun événement après %s, reçu %+v", lines[0].ID, got)
	}

	// La montre voit l'utilisation du code
	_, body = call(t, "GET", ts.URL+"/v1/codes", token, "")
	var codes []codeView
	_ = json.Unmarshal([]byte(body), &codes)
	if len(codes) != 1 || codes[0].LastUsed == nil || codes[0].LastProject != "mon-projet" || codes[0].Code != "" {
		t.Fatalf("codes : %s", body)
	}

	// Révocation
	if resp, _ := call(t, "DELETE", ts.URL+"/v1/codes/"+c.ID, token, ""); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("révocation : %d", resp.StatusCode)
	}
	if resp, _ := call(t, "POST", ts.URL+"/h", c.Code, `{"hook_event_name":"Stop"}`); resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("code révoqué accepté : %d", resp.StatusCode)
	}
}

func TestIsolationEntreMontres(t *testing.T) {
	ts, _ := newTestServer(t, "")
	idA, tokA := register(t, ts.URL)
	idB, tokB := register(t, ts.URL)
	codeA := newCode(t, ts.URL, tokA, "A")

	call(t, "POST", ts.URL+"/h", codeA.Code, `{"hook_event_name":"Stop","last_assistant_message":"fini"}`)

	if got := poll(t, ts.URL, idB, tokB, "all"); len(got) != 0 {
		t.Fatalf("B reçoit les événements de A : %+v", got)
	}
	if resp, _ := call(t, "GET", ts.URL+"/"+idA+"/json?poll=1", tokB, ""); resp.StatusCode != http.StatusForbidden {
		t.Fatalf("B lit le flux de A : %d", resp.StatusCode)
	}
	if resp, _ := call(t, "DELETE", ts.URL+"/v1/codes/"+codeA.ID, tokB, ""); resp.StatusCode != http.StatusNotFound {
		t.Fatalf("B révoque le code de A : %d", resp.StatusCode)
	}
	if resp, _ := call(t, "GET", ts.URL+"/v1/codes", "ccd_faux", ""); resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("jeton invalide accepté : %d", resp.StatusCode)
	}
	if got := poll(t, ts.URL, idA, tokA, "all"); len(got) != 1 || payloadOf(t, got[0]).Message != "fini" {
		t.Fatalf("A : %+v", got)
	}
}

func TestHookSansCodeEtSessionCloud(t *testing.T) {
	ts, _ := newTestServer(t, "")
	id, token := register(t, ts.URL)
	c := newCode(t, ts.URL, token, "Cloud perso")

	// Hook versionné dans un dépôt, variable non définie : « Bearer » vide, ignoré sans erreur.
	if resp, _ := call(t, "POST", ts.URL+"/h", "", `{"hook_event_name":"Stop"}`); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("hook sans code : %d", resp.StatusCode)
	}

	req, _ := http.NewRequest("POST", ts.URL+"/h", strings.NewReader(`{"hook_event_name":"Stop","cwd":"/home/user/mon-depot","last_assistant_message":"PR prête"}`))
	req.Header.Set("Authorization", "Bearer "+c.Code)
	req.Header.Set("X-Claude-Remote-Session", "cse_016aBUrCQFd6JdJcmHdCzaBN")
	resp, err := http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusNoContent {
		t.Fatalf("hook cloud : %v %v", err, resp)
	}
	lines := poll(t, ts.URL, id, token, "")
	p := payloadOf(t, lines[len(lines)-1])
	if !p.Cloud || p.URL != "https://claude.ai/code/session_016aBUrCQFd6JdJcmHdCzaBN" || p.Project != "mon-depot" {
		t.Fatalf("payload cloud %+v", p)
	}
}

func TestFluxInstantane(t *testing.T) {
	ts, _ := newTestServer(t, "")
	id, token := register(t, ts.URL)
	c := newCode(t, ts.URL, token, "PC")
	call(t, "POST", ts.URL+"/h", c.Code, `{"hook_event_name":"Stop","last_assistant_message":"un"}`)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	req, _ := http.NewRequestWithContext(ctx, "GET", ts.URL+"/"+id+"/json?since=all", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	sc := bufio.NewScanner(resp.Body)
	next := func() ntfyLine {
		if !sc.Scan() {
			t.Fatalf("flux fermé : %v", sc.Err())
		}
		var l ntfyLine
		_ = json.Unmarshal(sc.Bytes(), &l)
		return l
	}
	if l := next(); l.Event != "open" {
		t.Fatalf("attendu open, reçu %+v", l)
	}
	if l := next(); payloadOf(t, l).Message != "un" {
		t.Fatalf("attendu l'arriéré, reçu %+v", l)
	}
	call(t, "POST", ts.URL+"/h", c.Code, `{"hook_event_name":"Notification","notification_type":"idle_prompt","message":"Claude is waiting for your input"}`)
	if l := next(); payloadOf(t, l).Type != "input" {
		t.Fatalf("attendu l'événement en direct, reçu %+v", l)
	}
}

func TestPersistance(t *testing.T) {
	file := filepath.Join(t.TempDir(), "state.json")
	ts, _ := newTestServer(t, file)
	id, token := register(t, ts.URL)
	c := newCode(t, ts.URL, token, "PC")
	call(t, "POST", ts.URL+"/h", c.Code, `{"hook_event_name":"SessionEnd","reason":"logout"}`)
	ts.Close()

	ts2, _ := newTestServer(t, file)
	lines := poll(t, ts2.URL, id, token, "all")
	if len(lines) != 1 || payloadOf(t, lines[0]).Message != "Session terminée (logout)" {
		t.Fatalf("après redémarrage : %+v", lines)
	}
	if resp, _ := call(t, "POST", ts2.URL+"/h", c.Code, `{"hook_event_name":"Stop"}`); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("code perdu au redémarrage : %d", resp.StatusCode)
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
		{"sans événement", HookInput{}, Payload{}, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, show := ToPayload(tc.in, "", "")
			if show != tc.show || (show && got != tc.want) {
				t.Fatalf("reçu %+v (%v), attendu %+v (%v)", got, show, tc.want, tc.show)
			}
		})
	}

	long := strings.Repeat("é", 400) + "\n\n  fin"
	got, _ := ToPayload(HookInput{HookEventName: "Stop", LastAssistantMessage: long}, "", "")
	if n := len([]rune(got.Message)); n != maxMessageRunes {
		t.Fatalf("message de %d caractères", n)
	}
	got, _ = ToPayload(HookInput{HookEventName: "Stop", LastAssistantMessage: "a\n\tb   c"}, "", "pas un id/valide")
	if got.Message != "a b c" || got.Cloud {
		t.Fatalf("%+v", got)
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
