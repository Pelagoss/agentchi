package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// Une montre. Elle gère ses codes avec son jeton et reçoit ses événements par FCM.
type Device struct {
	ID        string    `json:"id"`
	TokenHash string    `json:"token_hash"`
	Name      string    `json:"name"`
	Created   time.Time `json:"created"`
	// Jeton d'enregistrement Firebase Cloud Messaging de la montre.
	PushToken string `json:"push_token,omitempty"`
	// Répondre depuis la montre : les hooks /ask attendent sa réponse au lieu de rendre la main tout de suite.
	Answers bool `json:"answers,omitempty"`
}

// Un code d'appairage, saisi sur un ordinateur ou dans un environnement cloud.
// Il sert ensuite de jeton au hook : un code par machine, révocable depuis la montre.
type Code struct {
	ID          string     `json:"id"`
	Hash        string     `json:"hash"`
	DeviceID    string     `json:"device_id"`
	Label       string     `json:"label"`
	Created     time.Time  `json:"created"`
	LastUsed    *time.Time `json:"last_used,omitempty"`
	LastProject string     `json:"last_project,omitempty"`
}

var (
	errNotFound    = errors.New("introuvable")
	errTooManyCode = errors.New("trop de codes pour cette montre")
)

const maxCodesPerDevice = 50

type state struct {
	Devices map[string]*Device `json:"devices"`
	Codes   map[string]*Code   `json:"codes"` // clé : Code.ID
}

// Store garde tout en mémoire et l'écrit dans un fichier JSON à chaque modification.
// C'est largement suffisant pour quelques dizaines de montres.
type Store struct {
	mu   sync.Mutex
	path string
	st   state
	// Index : empreinte du jeton → id
	deviceByToken map[string]string
	codeByHash    map[string]string
}

func OpenStore(path string) (*Store, error) {
	s := &Store{
		path:          path,
		st:            state{Devices: map[string]*Device{}, Codes: map[string]*Code{}},
		deviceByToken: map[string]string{},
		codeByHash:    map[string]string{},
	}
	if path != "" {
		data, err := os.ReadFile(path)
		switch {
		case err == nil:
			if err := json.Unmarshal(data, &s.st); err != nil {
				return nil, err
			}
		case !errors.Is(err, os.ErrNotExist):
			return nil, err
		}
	}
	if s.st.Devices == nil {
		s.st.Devices = map[string]*Device{}
	}
	if s.st.Codes == nil {
		s.st.Codes = map[string]*Code{}
	}
	for id, d := range s.st.Devices {
		s.deviceByToken[d.TokenHash] = id
	}
	for id, c := range s.st.Codes {
		s.codeByHash[c.Hash] = id
	}
	return s, nil
}

// save écrit l'état de façon atomique. À appeler avec s.mu verrouillé.
func (s *Store) save() error {
	if s.path == "" {
		return nil
	}
	data, err := json.Marshal(s.st)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}

func (s *Store) CreateDevice(name, pushToken string) (*Device, string, error) {
	token := "ccd_" + randomToken(32)
	d := &Device{ID: "w" + randomToken(15), TokenHash: hashToken(token), Name: name, Created: time.Now().UTC(), PushToken: pushToken}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.st.Devices[d.ID] = d
	s.deviceByToken[d.TokenHash] = d.ID
	return d, token, s.save()
}

func (s *Store) DeviceCount() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.st.Devices)
}

// DeviceByToken retourne une copie de la montre authentifiée par token.
func (s *Store) DeviceByToken(token string) (Device, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	id, ok := s.deviceByToken[hashToken(token)]
	if !ok {
		return Device{}, false
	}
	return *s.st.Devices[id], true
}

// PushToken retourne le jeton FCM de la montre, vide si elle n'en a pas.
func (s *Store) PushToken(deviceID string) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	if d, ok := s.st.Devices[deviceID]; ok {
		return d.PushToken
	}
	return ""
}

// SetPushToken enregistre le jeton FCM (Firebase le renouvelle de temps en temps).
func (s *Store) SetPushToken(deviceID, pushToken string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	d, ok := s.st.Devices[deviceID]
	if !ok {
		return errNotFound
	}
	if d.PushToken == pushToken {
		return nil
	}
	d.PushToken = pushToken
	return s.save()
}

// ForgetPushToken oublie un jeton refusé par Firebase, s'il n'a pas été remplacé entre-temps.
func (s *Store) ForgetPushToken(deviceID, pushToken string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	d, ok := s.st.Devices[deviceID]
	if !ok || d.PushToken != pushToken {
		return nil
	}
	d.PushToken = ""
	return s.save()
}

// Answers indique si la montre répond aux questions de Claude (voir ask.go).
func (s *Store) Answers(deviceID string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	d, ok := s.st.Devices[deviceID]
	return ok && d.Answers && d.PushToken != ""
}

func (s *Store) SetAnswers(deviceID string, on bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	d, ok := s.st.Devices[deviceID]
	if !ok {
		return errNotFound
	}
	if d.Answers == on {
		return nil
	}
	d.Answers = on
	return s.save()
}

func (s *Store) DeleteDevice(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	d, ok := s.st.Devices[id]
	if !ok {
		return errNotFound
	}
	delete(s.deviceByToken, d.TokenHash)
	delete(s.st.Devices, id)
	for cid, c := range s.st.Codes {
		if c.DeviceID == id {
			delete(s.codeByHash, c.Hash)
			delete(s.st.Codes, cid)
		}
	}
	return s.save()
}

// CreateCode retourne le code en clair : il n'est conservé que sous forme d'empreinte.
func (s *Store) CreateCode(deviceID, label string) (*Code, string, error) {
	plain := newPairingCode()
	c := &Code{ID: "c" + randomToken(11), Hash: hashToken(normalizeCode(plain)), DeviceID: deviceID, Label: label, Created: time.Now().UTC()}
	s.mu.Lock()
	defer s.mu.Unlock()
	n := 0
	for _, other := range s.st.Codes {
		if other.DeviceID == deviceID {
			n++
		}
	}
	if n >= maxCodesPerDevice {
		return nil, "", errTooManyCode
	}
	s.st.Codes[c.ID] = c
	s.codeByHash[c.Hash] = c.ID
	return c, plain, s.save()
}

func (s *Store) Codes(deviceID string) []Code {
	s.mu.Lock()
	defer s.mu.Unlock()
	var out []Code
	for _, c := range s.st.Codes {
		if c.DeviceID == deviceID {
			out = append(out, *c)
		}
	}
	return out
}

func (s *Store) DeleteCode(deviceID, codeID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, ok := s.st.Codes[codeID]
	if !ok || c.DeviceID != deviceID {
		return errNotFound
	}
	delete(s.codeByHash, c.Hash)
	delete(s.st.Codes, codeID)
	return s.save()
}

// UseCode retrouve le code saisi dans un hook et note son utilisation.
func (s *Store) UseCode(plain, project string) (Code, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	id, ok := s.codeByHash[hashToken(normalizeCode(plain))]
	if !ok {
		return Code{}, false
	}
	c := s.st.Codes[id]
	now := time.Now().UTC()
	c.LastUsed = &now
	if project != "" {
		c.LastProject = project
	}
	return *c, true
}

// --- Jetons et codes ---

// Sans I, L, O, 0 ni 1 : rien à confondre en recopiant depuis la montre.
const codeAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

// newPairingCode : 12 caractères (≈ 59 bits), affichés « XXXX-XXXX-XXXX ».
func newPairingCode() string {
	raw := randomChars(codeAlphabet, 12)
	return raw[0:4] + "-" + raw[4:8] + "-" + raw[8:12]
}

func normalizeCode(code string) string {
	return strings.Map(func(r rune) rune {
		switch {
		case r == '-' || r == ' ' || r == '\t':
			return -1
		case r >= 'a' && r <= 'z':
			return r - 'a' + 'A'
		}
		return r
	}, strings.TrimSpace(code))
}

func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

const tokenAlphabet = "abcdefghijkmnpqrstuvwxyz23456789"

func randomToken(n int) string { return randomChars(tokenAlphabet, n) }

// randomChars tire n caractères de alphabet sans biais (rejet des octets hors plage).
func randomChars(alphabet string, n int) string {
	limit := 256 - 256%len(alphabet)
	out := make([]byte, 0, n)
	buf := make([]byte, n)
	for len(out) < n {
		if _, err := rand.Read(buf); err != nil {
			panic(err)
		}
		for _, v := range buf {
			if int(v) < limit && len(out) < n {
				out = append(out, alphabet[int(v)%len(alphabet)])
			}
		}
	}
	return string(out)
}
