package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

// Répondre depuis la montre.
//
// Les hooks PermissionRequest, PreToolUse (AskUserQuestion, ExitPlanMode) et Stop appellent /ask
// et attendent. Si la montre a activé les réponses, le serveur lui pousse la question puis garde la
// requête ouverte jusqu'à sa réponse (POST /v1/asks/{id}), qu'il traduit en décision de hook
// (https://code.claude.com/docs/en/hooks). Sans réponse à temps, ou si la montre passe, le corps
// est vide : Claude Code affiche la question comme d'habitude. Rien n'est écrit sur disque.

const (
	// Attente par défaut, et bornes du réglage de la montre. Le hook attend 310 s
	// (plugin et hook http) : on rend toujours la main avant.
	defaultAskTimeout = 120 * time.Second
	minAskTimeout     = 30 * time.Second
	maxAskTimeout     = 300 * time.Second
	maxAsksPerDevice  = 10
	// Message « data » FCM : 4 Ko au plus, clés comprises.
	maxAskPayload  = 3500
	maxAnswerRunes = 2000
)

const (
	askPermission = "permission" // PermissionRequest : autoriser, toujours autoriser, refuser
	askQuestion   = "question"   // AskUserQuestion : choix simples, multiples ou réponse libre
	askPlan       = "plan"       // ExitPlanMode : approuver ou refuser le plan
	askReply      = "reply"      // Stop : répondre à Claude, qui repart avec ce texte
)

// AskView : la question telle que l'affiche la montre (champ « q » du payload).
type AskView struct {
	ID   string `json:"id"`
	Kind string `json:"k"`
	Tool string `json:"tool,omitempty"`
	// Commande, fichier, plan ou dernier message de Claude.
	Detail string `json:"d,omitempty"`
	// « Toujours autoriser » : Claude Code propose une règle de permission.
	Always    bool           `json:"al,omitempty"`
	Questions []QuestionView `json:"qs,omitempty"`
	// Date limite de réponse (Unix, secondes).
	Expires int64 `json:"x"`
}

type QuestionView struct {
	Question string       `json:"q"`
	Header   string       `json:"h,omitempty"`
	Multi    bool         `json:"mu,omitempty"`
	Options  []OptionView `json:"o"`
}

type OptionView struct {
	Label       string `json:"l"`
	Description string `json:"d,omitempty"`
}

// Answer : réponse de la montre.
type Answer struct {
	// allow, always (toujours autoriser), deny ou pass (laisser l'ordinateur demander).
	Decision string `json:"decision"`
	// Raison d'un refus, ou texte de la réponse à Claude (Stop).
	Message string `json:"message"`
	// AskUserQuestion : question → libellé choisi (choix multiples séparés par des virgules) ou texte libre.
	Answers map[string]string `json:"answers"`
}

type pendingAsk struct {
	id       string
	deviceID string
	kind     string
	in       HookInput
	// Sortie du hook (nil : pas de décision), envoyée une seule fois.
	done chan []byte
}

type askRegistry struct {
	mu sync.Mutex
	m  map[string]*pendingAsk
}

var errAskGone = errors.New("cette question n'attend plus de réponse")

func (r *askRegistry) add(p *pendingAsk) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	n := 0
	for _, other := range r.m {
		if other.deviceID == p.deviceID {
			n++
		}
	}
	if n >= maxAsksPerDevice {
		return false
	}
	r.m[p.id] = p
	return true
}

func (r *askRegistry) remove(id string) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	_, ok := r.m[id]
	delete(r.m, id)
	return ok
}

// answer valide la réponse et la transmet au hook en attente. Une question ne sert qu'une fois.
func (r *askRegistry) answer(deviceID, id string, a Answer) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	p, ok := r.m[id]
	if !ok || p.deviceID != deviceID {
		return errAskGone
	}
	out, err := hookOutput(p.kind, p.in, a)
	if err != nil {
		return err
	}
	delete(r.m, id)
	p.done <- out
	return nil
}

// --- Hook ---

func (s *Server) handleAsk(w http.ResponseWriter, r *http.Request) {
	in, c, ok := s.hookRequest(w, r)
	if !ok {
		return
	}
	remote := r.Header.Get("X-Claude-Remote-Session")
	answers, deviceTimeout := s.store.Answers(c.DeviceID)
	timeout := s.askTimeout(deviceTimeout)
	view, payload, askable := buildAsk(in, c.Label, remote, timeout)
	if !askable || !answers {
		// Pas de réponse possible : Stop reste une simple notification, comme avec /h.
		if p, show := ToPayload(in, c.Label, remote); show {
			s.pushAsync(c.DeviceID, p)
		}
		w.WriteHeader(http.StatusNoContent)
		return
	}

	p := &pendingAsk{id: view.ID, deviceID: c.DeviceID, kind: view.Kind, in: in, done: make(chan []byte, 1)}
	if !s.asks.add(p) {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), pushTimeout)
	err := s.push(ctx, c.DeviceID, payload, timeout)
	cancel()
	if err != nil {
		s.asks.remove(p.id)
		log.Printf("question vers %s : %v", c.DeviceID, err)
		w.WriteHeader(http.StatusNoContent)
		return
	}

	// La réponse peut prendre jusqu'à askTimeout : au-delà du WriteTimeout du serveur.
	_ = http.NewResponseController(w).SetWriteDeadline(time.Now().Add(timeout + 10*time.Second))
	timer := time.NewTimer(timeout)
	defer timer.Stop()
	select {
	case out := <-p.done:
		if out == nil {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(out)
	case <-timer.C:
		s.endAsk(p)
		w.WriteHeader(http.StatusNoContent)
	case <-r.Context().Done():
		// Claude Code a abandonné le hook (question traitée sur l'ordinateur, session interrompue…).
		s.endAsk(p)
	}
}

// askTimeout : attente choisie sur la montre, bornée ; Config.AskTimeout la remplace (tests).
func (s *Server) askTimeout(device time.Duration) time.Duration {
	switch {
	case s.cfg.AskTimeout > 0:
		return s.cfg.AskTimeout
	case device == 0:
		return defaultAskTimeout
	}
	return min(max(device, minAskTimeout), maxAskTimeout)
}

// endAsk retire une question restée sans réponse et la fait disparaître de la montre.
func (s *Server) endAsk(p *pendingAsk) {
	if s.asks.remove(p.id) {
		s.pushAsync(p.deviceID, Payload{Type: "ask_end", End: p.id})
	}
}

// --- API de la montre ---

func (s *Server) handleAnswer(w http.ResponseWriter, r *http.Request, d *Device) {
	var a Answer
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&a); err != nil {
		httpError(w, http.StatusBadRequest, "JSON invalide")
		return
	}
	switch err := s.asks.answer(d.ID, r.PathValue("id"), a); {
	case errors.Is(err, errAskGone):
		httpError(w, http.StatusGone, err.Error())
	case err != nil:
		httpError(w, http.StatusBadRequest, err.Error())
	default:
		w.WriteHeader(http.StatusNoContent)
	}
}

func (s *Server) handleSetAnswers(w http.ResponseWriter, r *http.Request, d *Device) {
	var body struct {
		Enabled bool `json:"enabled"`
		// Secondes, facultatif.
		Timeout int `json:"timeout"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1024)).Decode(&body); err != nil {
		httpError(w, http.StatusBadRequest, "JSON invalide")
		return
	}
	if body.Timeout != 0 && (time.Duration(body.Timeout)*time.Second < minAskTimeout || time.Duration(body.Timeout)*time.Second > maxAskTimeout) {
		httpError(w, http.StatusBadRequest, fmt.Sprintf("délai entre %d et %d secondes", int(minAskTimeout.Seconds()), int(maxAskTimeout.Seconds())))
		return
	}
	if err := s.store.SetAnswers(d.ID, body.Enabled, body.Timeout); err != nil {
		serverError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// --- Questions ---

// buildAsk prépare la question à pousser à la montre. ok vaut false si l'événement n'en est pas une.
func buildAsk(in HookInput, account, remoteSession string, timeout time.Duration) (AskView, Payload, bool) {
	v := AskView{ID: "q" + randomToken(21), Tool: in.ToolName, Expires: time.Now().Add(timeout).Unix()}
	p := Payload{Account: account, Ask: &v}
	tool := toolInput(in.ToolInput)
	switch {
	case in.HookEventName == "PermissionRequest" && in.ToolName != "AskUserQuestion" && in.ToolName != "ExitPlanMode":
		v.Kind, p.Type = askPermission, "permission"
		v.Detail = describeTool(tool, in.ToolInput)
		v.Always = hasSuggestions(in.PermissionSuggestions)
		p.Message = "Claude veut utiliser " + firstNonEmpty(in.ToolName, "un outil")
	case in.HookEventName == "PreToolUse" && in.ToolName == "AskUserQuestion":
		v.Kind, p.Type = askQuestion, "input"
		v.Questions = questionsOf(tool)
		if len(v.Questions) == 0 {
			return v, p, false
		}
		p.Message = v.Questions[0].Question
	case in.HookEventName == "PreToolUse" && in.ToolName == "ExitPlanMode":
		v.Kind, p.Type = askPlan, "input"
		plan, _ := tool["plan"].(string)
		v.Detail = strings.TrimSpace(plan)
		p.Message = "Claude propose un plan"
	case in.HookEventName == "Stop":
		v.Kind, p.Type = askReply, "done"
		v.Tool = ""
		// La fin du message, là où Claude pose sa question.
		v.Detail = tail(strings.TrimSpace(in.LastAssistantMessage), 1500)
		p.Message = firstNonEmpty(in.LastAssistantMessage, "Claude a terminé sa tâche.")
	default:
		return v, p, false
	}
	p.describe(in, remoteSession)
	fitPayload(&p)
	return v, p, true
}

func toolInput(raw json.RawMessage) map[string]any {
	var m map[string]any
	if json.Unmarshal(raw, &m) != nil || m == nil {
		m = map[string]any{}
	}
	return m
}

// describeTool : ce que l'outil va faire, en une ligne lisible.
func describeTool(tool map[string]any, raw json.RawMessage) string {
	for _, k := range []string{"command", "file_path", "notebook_path", "url", "pattern", "query", "path", "description", "prompt"} {
		if v, ok := tool[k].(string); ok && strings.TrimSpace(v) != "" {
			return truncate(strings.TrimSpace(v), 600)
		}
	}
	if len(tool) == 0 {
		return ""
	}
	return truncate(string(raw), 600)
}

func hasSuggestions(raw json.RawMessage) bool {
	var list []json.RawMessage
	return json.Unmarshal(raw, &list) == nil && len(list) > 0
}

func questionsOf(tool map[string]any) []QuestionView {
	list, _ := tool["questions"].([]any)
	var out []QuestionView
	for _, item := range list {
		q, _ := item.(map[string]any)
		text, _ := q["question"].(string)
		if strings.TrimSpace(text) == "" {
			continue
		}
		header, _ := q["header"].(string)
		multi, _ := q["multiSelect"].(bool)
		qv := QuestionView{Question: text, Header: header, Multi: multi}
		options, _ := q["options"].([]any)
		for _, o := range options {
			om, _ := o.(map[string]any)
			label, _ := om["label"].(string)
			desc, _ := om["description"].(string)
			if label != "" {
				qv.Options = append(qv.Options, OptionView{Label: label, Description: desc})
			}
		}
		out = append(out, qv)
	}
	return out
}

// fitPayload raccourcit la question pour tenir dans un message FCM.
func fitPayload(p *Payload) {
	size := func() int { return len(p.JSON()) }
	if size() <= maxAskPayload {
		return
	}
	for i := range p.Ask.Questions {
		for j := range p.Ask.Questions[i].Options {
			p.Ask.Questions[i].Options[j].Description = truncate(p.Ask.Questions[i].Options[j].Description, 60)
		}
	}
	p.Message = truncate(p.Message, 120)
	for size() > maxAskPayload && p.Ask.Detail != "" {
		n := utf8.RuneCountInString(p.Ask.Detail)
		over := size() - maxAskPayload
		keep := n - max(over, n/4)
		if keep <= 0 {
			p.Ask.Detail = ""
			break
		}
		if p.Ask.Kind == askReply {
			p.Ask.Detail = tail(p.Ask.Detail, keep)
		} else {
			p.Ask.Detail = truncate(p.Ask.Detail, keep)
		}
	}
	if size() > maxAskPayload {
		for i := range p.Ask.Questions {
			for j := range p.Ask.Questions[i].Options {
				p.Ask.Questions[i].Options[j].Description = ""
			}
		}
	}
}

// hookOutput traduit la réponse de la montre en sortie de hook. nil : pas de décision.
func hookOutput(kind string, in HookInput, a Answer) ([]byte, error) {
	a.Message = strings.TrimSpace(a.Message)
	if utf8.RuneCountInString(a.Message) > maxAnswerRunes {
		return nil, errors.New("réponse trop longue")
	}
	if a.Decision == "pass" {
		return nil, nil
	}
	var out any
	switch kind {
	case askPermission:
		decision := map[string]any{}
		switch a.Decision {
		case "allow":
			decision["behavior"] = "allow"
		case "always":
			decision["behavior"] = "allow"
			if hasSuggestions(in.PermissionSuggestions) {
				decision["updatedPermissions"] = in.PermissionSuggestions
			}
		case "deny":
			decision["behavior"] = "deny"
			decision["message"] = withReason("Refusé par l'utilisateur depuis sa montre.", a.Message)
		default:
			return nil, errors.New("décision inconnue")
		}
		out = map[string]any{"hookSpecificOutput": map[string]any{"hookEventName": "PermissionRequest", "decision": decision}}
	case askQuestion, askPlan:
		hso := map[string]any{"hookEventName": "PreToolUse"}
		switch a.Decision {
		case "allow":
			tool := toolInput(in.ToolInput)
			if kind == askQuestion {
				answers, err := checkAnswers(questionsOf(tool), a.Answers)
				if err != nil {
					return nil, err
				}
				tool["answers"] = answers
			}
			hso["permissionDecision"] = "allow"
			hso["updatedInput"] = tool
		case "deny":
			reason := "L'utilisateur a refusé de répondre depuis sa montre."
			if kind == askPlan {
				reason = "L'utilisateur a refusé le plan depuis sa montre."
			}
			hso["permissionDecision"] = "deny"
			hso["permissionDecisionReason"] = withReason(reason, a.Message)
		default:
			return nil, errors.New("décision inconnue")
		}
		out = map[string]any{"hookSpecificOutput": hso}
	case askReply:
		if a.Decision != "allow" {
			return nil, nil
		}
		if a.Message == "" {
			return nil, errors.New("réponse vide")
		}
		out = map[string]any{"decision": "block", "reason": "Réponse de l'utilisateur, envoyée depuis sa montre : " + a.Message}
	default:
		return nil, errors.New("question inconnue")
	}
	return json.Marshal(out)
}

// checkAnswers vérifie qu'il y a une réponse, non vide, à chaque question, et à rien d'autre.
func checkAnswers(questions []QuestionView, answers map[string]string) (map[string]string, error) {
	out := map[string]string{}
	for _, q := range questions {
		v := strings.TrimSpace(answers[q.Question])
		if v == "" {
			return nil, errors.New("réponse manquante : " + truncate(q.Question, 80))
		}
		if utf8.RuneCountInString(v) > maxAnswerRunes {
			return nil, errors.New("réponse trop longue")
		}
		out[q.Question] = v
	}
	if len(answers) != len(out) {
		return nil, errors.New("réponse à une question inconnue")
	}
	return out, nil
}

func withReason(base, reason string) string {
	if reason == "" {
		return base
	}
	return base + " Raison : " + reason
}

// tail garde les n derniers caractères.
func tail(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return "…" + string(r[len(r)-n:])
}
