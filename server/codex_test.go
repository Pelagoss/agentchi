package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// Le plugin Codex du dépôt lit l'adresse du serveur dans AGENTCHI_SERVER.
const codexPluginServer = "${AGENTCHI_SERVER:-https://agentchi.dev}"

func codexHooksJSON(t *testing.T, server string) []byte {
	t.Helper()
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetIndent("", "  ")
	enc.SetEscapeHTML(false)
	if err := enc.Encode(CodexHooks(server)); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

// Le hooks.json du plugin agentchi-codex est généré : UPDATE_CODEX_HOOKS=1 go test -run TestPluginCodex
func TestPluginCodexAJour(t *testing.T) {
	path := filepath.Join("..", "plugins", "agentchi-codex", "hooks", "hooks.json")
	want := codexHooksJSON(t, codexPluginServer)
	if os.Getenv("UPDATE_CODEX_HOOKS") != "" {
		if err := os.WriteFile(path, want, 0o644); err != nil {
			t.Fatal(err)
		}
	}
	got, err := os.ReadFile(path)
	if err != nil || !bytes.Equal(got, want) {
		t.Fatalf("%s n'est pas à jour : UPDATE_CODEX_HOOKS=1 go test -run TestPluginCodex", path)
	}
}

func TestHooksCodex(t *testing.T) {
	h := CodexHooks("https://agentchi.example")["hooks"].(map[string]any)
	if _, ok := h["Notification"]; ok {
		t.Fatal("Codex n'a pas d'événement Notification")
	}
	stop := h["Stop"].([]any)[0].(map[string]any)["hooks"].([]any)[0].(map[string]any)
	cmd := stop["command"].(string)
	if stop["timeout"] != askTimeoutHook || stop["async"] != nil ||
		!strings.Contains(cmd, `"https://agentchi.example/ask"`) || !strings.Contains(cmd, "X-Agent: codex") || !strings.Contains(cmd, "${AGENTCHI_CODE:-}") {
		t.Fatalf("hook Stop %+v", stop)
	}
	start := h["SessionStart"].([]any)[0].(map[string]any)["hooks"].([]any)[0].(map[string]any)
	if start["async"] != true || !strings.Contains(start["command"].(string), "/h\"") {
		t.Fatalf("hook SessionStart %+v", start)
	}

	e := newTestEnv(t, "")
	resp, body := call(t, "GET", e.url+"/v1/codex-hooks.json", "", "")
	if resp.StatusCode != http.StatusOK || !strings.Contains(body, e.url+"/ask") || strings.Contains(body, `&`) {
		t.Fatalf("codex-hooks.json : %d %s", resp.StatusCode, body)
	}
}

func TestEvenementsCodex(t *testing.T) {
	e := newTestEnv(t, "")
	_, device := register(t, e.url, "fcm-A")
	c := newCode(t, e.url, device, "MacBook")
	e.hook(t, c.Code, `{"hook_event_name":"Stop","cwd":"/p","turn_id":"t1","model":"gpt-5"}`, "X-Agent", "codex")
	e.hook(t, c.Code, `{"hook_event_name":"Stop","cwd":"/p"}`)
	got := e.pusher.payloads(t, "fcm-A")
	if len(got) != 2 || got[0].Agent != "codex" || got[0].Message != "Codex a terminé sa tâche." || got[1].Agent != "" || got[1].Message != "Claude a terminé sa tâche." {
		t.Fatalf("pushs %+v", got)
	}
}

func TestPatchCodexSurLaMontre(t *testing.T) {
	patch := "*** Begin Patch\n*** Update File: src/app.ts\n@@\n-a\n+b\n*** Add File: docs/new.md\n+x\n*** Delete File: old.txt\n*** End Patch"
	input, _ := json.Marshal(map[string]any{"command": patch})
	v, p, ok := buildAsk(HookInput{HookEventName: "PermissionRequest", ToolName: "apply_patch", ToolInput: input, Agent: "codex"}, "", "", "", time.Minute)
	if !ok || p.Message != "Codex veut modifier des fichiers" || v.Detail != "~ src/app.ts\n+ docs/new.md\n- old.txt" || v.Always || p.Agent != "codex" {
		t.Fatalf("%+v %+v", p, v)
	}
	// Commande shell : même format que Claude Code (Bash, command, description).
	_, p, _ = buildAsk(HookInput{HookEventName: "PermissionRequest", ToolName: "Bash", ToolInput: json.RawMessage(`{"command":"npm test","description":"tests"}`), Agent: "codex"}, "", "", "", time.Minute)
	if p.Message != "Codex veut utiliser Bash" || p.Ask.Detail != "npm test" {
		t.Fatalf("%+v", p)
	}
}
