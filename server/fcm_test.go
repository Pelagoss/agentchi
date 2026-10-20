package main

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync/atomic"
	"testing"
)

// Faux Google : vérifie le JWT du compte de service, puis le message envoyé à FCM.
func TestFCM(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	der, _ := x509.MarshalPKCS8PrivateKey(key)
	var tokenCalls atomic.Int32
	var sent map[string]any

	mux := http.NewServeMux()
	google := httptest.NewServer(mux)
	defer google.Close()
	mux.HandleFunc("POST /token", func(w http.ResponseWriter, r *http.Request) {
		tokenCalls.Add(1)
		_ = r.ParseForm()
		parts := strings.Split(r.Form.Get("assertion"), ".")
		if r.Form.Get("grant_type") != "urn:ietf:params:oauth:grant-type:jwt-bearer" || len(parts) != 3 {
			http.Error(w, "bad request", 400)
			return
		}
		sig, _ := base64.RawURLEncoding.DecodeString(parts[2])
		sum := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
		if rsa.VerifyPKCS1v15(&key.PublicKey, crypto.SHA256, sum[:], sig) != nil {
			http.Error(w, "bad signature", 401)
			return
		}
		raw, _ := base64.RawURLEncoding.DecodeString(parts[1])
		var claims map[string]any
		_ = json.Unmarshal(raw, &claims)
		if claims["iss"] != "companion@projet.iam.gserviceaccount.com" || claims["scope"] != fcmScope || claims["aud"] != google.URL+"/token" {
			http.Error(w, "bad claims", 401)
			return
		}
		_, _ = io.WriteString(w, `{"access_token":"ya29.test","expires_in":3600,"token_type":"Bearer"}`)
	})
	mux.HandleFunc("POST /v1/projects/projet-test/messages:send", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer ya29.test" {
			http.Error(w, "unauthorized", 401)
			return
		}
		var body map[string]map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		if body["message"]["token"] == "perime" {
			w.WriteHeader(404)
			_, _ = io.WriteString(w, `{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}`)
			return
		}
		sent = body["message"]
		_, _ = io.WriteString(w, `{"name":"projects/projet-test/messages/1"}`)
	})

	creds, _ := json.Marshal(map[string]string{
		"type":         "service_account",
		"project_id":   "projet-test",
		"client_email": "companion@projet.iam.gserviceaccount.com",
		"private_key":  string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})),
		"token_uri":    google.URL + "/token",
	})
	fcm, err := NewFCM(creds)
	if err != nil {
		t.Fatal(err)
	}
	fcm.sendURL = google.URL + "/v1/projects/" + url.PathEscape("projet-test") + "/messages:send"

	ctx := context.Background()
	for i := 0; i < 2; i++ {
		if err := fcm.Send(ctx, "jeton-montre", Push{ID: "eabc", Time: 1792500000, Payload: `{"t":"done"}`}); err != nil {
			t.Fatal(err)
		}
	}
	if n := tokenCalls.Load(); n != 1 {
		t.Fatalf("jeton OAuth demandé %d fois, attendu 1 (cache)", n)
	}
	data := sent["data"].(map[string]any)
	android := sent["android"].(map[string]any)
	if sent["token"] != "jeton-montre" || data["id"] != "eabc" || data["time"] != "1792500000" || data["payload"] != `{"t":"done"}` || android["priority"] != "HIGH" {
		t.Fatalf("message envoyé : %+v", sent)
	}
	if _, ok := sent["notification"]; ok {
		t.Fatal("le message doit être de type data uniquement")
	}
	if err := fcm.Send(ctx, "perime", Push{ID: "e1"}); !errors.Is(err, errUnregistered) {
		t.Fatalf("jeton périmé : %v", err)
	}
}

func TestFCMCompteDeServiceInvalide(t *testing.T) {
	for _, creds := range []string{`pas du json`, `{"project_id":"p"}`, `{"project_id":"p","client_email":"e","private_key":"pas une clé"}`} {
		if _, err := NewFCM([]byte(creds)); err == nil {
			t.Fatalf("accepté : %s", creds)
		}
	}
}
