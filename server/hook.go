package main

import (
	"encoding/json"
	"path"
	"regexp"
	"strings"
	"unicode/utf8"
)

// HookInput : champs utiles du JSON qu'envoie un hook Claude Code (https://code.claude.com/docs/en/hooks)
// ou Codex (même format, voir openai/codex, crate codex-rs/hooks).
type HookInput struct {
	HookEventName        string `json:"hook_event_name"`
	SessionID            string `json:"session_id"`
	Cwd                  string `json:"cwd"`
	Message              string `json:"message"`
	NotificationType     string `json:"notification_type"`
	LastAssistantMessage string `json:"last_assistant_message"`
	Source               string `json:"source"`
	Reason               string `json:"reason"`
	Trigger              string `json:"trigger"`
	Error                string `json:"error"`
	ErrorDetails         string `json:"error_details"`
	// PermissionRequest et PreToolUse
	ToolName              string          `json:"tool_name"`
	ToolInput             json.RawMessage `json:"tool_input"`
	PermissionSuggestions json.RawMessage `json:"permission_suggestions"`

	// Agent à l'origine du hook (en-tête X-Agent) : vide pour Claude Code, « codex » pour Codex.
	Agent string `json:"-"`
}

const agentCodex = "codex"

// typePrompt : message envoyé à l'agent, jamais notifié (voir Server.push).
const typePrompt = "prompt"

// AgentName : nom affiché de l'agent.
func (in HookInput) AgentName() string {
	if in.Agent == agentCodex {
		return "Codex"
	}
	return "Claude"
}

// agentOf lit l'en-tête X-Agent ; tout ce qui n'est pas Codex est Claude Code.
func agentOf(header string) string {
	if strings.EqualFold(strings.TrimSpace(header), agentCodex) {
		return agentCodex
	}
	return ""
}

// Payload : ce que lit la montre (voir app/.../net/HookPayload.kt).
type Payload struct {
	Type    string `json:"t"`
	Message string `json:"m,omitempty"`
	Project string `json:"p,omitempty"`
	Session string `json:"s,omitempty"`
	Account string `json:"a,omitempty"`
	Cloud   bool   `json:"c,omitempty"`
	URL     string `json:"u,omitempty"`
	// Agent : vide pour Claude Code, « codex » pour Codex.
	Agent string `json:"ag,omitempty"`
	// Question à laquelle la montre peut répondre (voir ask.go).
	Ask *AskView `json:"q,omitempty"`
	// Type « ask_end » : id de la question qui n'attend plus de réponse.
	End string `json:"e,omitempty"`
}

// Une montre, une ligne, 300 caractères au plus.
const maxMessageRunes = 300

var (
	spaces         = regexp.MustCompile(`\s+`)
	inlineSpaces   = regexp.MustCompile(`[ \t]+`)
	remoteSessionR = regexp.MustCompile(`^[A-Za-z0-9_-]{1,128}$`)
)

// ToPayload traduit un événement de hook. ok vaut false pour un événement que la montre n'affiche pas.
// remoteSession est l'id de session cloud (variable CLAUDE_CODE_REMOTE_SESSION_ID), vide en local.
func ToPayload(in HookInput, account, remoteSession string) (Payload, bool) {
	p := Payload{Type: "other", Account: account}
	switch in.HookEventName {
	case "Notification":
		p.Message = in.Message
		switch in.NotificationType {
		case "permission_prompt":
			p.Type = "permission"
		case "idle_prompt", "elicitation_dialog":
			p.Type = "input"
		case "auth_success":
			return p, false
		case "":
			lower := strings.ToLower(in.Message)
			if strings.Contains(lower, "permission") {
				p.Type = "permission"
			} else if strings.Contains(lower, "waiting") || strings.Contains(lower, "input") {
				p.Type = "input"
			}
		default:
			if strings.HasPrefix(in.NotificationType, "elicitation") {
				p.Type = "input"
			}
		}
	case "Stop":
		p.Type = "done"
		p.Message = in.LastAssistantMessage
		if strings.TrimSpace(p.Message) == "" {
			p.Message = in.AgentName() + " a terminé sa tâche."
		}
	case "StopFailure":
		p.Type = "error"
		// Pour StopFailure, last_assistant_message contient le texte de l'erreur API.
		p.Message = firstNonEmpty(in.LastAssistantMessage, in.ErrorDetails, "Erreur API ("+firstNonEmpty(in.Error, "unknown")+")")
	case "SubagentStop":
		p.Type = "subagent"
		p.Message = "Un sous-agent a terminé."
	case "SessionStart":
		p.Type = "session_start"
		p.Message = "Nouvelle session" + paren(in.Source)
	case "SessionEnd":
		p.Type = "session_end"
		p.Message = "Session terminée" + paren(in.Reason)
	case "PreCompact":
		p.Type = "compact"
		p.Message = "Compaction du contexte" + paren(in.Trigger)
	case "UserPromptSubmit":
		// La session se remet au travail (écran « Sessions en cours ») : le message lui-même n'est pas transmis.
		p.Type = typePrompt
		p.Message = "Message envoyé à " + in.AgentName()
	case "", "PermissionRequest", "PreToolUse":
		// Les questions passent par /ask (ask.go), qui les affiche lui-même.
		return p, false
	default:
		p.Message = in.Message
	}

	p.describe(in, remoteSession)
	return p, true
}

// describe complète le payload : projet, session, origine cloud, message sur une ligne.
func (p *Payload) describe(in HookInput, remoteSession string) {
	p.Agent = in.Agent
	p.Project = projectOf(in.Cwd)
	p.Session = truncate(in.SessionID, 8)
	p.Message = compact(p.Message, maxMessageRunes)
	if p.Message == "" {
		p.Message = "Événement " + in.AgentName() + " (" + p.Type + ")"
	}
	if remoteSessionR.MatchString(remoteSession) {
		p.Cloud = true
		p.URL = "https://claude.ai/code/session_" + strings.TrimPrefix(remoteSession, "cse_")
	}
}

// projectOf : nom du dossier de travail, sous Windows comme ailleurs.
func projectOf(cwd string) string {
	if cwd == "" {
		return ""
	}
	return path.Base(strings.ReplaceAll(strings.TrimRight(cwd, `/\`), `\`, "/"))
}

// compact garde les retours à la ligne (la montre affiche le Markdown de l'agent : listes, titres…)
// et l'indentation, mais pas les espaces en trop ni plus d'une ligne vide d'affilée.
func compact(s string, n int) string {
	var out []string
	blank := false
	for _, line := range strings.Split(strings.ReplaceAll(s, "\r\n", "\n"), "\n") {
		line = strings.TrimRight(strings.ReplaceAll(line, "\t", "  "), " ")
		if line == "" {
			if !blank && len(out) > 0 {
				out = append(out, "")
			}
			blank = true
			continue
		}
		blank = false
		body := strings.TrimLeft(line, " ")
		out = append(out, line[:len(line)-len(body)]+inlineSpaces.ReplaceAllString(body, " "))
	}
	return truncate(strings.TrimSpace(strings.Join(out, "\n")), n)
}

func (p Payload) JSON() string {
	b, _ := json.Marshal(p)
	return string(b)
}

func paren(s string) string {
	if s == "" {
		return ""
	}
	return " (" + s + ")"
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return v
		}
	}
	return ""
}

func truncate(s string, n int) string {
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	return string([]rune(s)[:n])
}
