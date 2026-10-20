package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Une montre. Elle lit ses événements avec son jeton.
type Device struct {
	ID        string    `json:"id"`
	TokenHash string    `json:"token_hash"`
	Name      string    `json:"name"`
	Created   time.Time `json:"created"`
	Seq       int64     `json:"seq"`
	Events    []Event   `json:"events"`
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

// Événement prêt pour la montre, au format de l'API d'abonnement de ntfy.
type Event struct {
	ID      string `json:"id"`
	Time    int64  `json:"time"`
	Message string `json:"message"`
}

var (
	errNotFound    = errors.New("introuvable")
	errTooManyCode = errors.New("trop de codes pour cette montre")
)

const (
	maxEventsPerDevice = 200
	eventRetention     = 48 * time.Hour
	maxCodesPerDevice  = 50
)

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
	subs          map[string]map[chan Event]struct{}
}

func OpenStore(path string) (*Store, error) {
	s := &Store{
		path:          path,
		st:            state{Devices: map[string]*Device{}, Codes: map[string]*Code{}},
		deviceByToken: map[string]string{},
		codeByHash:    map[string]string{},
		subs:          map[string]map[chan Event]struct{}{},
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

func (s *Store) CreateDevice(name string) (*Device, string, error) {
	token := "ccd_" + randomToken(32)
	d := &Device{ID: "w" + randomToken(15), TokenHash: hashToken(token), Name: name, Created: time.Now().UTC()}
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

func (s *Store) DeviceByToken(token string) (*Device, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	id, ok := s.deviceByToken[hashToken(token)]
	if !ok {
		return nil, false
	}
	return s.st.Devices[id], true
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
	for ch := range s.subs[id] {
		close(ch)
	}
	delete(s.subs, id)
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

// Publish ajoute un événement à la file de la montre et le pousse aux connexions ouvertes.
func (s *Store) Publish(deviceID, message string) (Event, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	d, ok := s.st.Devices[deviceID]
	if !ok {
		return Event{}, errNotFound
	}
	d.Seq++
	now := time.Now()
	// Suffixe aléatoire : la montre déduplique par id, y compris après un réenregistrement.
	ev := Event{ID: "e" + strconv.FormatInt(d.Seq, 36) + randomToken(6), Time: now.Unix(), Message: message}
	d.Events = append(d.Events, ev)
	d.Events = prune(d.Events, now)
	for ch := range s.subs[deviceID] {
		select {
		case ch <- ev:
		default: // abonné trop lent : il rattrapera au prochain sondage
		}
	}
	return ev, s.save()
}

func prune(events []Event, now time.Time) []Event {
	cutoff := now.Add(-eventRetention).Unix()
	i := 0
	for i < len(events) && (events[i].Time < cutoff || len(events)-i > maxEventsPerDevice) {
		i++
	}
	return events[i:]
}

// Since retourne les événements postérieurs à since : un id d'événement, un horodatage Unix ou « all ».
// Un id inconnu (événement expiré) renvoie tout ce qui reste, pour ne rien perdre.
func (s *Store) Since(deviceID, since string) []Event {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.sinceLocked(deviceID, since)
}

func (s *Store) sinceLocked(deviceID, since string) []Event {
	d, ok := s.st.Devices[deviceID]
	if !ok {
		return nil
	}
	events := d.Events
	switch {
	case since == "" || since == "all":
		// tout
	case isDigits(since):
		ts, _ := strconv.ParseInt(since, 10, 64)
		i := 0
		for i < len(events) && events[i].Time <= ts {
			i++
		}
		events = events[i:]
	default:
		for i, ev := range events {
			if ev.ID == since {
				events = events[i+1:]
				break
			}
		}
	}
	return append([]Event(nil), events...)
}

// Subscribe retourne les événements en attente et un canal pour les suivants, sans trou entre les deux.
func (s *Store) Subscribe(deviceID, since string) ([]Event, chan Event, func()) {
	ch := make(chan Event, 32)
	s.mu.Lock()
	defer s.mu.Unlock()
	backlog := s.sinceLocked(deviceID, since)
	if s.subs[deviceID] == nil {
		s.subs[deviceID] = map[chan Event]struct{}{}
	}
	s.subs[deviceID][ch] = struct{}{}
	cancel := func() {
		s.mu.Lock()
		defer s.mu.Unlock()
		if _, ok := s.subs[deviceID][ch]; ok {
			delete(s.subs[deviceID], ch)
			close(ch)
		}
	}
	return backlog, ch, cancel
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

func isDigits(s string) bool {
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return s != ""
}
