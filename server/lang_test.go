package main

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestWatchLang(t *testing.T) {
	for in, want := range map[string]string{"": "", "fr": "fr", "fr-FR": "fr", " FR ": "fr", "en": "en", "en-US": "en", "de": "en"} {
		if got := watchLang(in); got != want {
			t.Errorf("watchLang(%q) = %q, attendu %q", in, got, want)
		}
	}
}

// Les textes écrits par le serveur suivent la langue de la montre ; sans langue (ancienne appli), français.
func TestTextesSelonLaLangue(t *testing.T) {
	stop := HookInput{HookEventName: "Stop", Agent: "codex"}
	for lang, want := range map[string]string{"": "Codex a terminé sa tâche.", "fr": "Codex a terminé sa tâche.", "en": "Codex finished its task."} {
		if p, _ := ToPayload(stop, "", "", lang); p.Message != want {
			t.Errorf("%q : %q, attendu %q", lang, p.Message, want)
		}
	}
	if p, _ := ToPayload(HookInput{HookEventName: "SessionEnd", Reason: "exit"}, "", "", "en"); p.Message != "Session ended (exit)" {
		t.Errorf("SessionEnd : %q", p.Message)
	}
	_, p, _ := buildAsk(HookInput{HookEventName: "PermissionRequest", ToolName: "Bash", ToolInput: []byte(`{"command":"ls"}`)}, "", "", "en", time.Minute)
	if p.Message != "Claude wants to use Bash" {
		t.Errorf("autorisation : %q", p.Message)
	}
}

func TestLangueDeLaMontre(t *testing.T) {
	e := newTestEnv(t, "")
	resp, body := call(t, "POST", e.url+"/v1/devices", "", `{"push_token":"fcm-en","lang":"en-US"}`)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("enregistrement : %d %s", resp.StatusCode, body)
	}
	var en struct{ Token string }
	_ = json.Unmarshal([]byte(body), &en)
	enToken := en.Token
	_, token := register(t, e.url, "fcm-fr")
	if _, got := call(t, "GET", e.url+"/v1/device", enToken, ""); !strings.Contains(got, `"name":"Watch"`) || !strings.Contains(got, `"lang":"en"`) {
		t.Fatalf("montre anglaise : %s", got)
	}

	// Push de test dans la langue de chaque montre ; la langue suit l'envoi du jeton FCM.
	call(t, "POST", e.url+"/v1/test", enToken, "")
	call(t, "POST", e.url+"/v1/test", token, "")
	if got := e.pusher.payloads(t, "fcm-en"); len(got) != 1 || got[0].Account != "Server" || !strings.HasPrefix(got[0].Message, "FCM push received") {
		t.Fatalf("push de test (en) : %+v", got)
	}
	if got := e.pusher.payloads(t, "fcm-fr"); len(got) != 1 || got[0].Account != "Serveur" {
		t.Fatalf("push de test (fr) : %+v", got)
	}
	call(t, "PUT", e.url+"/v1/device/push", token, `{"token":"fcm-fr","lang":"en"}`)
	call(t, "PUT", e.url+"/v1/device/push", enToken, `{"token":"fcm-en"}`) // sans langue : inchangée
	call(t, "POST", e.url+"/v1/test", token, "")
	call(t, "POST", e.url+"/v1/test", enToken, "")
	if got := e.pusher.payloads(t, "fcm-fr"); len(got) != 2 || got[1].Account != "Server" {
		t.Fatalf("langue changée : %+v", got)
	}
	if got := e.pusher.payloads(t, "fcm-en"); len(got) != 2 || got[1].Account != "Server" {
		t.Fatalf("langue conservée : %+v", got)
	}
}
