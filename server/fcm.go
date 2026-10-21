package main

import (
	"bytes"
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
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"
)

// Push : un événement à envoyer à une montre.
type Push struct {
	ID      string
	Time    int64
	Payload string // JSON lu par la montre (voir Payload)
	// Durée de vie chez Firebase si la montre est injoignable, 0 pour 24 h.
	TTL time.Duration
}

// Pusher envoie un événement à la montre dont le jeton FCM est token.
type Pusher interface {
	Send(ctx context.Context, token string, p Push) error
}

var (
	// errUnregistered : jeton FCM périmé (appli désinstallée, données effacées…), à oublier.
	errUnregistered = errors.New("jeton FCM inconnu de Firebase")
	errNoPush       = errors.New("push FCM non configuré sur le serveur (CC_FCM_CREDENTIALS)")
)

// noPusher : serveur lancé sans compte de service Firebase.
type noPusher struct{}

func (noPusher) Send(context.Context, string, Push) error { return errNoPush }

// FCM envoie des messages « data » prioritaires via l'API HTTP v1 de Firebase Cloud Messaging,
// authentifiée par un compte de service (JWT signé RS256, échangé contre un jeton OAuth 2).
type FCM struct {
	http        *http.Client
	projectID   string
	clientEmail string
	key         *rsa.PrivateKey
	tokenURI    string
	sendURL     string

	mu          sync.Mutex
	accessToken string
	expires     time.Time
}

type serviceAccount struct {
	ProjectID   string `json:"project_id"`
	ClientEmail string `json:"client_email"`
	PrivateKey  string `json:"private_key"`
	TokenURI    string `json:"token_uri"`
}

const fcmScope = "https://www.googleapis.com/auth/firebase.messaging"

// NewFCMFromFile lit le fichier JSON du compte de service
// (console Firebase › Paramètres du projet › Comptes de service › Générer une clé privée).
func NewFCMFromFile(path string) (*FCM, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return NewFCM(data)
}

func NewFCM(credentials []byte) (*FCM, error) {
	var sa serviceAccount
	if err := json.Unmarshal(credentials, &sa); err != nil {
		return nil, fmt.Errorf("compte de service illisible : %w", err)
	}
	if sa.ProjectID == "" || sa.ClientEmail == "" || sa.PrivateKey == "" {
		return nil, errors.New("compte de service incomplet (project_id, client_email, private_key)")
	}
	block, _ := pem.Decode([]byte(sa.PrivateKey))
	if block == nil {
		return nil, errors.New("clé privée du compte de service illisible")
	}
	parsed, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("clé privée : %w", err)
	}
	key, ok := parsed.(*rsa.PrivateKey)
	if !ok {
		return nil, errors.New("la clé du compte de service n'est pas une clé RSA")
	}
	if sa.TokenURI == "" {
		sa.TokenURI = "https://oauth2.googleapis.com/token"
	}
	return &FCM{
		http:        &http.Client{Timeout: 10 * time.Second},
		projectID:   sa.ProjectID,
		clientEmail: sa.ClientEmail,
		key:         key,
		tokenURI:    sa.TokenURI,
		sendURL:     "https://fcm.googleapis.com/v1/projects/" + url.PathEscape(sa.ProjectID) + "/messages:send",
	}, nil
}

func (f *FCM) Send(ctx context.Context, token string, p Push) error {
	access, err := f.token(ctx)
	if err != nil {
		return err
	}
	ttl := 24 * time.Hour
	if p.TTL > 0 {
		ttl = p.TTL
	}
	body, _ := json.Marshal(map[string]any{
		"message": map[string]any{
			"token": token,
			// Message « data » seul : c'est l'appli qui affiche la notification, selon les réglages.
			"data": map[string]string{
				"id":      p.ID,
				"time":    fmt.Sprint(p.Time),
				"payload": p.Payload,
			},
			// Priorité haute : réveille la montre en veille. Passé 24 h, l'événement n'a plus d'intérêt
			// (une question, dès que Claude Code ne l'attend plus).
			"android": map[string]any{"priority": "HIGH", "ttl": fmt.Sprintf("%ds", int64(ttl.Seconds()))},
		},
	})
	req, _ := http.NewRequestWithContext(ctx, "POST", f.sendURL, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+access)
	req.Header.Set("Content-Type", "application/json")
	resp, err := f.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusOK {
		return nil
	}
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	// https://firebase.google.com/docs/reference/fcm/rest/v1/ErrorCode
	if resp.StatusCode == http.StatusNotFound || strings.Contains(string(b), "UNREGISTERED") {
		return errUnregistered
	}
	if resp.StatusCode == http.StatusUnauthorized {
		f.mu.Lock()
		f.accessToken = ""
		f.mu.Unlock()
	}
	return fmt.Errorf("FCM HTTP %d : %s", resp.StatusCode, strings.TrimSpace(string(b)))
}

// token retourne un jeton d'accès OAuth 2 valide, renouvelé une minute avant expiration.
func (f *FCM) token(ctx context.Context) (string, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.accessToken != "" && time.Now().Before(f.expires.Add(-time.Minute)) {
		return f.accessToken, nil
	}
	assertion, err := f.assertion(time.Now())
	if err != nil {
		return "", err
	}
	form := url.Values{
		"grant_type": {"urn:ietf:params:oauth:grant-type:jwt-bearer"},
		"assertion":  {assertion},
	}
	req, _ := http.NewRequestWithContext(ctx, "POST", f.tokenURI, strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := f.http.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	var out struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int64  `json:"expires_in"`
	}
	if resp.StatusCode != http.StatusOK || json.NewDecoder(resp.Body).Decode(&out) != nil || out.AccessToken == "" {
		return "", fmt.Errorf("authentification Google refusée (HTTP %d)", resp.StatusCode)
	}
	f.accessToken = out.AccessToken
	f.expires = time.Now().Add(time.Duration(out.ExpiresIn) * time.Second)
	return f.accessToken, nil
}

// assertion : JWT signé avec la clé du compte de service (RFC 7523).
func (f *FCM) assertion(now time.Time) (string, error) {
	enc := base64.RawURLEncoding
	header, _ := json.Marshal(map[string]string{"alg": "RS256", "typ": "JWT"})
	claims, _ := json.Marshal(map[string]any{
		"iss":   f.clientEmail,
		"scope": fcmScope,
		"aud":   f.tokenURI,
		"iat":   now.Unix(),
		"exp":   now.Add(time.Hour).Unix(),
	})
	unsigned := enc.EncodeToString(header) + "." + enc.EncodeToString(claims)
	sum := sha256.Sum256([]byte(unsigned))
	sig, err := rsa.SignPKCS1v15(rand.Reader, f.key, crypto.SHA256, sum[:])
	if err != nil {
		return "", err
	}
	return unsigned + "." + enc.EncodeToString(sig), nil
}
