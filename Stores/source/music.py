"""Musique chiptune et bruitages de la vidéo, synthétisés (aucun échantillon externe : libre de droits).

Usage : python3 music.py cues.json sortie.wav
cues.json : {"duration": s, "cues": [{"t": s, "kind": "tap" | "pop" | ...}]}, écrit par render-video.mjs.
"""
import json
import math
import random
import struct
import sys
import wave

RATE = 44100


def square(f, t, duty=0.5):
    return 1.0 if (f * t) % 1.0 < duty else -1.0


def triangle(f, t):
    p = (f * t) % 1.0
    return 4 * p - 1 if p < 0.5 else 3 - 4 * p


def note(n):
    """Fréquence d'une note MIDI."""
    return 440.0 * 2 ** ((n - 69) / 12)


def add(buf, start, dur, gen, vol, attack=0.005, release=0.05):
    i0 = int(start * RATE)
    n = int(dur * RATE)
    for i in range(n):
        if i0 + i >= len(buf) or i0 + i < 0:
            continue
        t = i / RATE
        env = min(1.0, t / attack) if attack else 1.0
        env *= min(1.0, (dur - t) / release) if release else 1.0
        buf[i0 + i] += gen(t) * vol * max(0.0, env)


def sweep(f0, f1, dur, wave_fn=triangle):
    phase = [0.0]

    def gen(t):
        f = f0 + (f1 - f0) * (t / dur)
        phase[0] += f / RATE
        return wave_fn(1.0, phase[0])
    return gen


def main():
    cues = json.load(open(sys.argv[1]))
    duration = cues["duration"]
    buf = [0.0] * int(duration * RATE + RATE)
    rnd = random.Random(7)

    # --- Boucle : 120 bpm, C - Am - F - G, une mesure (2 s) par accord ---
    beat = 0.5
    chords = [(48, [60, 64, 67, 72]), (45, [57, 60, 64, 69]), (41, [53, 57, 60, 65]), (43, [55, 59, 62, 67])]
    bars = int(duration / (4 * beat)) + 1
    for b in range(bars):
        root, tones = chords[b % 4]
        t0 = b * 4 * beat
        if t0 >= duration - 1.0:
            break
        # Basse triangle sur les noires
        for q in range(4):
            f = note(root + (12 if q % 2 else 0))
            add(buf, t0 + q * beat, beat * 0.9, lambda t, f=f: triangle(f, t), 0.16, release=0.08)
        # Arpège carré (25 %) en croches, une octave au-dessus à partir de l'intro
        if b >= 1:
            pattern = [0, 1, 2, 3, 2, 1, 2, 3]
            for e, k in enumerate(pattern):
                f = note(tones[k] + 12)
                add(buf, t0 + e * beat / 2, beat / 2 * 0.8, lambda t, f=f: square(f, t, 0.25), 0.035, release=0.06)
        # Grosse caisse (sinus qui descend) et charleston (bruit) sur les contretemps
        for q in (0, 2):
            add(buf, t0 + q * beat, 0.18, sweep(140, 40, 0.18, lambda f, p: math.sin(2 * math.pi * p)), 0.35, release=0.1)
        if b >= 1:
            for q in range(4):
                add(buf, t0 + q * beat + beat / 2, 0.04, lambda t: rnd.uniform(-1, 1), 0.05, release=0.03)

    # --- Bruitages ---
    def blip(t, n, d=0.07, vol=0.12, duty=0.5):
        add(buf, t, d, lambda x, f=note(n): square(f, x, duty), vol, release=0.03)

    for c in cues["cues"]:
        t, kind = c["t"], c["kind"]
        if kind == "tap":
            blip(t, 88, 0.05, 0.1)
        elif kind == "pop":
            add(buf, t, 0.18, sweep(300, 1200, 0.18, square), 0.1)
        elif kind == "wave":
            blip(t, 79); blip(t + 0.09, 84)
        elif kind == "done":
            for i, n in enumerate([72, 76, 79, 84]):
                blip(t + i * 0.08, n, 0.09, 0.11, 0.25)
        elif kind == "levelup":
            for i, n in enumerate([72, 76, 79, 84, 88, 91, 96]):
                blip(t + i * 0.06, n, 0.08, 0.09, 0.25)
        elif kind == "switch":
            add(buf, t, 0.16, sweep(400, 1400, 0.16, square), 0.08)
        elif kind == "blip":
            blip(t, rnd.choice([79, 81, 84, 86, 88]), 0.08, 0.1, 0.25)
        elif kind == "drop":
            add(buf, t, 0.22, sweep(1400, 350, 0.22, square), 0.07)
        elif kind == "sweep":
            add(buf, t, 0.6, lambda x: rnd.uniform(-1, 1) * (x / 0.6), 0.08, release=0.1)
        elif kind == "outro":
            for n in (60, 64, 67, 72):
                add(buf, t, 2.2, lambda x, f=note(n): square(f, x, 0.25), 0.04, release=1.2)

    # Fondu final et normalisation douce
    fade = int(1.2 * RATE)
    end = int(duration * RATE)
    for i in range(fade):
        j = end - fade + i
        if 0 <= j < len(buf):
            buf[j] *= 1 - i / fade
    del buf[end:]
    peak = max(1e-6, max(abs(v) for v in buf))
    gain = 0.85 / peak
    with wave.open(sys.argv[2], "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v * gain)) * 32767)) for v in buf))


if __name__ == "__main__":
    main()
