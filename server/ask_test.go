package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// askEnv : une montre connectée, avec un code et les réponses activées.
type askEnv struct {
	*testEnv
	device, code string
}

func newAskEnv(t *testing.T, timeout time.Duration) *askEnv {
	t.Helper()
	e := newTestEnv(t, "")
	e.srv.cfg.AskTimeout = timeout
	_, device := register(t, e.url, "fcm-A")
	c := newCode(t, e.url, device, "MacBook")
	if resp, body := call(t, "PUT", e.url+"/v1/device/answers", device, `{"enabled":true}`); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("activation des réponses : %d %s", resp.StatusCode, body)
	}
	return &askEnv{testEnv: e, device: device, code: c.Code}
}

type askResult struct {
	status int
	body   string
}

// ask lance le hook /ask, qui attend la réponse de la montre.
func (e *askEnv) ask(t *testing.T, body string) <-chan askResult {
	t.Helper()
	out := make(chan askResult, 1)
	go func() {
		req, _ := http.NewRequest("POST", e.url+"/ask", strings.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+e.code)
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			out <- askResult{status: -1, body: err.Error()}
			return
		}
		defer resp.Body.Close()
		b, _ := io.ReadAll(resp.Body)
		out <- askResult{resp.StatusCode, string(b)}
	}()
	return out
}

// question attend le push de la question sur la montre.
func (e *askEnv) question(t *testing.T) Payload {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		for _, p := range e.pusher.payloads(t, "fcm-A") {
			if p.Ask != nil {
				return p
			}
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatal("aucune question poussée à la montre")
	return Payload{}
}

func (e *askEnv) answer(t *testing.T, id, body string) (int, string) {
	t.Helper()
	resp, b := call(t, "POST", e.url+"/v1/asks/"+id, e.device, body)
	return resp.StatusCode, b
}

func wait(t *testing.T, ch <-chan askResult) askResult {
	t.Helper()
	select {
	case r := <-ch:
		return r
	case <-time.After(3 * time.Second):
		t.Fatal("le hook n'a pas répondu")
		return askResult{}
	}
}

func decode(t *testing.T, s string) map[string]any {
	t.Helper()
	var m map[string]any
	if err := json.Unmarshal([]byte(s), &m); err != nil {
		t.Fatalf("sortie de hook invalide %q", s)
	}
	return m
}

const permissionRequest = `{"hook_event_name":"PermissionRequest","cwd":"/home/cyril/mon-projet","session_id":"abcdef1234567890",
	"tool_name":"Bash","tool_input":{"command":"npm test","description":"Lance les tests"},
	"permission_suggestions":[{"type":"addRules","rules":[{"toolName":"Bash","ruleContent":"npm test"}],"behavior":"allow","destination":"localSettings"}]}`

func TestAutorisationDepuisLaMontre(t *testing.T) {
	e := newAskEnv(t, 5*time.Second)

	res := e.ask(t, permissionRequest)
	q := e.question(t)
	if q.Type != "permission" || q.Project != "mon-projet" || q.Account != "MacBook" || q.Ask.Kind != askPermission ||
		q.Ask.Detail != "npm test" || !q.Ask.Always || q.Ask.Tool != "Bash" || q.Ask.Expires == 0 {
		t.Fatalf("question %+v %+v", q, q.Ask)
	}

	// Une autre montre ne peut pas répondre à sa place.
	_, other := register(t, e.url, "fcm-B")
	if resp, _ := call(t, "POST", e.url+"/v1/asks/"+q.Ask.ID, other, `{"decision":"allow"}`); resp.StatusCode != http.StatusGone {
		t.Fatalf("réponse d'une autre montre : %d", resp.StatusCode)
	}
	if status, body := e.answer(t, q.Ask.ID, `{"decision":"peut-être"}`); status != http.StatusBadRequest {
		t.Fatalf("décision inconnue : %d %s", status, body)
	}

	if status, body := e.answer(t, q.Ask.ID, `{"decision":"always"}`); status != http.StatusNoContent {
		t.Fatalf("réponse : %d %s", status, body)
	}
	r := wait(t, res)
	if r.status != http.StatusOK {
		t.Fatalf("hook : %d %s", r.status, r.body)
	}
	hso := decode(t, r.body)["hookSpecificOutput"].(map[string]any)
	decision := hso["decision"].(map[string]any)
	if hso["hookEventName"] != "PermissionRequest" || decision["behavior"] != "allow" {
		t.Fatalf("sortie %s", r.body)
	}
	if rules, _ := decision["updatedPermissions"].([]any); len(rules) != 1 {
		t.Fatalf("règle « toujours autoriser » absente : %s", r.body)
	}

	// Une question ne sert qu'une fois.
	if status, _ := e.answer(t, q.Ask.ID, `{"decision":"allow"}`); status != http.StatusGone {
		t.Fatalf("seconde réponse : %d", status)
	}
}

func TestRefusAvecRaison(t *testing.T) {
	e := newAskEnv(t, 5*time.Second)
	res := e.ask(t, permissionRequest)
	q := e.question(t)
	e.answer(t, q.Ask.ID, `{"decision":"deny","message":"pas sur la prod"}`)
	r := wait(t, res)
	decision := decode(t, r.body)["hookSpecificOutput"].(map[string]any)["decision"].(map[string]any)
	if decision["behavior"] != "deny" || !strings.Contains(decision["message"].(string), "pas sur la prod") {
		t.Fatalf("sortie %s", r.body)
	}
}

func TestQuestionAChoix(t *testing.T) {
	e := newAskEnv(t, 5*time.Second)
	res := e.ask(t, `{"hook_event_name":"PreToolUse","tool_name":"AskUserQuestion","cwd":"/p","tool_input":{"questions":[
		{"question":"Quelle base ?","header":"Base","multiSelect":false,"options":[{"label":"Postgres","description":"Relationnelle"},{"label":"SQLite"}]},
		{"question":"Quelles étapes ?","header":"Étapes","multiSelect":true,"options":[{"label":"Tests"},{"label":"Docs"},{"label":"CI"}]}]}}`)
	q := e.question(t)
	if q.Type != "input" || q.Ask.Kind != askQuestion || len(q.Ask.Questions) != 2 || q.Message != "Quelle base ?" ||
		!q.Ask.Questions[1].Multi || q.Ask.Questions[0].Options[0] != (OptionView{"Postgres", "Relationnelle"}) {
		t.Fatalf("question %+v", q.Ask)
	}
	if status, body := e.answer(t, q.Ask.ID, `{"decision":"allow","answers":{"Quelle base ?":"SQLite"}}`); status != http.StatusBadRequest ||
		!strings.Contains(body, "Quelles étapes") {
		t.Fatalf("réponse incomplète acceptée : %d %s", status, body)
	}
	if status, body := e.answer(t, q.Ask.ID, `{"decision":"allow","answers":{"Quelle base ?":"SQLite","Quelles étapes ?":"Tests, CI"}}`); status != http.StatusNoContent {
		t.Fatalf("réponse : %d %s", status, body)
	}
	r := wait(t, res)
	hso := decode(t, r.body)["hookSpecificOutput"].(map[string]any)
	input := hso["updatedInput"].(map[string]any)
	answers := input["answers"].(map[string]any)
	if hso["permissionDecision"] != "allow" || len(input["questions"].([]any)) != 2 ||
		answers["Quelle base ?"] != "SQLite" || answers["Quelles étapes ?"] != "Tests, CI" {
		t.Fatalf("sortie %s", r.body)
	}
}

func TestPlan(t *testing.T) {
	e := newAskEnv(t, 5*time.Second)
	res := e.ask(t, `{"hook_event_name":"PreToolUse","tool_name":"ExitPlanMode","tool_input":{"plan":"1. Lire\n2. Écrire","planFilePath":"/tmp/plan.md"}}`)
	q := e.question(t)
	if q.Ask.Kind != askPlan || q.Ask.Detail != "1. Lire\n2. Écrire" {
		t.Fatalf("plan %+v", q.Ask)
	}
	e.answer(t, q.Ask.ID, `{"decision":"allow"}`)
	hso := decode(t, wait(t, res).body)["hookSpecificOutput"].(map[string]any)
	if hso["permissionDecision"] != "allow" || hso["updatedInput"].(map[string]any)["planFilePath"] != "/tmp/plan.md" {
		t.Fatalf("sortie %+v", hso)
	}
}

func TestReponseAClaude(t *testing.T) {
	e := newAskEnv(t, 5*time.Second)
	res := e.ask(t, `{"hook_event_name":"Stop","last_assistant_message":"J'ai fini. Je fais aussi la doc ?"}`)
	q := e.question(t)
	if q.Type != "done" || q.Ask.Kind != askReply || q.Ask.Detail != "J'ai fini. Je fais aussi la doc ?" {
		t.Fatalf("question %+v", q.Ask)
	}
	e.answer(t, q.Ask.ID, `{"decision":"allow","message":"oui, en français"}`)
	out := decode(t, wait(t, res).body)
	if out["decision"] != "block" || !strings.HasSuffix(out["reason"].(string), "oui, en français") {
		t.Fatalf("sortie %+v", out)
	}

	// « Terminer » : Claude s'arrête, pas de décision.
	res = e.ask(t, `{"hook_event_name":"Stop","last_assistant_message":"Fini."}`)
	var id string
	deadline := time.Now().Add(2 * time.Second)
	for id == "" && time.Now().Before(deadline) {
		ps := e.pusher.payloads(t, "fcm-A")
		if last := ps[len(ps)-1]; last.Ask != nil && last.Ask.ID != q.Ask.ID {
			id = last.Ask.ID
		}
		time.Sleep(5 * time.Millisecond)
	}
	e.answer(t, id, `{"decision":"pass"}`)
	if r := wait(t, res); r.status != http.StatusNoContent || r.body != "" {
		t.Fatalf("passer : %d %q", r.status, r.body)
	}
}

func TestSansReponseALaMontre(t *testing.T) {
	e := newAskEnv(t, 100*time.Millisecond)
	res := e.ask(t, permissionRequest)
	q := e.question(t)
	r := wait(t, res)
	if r.status != http.StatusNoContent || r.body != "" {
		t.Fatalf("délai dépassé : %d %q", r.status, r.body)
	}
	e.srv.sending.Wait()
	ps := e.pusher.payloads(t, "fcm-A")
	if last := ps[len(ps)-1]; last.Type != "ask_end" || last.End != q.Ask.ID {
		t.Fatalf("la question n'est pas retirée de la montre : %+v", last)
	}
	if status, _ := e.answer(t, q.Ask.ID, `{"decision":"allow"}`); status != http.StatusGone {
		t.Fatalf("réponse tardive : %d", status)
	}
}

func TestReponsesDesactivees(t *testing.T) {
	e := newAskEnv(t, 5*time.Second)
	call(t, "PUT", e.url+"/v1/device/answers", e.device, `{"enabled":false}`)

	// Rend la main tout de suite : l'ordinateur pose la question comme d'habitude.
	start := time.Now()
	if r := wait(t, e.ask(t, permissionRequest)); r.status != http.StatusNoContent || time.Since(start) > time.Second {
		t.Fatalf("autorisation : %d en %v", r.status, time.Since(start))
	}
	// Stop reste une notification de fin de tâche.
	wait(t, e.ask(t, `{"hook_event_name":"Stop","last_assistant_message":"fini"}`))
	e.srv.sending.Wait()
	ps := e.pusher.payloads(t, "fcm-A")
	if len(ps) != 1 || ps[0].Type != "done" || ps[0].Ask != nil || ps[0].Message != "fini" {
		t.Fatalf("pushs %+v", ps)
	}
	_, body := call(t, "GET", e.url+"/v1/device", e.device, "")
	if !strings.Contains(body, `"answers":false`) {
		t.Fatalf("montre : %s", body)
	}
}

func TestPermissionRequestDesQuestions(t *testing.T) {
	// AskUserQuestion et ExitPlanMode passent par PreToolUse : PermissionRequest ne les pose pas deux fois.
	if _, _, ok := buildAsk(HookInput{HookEventName: "PermissionRequest", ToolName: "AskUserQuestion"}, "", "", "", time.Minute); ok {
		t.Fatal("AskUserQuestion posée par PermissionRequest")
	}
	if _, _, ok := buildAsk(HookInput{HookEventName: "PreToolUse", ToolName: "Bash"}, "", "", "", time.Minute); ok {
		t.Fatal("PreToolUse Bash posé comme question")
	}
	if _, _, ok := buildAsk(HookInput{HookEventName: "PreToolUse", ToolName: "AskUserQuestion", ToolInput: json.RawMessage(`{}`)}, "", "", "", time.Minute); ok {
		t.Fatal("AskUserQuestion sans question")
	}
}

func TestQuestionTientDansUnPush(t *testing.T) {
	long := strings.Repeat("é", 5000)
	_, p, _ := buildAsk(HookInput{HookEventName: "Stop", LastAssistantMessage: long + " Une question ?"}, "", "", "", time.Minute)
	if n := len(p.JSON()); n > maxAskPayload {
		t.Fatalf("payload de %d octets", n)
	}
	if !strings.HasSuffix(p.Ask.Detail, "Une question ?") {
		t.Fatalf("la fin du message est perdue : %q", p.Ask.Detail)
	}

	var qs []string
	for i := 0; i < 4; i++ {
		var opts []string
		for j := 0; j < 4; j++ {
			opts = append(opts, `{"label":"`+strings.Repeat("l", 60)+`","description":"`+strings.Repeat("d", 400)+`"}`)
		}
		qs = append(qs, `{"question":"Q`+string(rune('0'+i))+strings.Repeat("?", 200)+`","options":[`+strings.Join(opts, ",")+`]}`)
	}
	_, p, ok := buildAsk(HookInput{HookEventName: "PreToolUse", ToolName: "AskUserQuestion", ToolInput: json.RawMessage(`{"questions":[` + strings.Join(qs, ",") + `]}`)}, "", "", "", time.Minute)
	if !ok || len(p.JSON()) > maxAskPayload || len(p.Ask.Questions) != 4 {
		t.Fatalf("questions : %v, %d octets", ok, len(p.JSON()))
	}
}

func TestToPayloadIgnoreLesQuestions(t *testing.T) {
	for _, ev := range []string{"PermissionRequest", "PreToolUse"} {
		if _, show := ToPayload(HookInput{HookEventName: ev}, "", "", ""); show {
			t.Fatalf("%s affiché par /h", ev)
		}
	}
}

// En production, le serveur a des délais de lecture et d'écriture plus courts que l'attente d'une réponse.
func TestAttenteAuDelaDesDelaisDuServeur(t *testing.T) {
	store, _ := OpenStore("")
	pusher := &fakePusher{reject: map[string]bool{}}
	srv := NewServer(Config{MaxDevices: 10, AskTimeout: 5 * time.Second}, store, pusher)
	ts := httptest.NewUnstartedServer(srv)
	ts.Config.ReadTimeout = 200 * time.Millisecond
	ts.Config.WriteTimeout = 200 * time.Millisecond
	ts.Start()
	t.Cleanup(ts.Close)
	e := &askEnv{testEnv: &testEnv{url: ts.URL, srv: srv, pusher: pusher}}
	_, e.device = register(t, e.url, "fcm-A")
	e.code = newCode(t, e.url, e.device, "PC").Code
	call(t, "PUT", e.url+"/v1/device/answers", e.device, `{"enabled":true}`)

	res := e.ask(t, permissionRequest)
	q := e.question(t)
	time.Sleep(600 * time.Millisecond)
	if status, body := e.answer(t, q.Ask.ID, `{"decision":"allow"}`); status != http.StatusNoContent {
		t.Fatalf("réponse : %d %s", status, body)
	}
	if r := wait(t, res); r.status != http.StatusOK || !strings.Contains(r.body, `"allow"`) {
		t.Fatalf("hook : %d %q", r.status, r.body)
	}
}

func TestDelaiChoisiSurLaMontre(t *testing.T) {
	e := newAskEnv(t, 0)
	if resp, body := call(t, "PUT", e.url+"/v1/device/answers", e.device, `{"enabled":true,"timeout":10}`); resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("délai trop court accepté : %d %s", resp.StatusCode, body)
	}
	if resp, body := call(t, "PUT", e.url+"/v1/device/answers", e.device, `{"enabled":true,"timeout":300}`); resp.StatusCode != http.StatusNoContent {
		t.Fatalf("délai : %d %s", resp.StatusCode, body)
	}
	// Réactiver sans délai garde le délai choisi.
	call(t, "PUT", e.url+"/v1/device/answers", e.device, `{"enabled":true}`)
	_, body := call(t, "GET", e.url+"/v1/device", e.device, "")
	if !strings.Contains(body, `"answer_timeout":300`) {
		t.Fatalf("montre : %s", body)
	}

	res := e.ask(t, permissionRequest)
	q := e.question(t)
	if left := time.Until(time.Unix(q.Ask.Expires, 0)); left < 290*time.Second || left > 301*time.Second {
		t.Fatalf("échéance dans %v", left)
	}
	e.answer(t, q.Ask.ID, `{"decision":"allow"}`)
	wait(t, res)
}
