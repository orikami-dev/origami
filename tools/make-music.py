#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make-music.py : musiques de thème d'Origami (KAMI-WORKS, pour BOSS).

Synthèse pure (numpy), sans aucun échantillon. Chaque morceau est écrit (mélodie, accords,
arrangement par sections) puis rendu dans un tampon CIRCULAIRE d'un nombre entier de secondes :
les queues de notes, les réverbérations, les échos et le traitement de mastering rebouclent au
début, si bien que la boucle n'a ni blanc ni clic au raccord (lecture par MusicLoop.java, qui
recoupe le PCM décodé à la seconde près).

Ce script remplace la partie « musiques » de make-sounds.py (qui reste l'outil des bruitages).
Attention : lancer make-sounds.py sans argument réécrit encore les anciennes musiques ; relancer
ensuite ce script.

Usage :
  python3 tools/make-music.py                    # les huit thèmes -> app/assets/music/<thème>.ogg
  python3 tools/make-music.py codec neon         # quelques thèmes seulement
  python3 tools/make-music.py --previews         # + extraits mp3 (~40 s, raccord inclus)
                                                 #   dans docs/previews/audio/<thème>.mp3
  python3 tools/make-music.py --check            # analyse des .ogg existants (sans rendu)
  options : --out DIR (dossier des .ogg), --jobs N (rendus en parallèle)

Format (identique aux anciennes musiques) : Ogg Vorbis, 44,1 kHz, stéréo, qualité Vorbis 5.
Niveaux : -23 LUFS intégrés pour tous les thèmes (moyenne des anciennes musiques, pour garder
l'équilibre avec les bruitages), crête plafonnée à -6 dBFS.
"""
import os, sys, subprocess, json
import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(HERE)
ROOT = os.environ.get("ORIGAMI_ASSETS", os.path.join(PROJ, "app", "assets"))
PREVIEW_DIR = os.path.join(PROJ, "docs", "previews", "audio")
THEMES = ["codec", "arctique", "ecarlate", "sakura", "ambre", "neon", "jade", "argent"]
QUALITY = 5            # qualité Vorbis des anciennes musiques (make-sounds.py : encode(..., 2, 5))
TARGET_LUFS = -23.0
CEIL_DB = -6.0


# =====================================================================================
#  Outils de base
# =====================================================================================
def mf(m): return 440.0 * 2 ** ((m - 69) / 12)
def T(n): return np.arange(n) / SR
def ns(d): return max(1, int(round(d * SR)))

_NOTE = dict(c=0, d=2, e=4, f=5, g=7, a=9, b=11)
def nm(s):
    """ "c#4", "bb3", "eb5", "cb6" -> numéro midi """
    s = s.lower(); p = _NOTE[s[0]]; i = 1
    while i < len(s) and s[i] in "#b":
        p += 1 if s[i] == "#" else -1; i += 1
    return 12 * (int(s[i:]) + 1) + p

def mel(s):
    """Mélodie compacte : "e5:1 g5:.5 r:1 c4+e4:2 a4!:1 d5?:.5" ; '|' ignoré (repère de mesure).
    ! = accent, ? = note fantôme. Renvoie [(temps, durée, midi, vélocité)], longueur en temps."""
    out = []; pos = 0.
    for tok in s.replace("|", " ").split():
        p, d = tok.split(":")
        if "/" in d: a, b = d.split("/"); d = float(a) / float(b)
        else: d = float(d)
        if p not in ("r", "-"):
            v = 1.
            if p.endswith("!"): v, p = 1.22, p[:-1]
            elif p.endswith("?"): v, p = .6, p[:-1]
            for q in p.split("+"): out.append((pos, d, nm(q), v))
        pos += d
    return out, pos

def shift(notes, beats=0., semis=0, vel=1.):
    return [(p + beats, d, m + semis, v * vel) for p, d, m, v in notes]

def fft_filter(x, lo=None, hi=None, n=2, bells=(), shelves=()):
    """Filtre zéro-phase par FFT (circulaire : sans raccord sur une boucle).
    bells : (fc, gain dB, largeur en octaves) ; shelves : ('lo'|'hi', fc, gain dB)."""
    x = np.asarray(x, float); N = x.shape[-1]
    X = np.fft.rfft(x, axis=-1); f = np.fft.rfftfreq(N, 1 / SR); m = np.ones_like(f)
    fz = np.maximum(f, 1e-3)
    if lo: m *= 1 / np.sqrt(1 + (lo / fz) ** (2 * n))
    if hi: m *= 1 / np.sqrt(1 + (fz / hi) ** (2 * n))
    db = np.zeros_like(f)
    for fc, g, w in bells: db += g * np.exp(-.5 * (np.log2(np.maximum(f, 1) / fc) / w) ** 2)
    for kind, fc, g in shelves:
        db += g / (1 + (fc / fz) ** 2) if kind == "hi" else g / (1 + (fz / fc) ** 2)
    if bells or shelves: m *= 10 ** (db / 20)
    return np.fft.irfft(X * m, N, axis=-1)

def endfade(y, s=.01):
    k = min(ns(s), y.shape[-1] // 3)
    if k > 1: y[..., -k:] *= np.cos(np.linspace(0, np.pi / 2, k)) ** 2
    return y

def attack(y, s=.002):
    k = min(ns(s), y.shape[-1] // 3)
    if k > 1: y[..., :k] *= np.sin(np.linspace(0, np.pi / 2, k)) ** 2
    return y

def adsr(dur, rel, a=.005, d=.3, s=.8, curve=1.):
    """Enveloppe : attaque a, décroissance d vers s, tenue jusqu'à dur, relâche rel (~-60 dB)."""
    n = ns(dur + rel); t = T(n)
    e = np.where(t < a, np.sin(np.clip(t / max(a, 1e-4), 0, 1) * np.pi / 2) ** 2,
                 s + (1 - s) * np.exp(-np.maximum(0, t - a) / max(d, 1e-4)))
    if curve != 1: e = e ** curve
    e = e * np.where(t > dur, np.exp(-(t - dur) * 6.9 / max(rel, 1e-3)), 1.)
    return endfade(e, .005)

def clfo(L, cycles, ph=0.):
    """LFO à nombre entier de cycles sur la boucle (donc sans raccord)."""
    return np.sin(2 * np.pi * cycles * np.arange(L) / L + ph)

def circ_smooth(v, sec):
    """Lissage gaussien circulaire."""
    N = v.shape[-1]; f = np.fft.rfftfreq(N, 1 / SR); s = sec
    return np.fft.irfft(np.fft.rfft(v, axis=-1) * np.exp(-.5 * (2 * np.pi * f * s) ** 2), N, axis=-1)


# =====================================================================================
#  Instruments (chacun renvoie un signal mono ou stéréo (2, n), relâche comprise)
# =====================================================================================
def _amp(wave, k, duty=.5):
    if wave == "saw": return 1 / k
    if wave == "square": return (k % 2) / k
    if wave == "tri": return (k % 2) / k ** 2 * (1 if (k // 2) % 2 == 0 else -1)
    if wave == "pulse": return abs(np.sin(np.pi * k * duty)) / k
    if wave == "sine": return 1. if k == 1 else 0.
    if wave == "soft": return 1 / k ** 2          # entre sinus et dent de scie : flûte de synthé
    if wave == "organ": return {1: 1, 2: .5, 3: .25, 4: .3, 6: .12, 8: .1}.get(k, 0)
    raise ValueError(wave)

def synth(f, dur, r, vel=1., wave="saw", fc=3000., fenv=0., ftau=.1, fswell=0., q=0., a=.005, d=.3, s=.8,
          rel=.15, vib=0., vrate=5.2, vdel=.3, voices=1, det=0., glide=None, gtau=.035, stereo=False,
          duty=.5, kmax=80, keytrack=0., velfc=.5, drift=0.):
    """Synthé additif à bande limitée : forme d'onde, filtre passe-bas (12 dB/oct) avec enveloppe,
    résonance, unisson désaccordé, vibrato différé, portamento."""
    env = adsr(dur, rel, a, d, s); n = len(env); t = T(n)
    fcb = fc * (f / 261.6) ** keytrack * (1 - velfc + velfc * vel)
    dyn = fenv != 0 or fswell > 0
    fct = fcb * (1 + fenv * np.exp(-t / ftau)) if fenv else np.full(n, fcb)
    if fswell > 0: fct = fct * (.25 + .75 * (1 - np.exp(-t / fswell)))
    fct = np.minimum(fct, 18000)
    fmax = fct.max()
    chans = []
    for ch in range(2 if stereo else 1):
        y = np.zeros(n)
        for v in range(voices):
            dc = det * ((v - (voices - 1) / 2) / ((voices - 1) / 2)) if voices > 1 else 0.
            dc += r.normal(0, det * .15 + .5) if (voices > 1 or stereo) else 0
            fv = f * 2 ** (dc / 1200)
            fi = np.full(n, fv)
            if vib: fi = fi * (1 + vib * np.sin(2 * np.pi * vrate * t + r.uniform(0, 6.28)) * np.clip(t / vdel, 0, 1) ** 2)
            if drift: fi = fi * (1 + drift * np.sin(2 * np.pi * r.uniform(.15, .4) * t + r.uniform(0, 6.28)))
            if glide: fi = fi * (glide / f) ** np.exp(-t / gtau)
            ph = 2 * np.pi * np.cumsum(fi) / SR + r.uniform(0, 2 * np.pi)
            s1 = np.sin(ph); c2 = 2 * np.cos(ph); sp = np.zeros(n); sk = s1
            for k in range(1, kmax + 1):
                fk = fv * k
                if fk > min(SR / 2.3, 6 * fmax): break
                ak = _amp(wave, k, duty)
                if ak:
                    if dyn:
                        g = ak / np.sqrt(1 + (fk / fct) ** 4)
                        if q: g = g + abs(ak) * q * np.exp(-.5 * ((fk - fct) / (.18 * fct)) ** 2)
                    else:
                        g = ak / np.sqrt(1 + (fk / fcb) ** 4)
                        if q: g += abs(ak) * q * np.exp(-.5 * ((fk - fcb) / (.18 * fcb)) ** 2)
                    y += g * sk
                sk, sp = c2 * sk - sp, sk
        chans.append(y)
    y = np.stack(chans) if stereo else chans[0]
    y = y * env
    pk = np.abs(y).max() + 1e-9
    return y / pk * vel

def ks(f, dur, r, hl=.4, bright=1., bend=None, pick=.0, damp=None, body=None):
    """Corde pincée (Karplus-Strong accordé par ré-échantillonnage). bend(t) -> rapport de hauteur.
    damp : durée de tenue après laquelle la corde est étouffée."""
    N = max(int(SR / f - .5), 2); fa = SR / (N + .5)
    m = ns(dur); tt = T(m)
    rate = (f / fa) * (bend(tt) if bend is not None else np.ones(m))
    pos = np.concatenate([[0.], np.cumsum(rate)[:-1]])
    n = int(pos[-1]) + N + 4
    dec = .5 ** (1 / (f * hl))
    ex = r.uniform(-1, 1, N)
    if bright < 1:
        for _ in range(int((1 - bright) * 12)): ex = np.convolve(ex, [.25, .5, .25], "same")
    ex -= ex.mean()
    y = np.zeros(n + 1); y[1:N + 1] = ex
    for s in range(N + 1, n + 1, N):
        e = min(s + N, n + 1); k = e - s
        y[s:e] = dec * .5 * (y[s - N:s - N + k] + y[s - N - 1:s - N - 1 + k])
    o = np.interp(pos, np.arange(n), y[1:])
    o /= np.abs(o).max() + 1e-9
    if pick: o = o + pick * fft_filter(r.standard_normal(m), 1500, 7000) * np.exp(-tt / .005)
    if damp is not None: o = o * np.where(tt > damp, np.exp(-(tt - damp) / .05), 1.)
    o = attack(o, .001)
    return endfade(o, .02)

def modal(f, dur, r, vel=1., parts=((1, 1, 1),), a=.001, click=0., trem=0., trate=5., beat=0.):
    """Percussion accordée : partiels (rapport, amplitude, durée de décroissance en s)."""
    n = ns(dur); t = T(n); y = np.zeros(n)
    for ra, am, tau in parts:
        fk = f * ra
        if fk > SR / 2.3: continue
        if beat:
            y += am * .5 * (np.sin(2 * np.pi * fk * t + r.uniform(0, 6.28)) +
                            np.sin(2 * np.pi * (fk + beat * ra) * t + r.uniform(0, 6.28))) * np.exp(-t / tau)
        else:
            y += am * np.sin(2 * np.pi * fk * t + r.uniform(0, 6.28) * 0) * np.exp(-t / tau)
    if click: y += click * fft_filter(r.standard_normal(n), 2000, 9000) * np.exp(-t / .002)
    if trem: y *= 1 - trem * (.5 - .5 * np.cos(2 * np.pi * trate * t))
    y = attack(y, a)
    y = endfade(y, .03)
    return y / (np.abs(y).max() + 1e-9) * vel

def celesta(f, dur, r, vel=1.):
    k = (523 / f) ** .5
    return modal(f, dur, r, vel, ((1, 1, 1.6 * k), (2, .12, .5 * k), (4.02, .22, .3 * k), (10.1, .04, .06)), .0015, .08)
def glock(f, dur, r, vel=1.):
    return modal(f, dur, r, vel, ((1, 1, 1.8), (2.76, .28, .45), (5.4, .1, .2), (8.93, .04, .1)), .001, .12)
def mbox(f, dur, r, vel=1.):
    return modal(f, dur, r, vel, ((1, 1, 1.3), (2, .18, .5), (3, .06, .3), (4.01, .07, .18), (5.4, .03, .1)), .001, .05)
def vibes(f, dur, r, vel=1.):
    return modal(f, dur, r, vel, ((1, 1, 2.2), (4, .3, .5), (10, .06, .12)), .002, .03, trem=.25, trate=4.5)
def bowl(f, dur, r, vel=1.):
    return modal(f, dur, r, vel, ((1, 1, 9), (2.71, .45, 5), (5.15, .2, 2.5), (8.4, .08, 1.2)), .03, 0, beat=.9)
def kane(f, dur, r, vel=1.):
    return modal(f, dur, r, vel, ((1, 1, dur * .4), (2.4, .5, dur * .2), (3.9, .3, dur * .12), (5.6, .15, dur * .08)), .001, .1)
def chime(f, dur, r, vel=1.):
    return modal(f, dur, r, vel, ((1, 1, 2.5), (2.76, .35, .8), (5.4, .15, .4)), .001, .02, beat=1.3)

def piano(f, dur, r, vel=1., rel=.25, bright=1.):
    """Piano : partiels légèrement inharmoniques, trois cordes désaccordées, décroissance en deux
    temps, marteau dont la dureté suit la vélocité, étouffoir à la fin de la note."""
    tot = dur + rel; n = ns(tot); t = T(n)
    B = 2e-4 * (f / 261.6) ** .6
    tau1 = float(np.clip(3.2 * (261.6 / f) ** .65, .5, 9))
    hb = (900 + 2600 * vel) * bright
    y = np.zeros(n)
    for k in range(1, 40):
        fk = f * k * np.sqrt(1 + B * k * k)
        if fk > 9000: break
        ak = (1 / k ** .9) * np.exp(-(k - 1) * f / hb)
        if ak < 2e-3: break
        tk = tau1 / (1 + .3 * (k - 1) * (f / 261.6) ** .4)
        ek = .55 * np.exp(-t / (.22 * tk)) + .45 * np.exp(-t / tk)
        for dc in ((-.7, .7) if f > 180 else (0.,)):
            y += ak * ek * np.sin(2 * np.pi * fk * 2 ** (dc / 1200) * t + r.uniform(0, 6.28)) / (2 if f > 180 else 1)
    y += .03 * vel * fft_filter(r.standard_normal(n), 60, 1500) * np.exp(-t / .015)
    y *= np.where(t > dur, np.exp(-(t - dur) / .07), 1.)
    y = attack(y, .0015); y = endfade(y, .02)
    return y / (np.abs(y).max() + 1e-9) * vel

def ep(f, dur, r, vel=1., rel=.2, bark=1.):
    """Piano électrique type « lame » (FM)."""
    n = ns(dur + rel); t = T(n)
    tau = float(np.clip(2.2 * (261.6 / f) ** .5, .5, 4))
    idx = (.4 + 1.6 * vel * bark) * np.exp(-t / .35) + .15
    y = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t))
    y += .12 * vel * np.sin(2 * np.pi * f * 7.1 * t) * np.exp(-t / .025) * (f * 7.1 < 16000)
    y *= np.exp(-t / tau) * np.where(t > dur, np.exp(-(t - dur) / .06), 1.)
    y = attack(y, .002); y = endfade(y, .02)
    return y / (np.abs(y).max() + 1e-9) * vel

def upright(f, dur, r, vel=1.):
    """Contrebasse jouée aux doigts."""
    n = ns(dur + .08); t = T(n)
    fi = f * (1 + .015 * np.exp(-t / .03)); ph = 2 * np.pi * np.cumsum(fi) / SR
    y = np.zeros(n)
    for k in range(1, 14):
        if f * k > 5000: break
        y += (1 / k ** 1.25) * np.exp(-t * (k - 1) * 2.2) * np.sin(k * ph)
    y *= np.exp(-t / 1.4) * np.where(t > dur, np.exp(-(t - dur) / .03), 1.)
    y += .25 * fft_filter(r.standard_normal(n), 80, 900) * np.exp(-t / .012)
    y = attack(y, .004); y = endfade(y, .01)
    return y / (np.abs(y).max() + 1e-9) * vel

def wind(f, dur, r, vel=1., parts=((1, 1), (2, .22), (3, .07), (4, .03)), breath=.22, band=(.8, 3.2),
         air=.04, vib=.008, vrate=5., vdel=.45, a=.07, rel=.18, scoop=0., stau=.08, chiff=.25, buzz=0., swell=.15):
    """Instrument à vent soufflé (flûtes) : son + souffle accordé + attaque soufflée."""
    n = ns(dur + rel); t = T(n)
    fi = f * (1 + scoop * np.exp(-t / stau))
    if vib: fi = fi * (1 + vib * np.sin(2 * np.pi * vrate * t + r.uniform(0, 6)) * np.clip((t - .1) / vdel, 0, 1) ** 1.5)
    ph = 2 * np.pi * np.cumsum(fi) / SR
    tone = sum(am * np.sin(k * ph) for k, am in parts if f * k < 16000)
    if buzz: tone = tone + buzz * fft_filter(np.tanh(3 * tone) - tone * .8, 1500, 8000)
    br = fft_filter(r.standard_normal(n), f * band[0], f * band[1]); br /= np.abs(br).max() + 1e-9
    hiss = fft_filter(r.standard_normal(n), 2500, 8000); hiss /= np.abs(hiss).max() + 1e-9
    env = adsr(dur, rel, a, .3, 1., 1.3)
    sw = 1 + swell * np.sin(np.clip(t / max(dur, .1), 0, 1) * np.pi)
    y = (tone * sw + breath * br * (1 + 2.5 * np.exp(-t / .06)) + air * hiss) * env
    y += chiff * fft_filter(r.standard_normal(n), 1200, 7000) * np.exp(-t / .02) * np.minimum(1, t / .004) * .3
    y = endfade(y, .02)
    return y / (np.abs(y).max() + 1e-9) * vel

def flute(f, dur, r, vel=1., **k): return wind(f, dur, r, vel, **k)
def shaku(f, dur, r, vel=1., scoop=True):
    return wind(f, dur, r, vel, parts=((1, 1), (2, .3), (3, .1), (4, .04)), breath=.38, band=(.8, 3.5), air=.06,
                vib=.012, vrate=5.2, vdel=dur * .55, a=.11, rel=.25, scoop=(2 ** (-1 / 12) - 1) if scoop else 0,
                stau=.09, chiff=.35, swell=.2)
def xiao(f, dur, r, vel=1.):
    return wind(f, dur, r, vel, parts=((1, 1), (2, .12), (3, .05)), breath=.45, band=(.7, 2.5), air=.08,
                vib=.007, vrate=4.6, vdel=.5, a=.14, rel=.3, chiff=.2, swell=.25)
def dizi(f, dur, r, vel=1.):
    return wind(f, dur, r, vel, parts=((1, 1), (2, .35), (3, .18), (4, .08), (5, .04)), breath=.2, band=(.9, 3),
                air=.05, vib=.011, vrate=5.6, vdel=.35, a=.05, rel=.15, chiff=.4, buzz=.22, swell=.12)

def horn(f, dur, r, vel=1., kind="harmon", rel=.12):
    """Cuivre / anche additif : la brillance monte avec le souffle (enveloppe^k)."""
    n = ns(dur + rel); t = T(n)
    a = .035 if kind == "harmon" else .05
    env = adsr(dur, rel, a, .25, .82)
    fi = f * (1 - .012 * np.exp(-t / .04)) * (1 + .006 * np.sin(2 * np.pi * 5.3 * t) * np.clip((t - .25) / .4, 0, 1))
    ph = 2 * np.pi * np.cumsum(fi) / SR
    y = np.zeros(n)
    for k in range(1, 30):
        fk = f * k
        if fk > 10000: break
        if kind == "harmon":
            base = np.exp(-.5 * (np.log2(fk / 1700) / .8) ** 2) + .2 / k; ex = 1 + .22 * (k - 1)
        elif kind == "clarinet":
            base = (1. if k % 2 else .16) / k ** .7 / np.sqrt(1 + (fk / 2600) ** 4); ex = 1 + .1 * (k - 1)
        else:  # anche douce (pupitre de saxophones)
            base = 1 / k ** 1.1 / np.sqrt(1 + (fk / 1800) ** 4); ex = 1 + .12 * (k - 1)
        y += base * env ** ex * np.sin(k * ph)
    y += .02 * fft_filter(r.standard_normal(n), 1500, 6000) * env
    y = endfade(y, .02)
    return y / (np.abs(y).max() + 1e-9) * vel

_FORM = dict(a=[(730, 1., 90), (1090, .5, 110), (2440, .22, 170), (3400, .08, 250)],
             o=[(480, 1., 80), (820, .45, 100), (2450, .1, 170), (3300, .05, 250)],
             u=[(340, 1., 70), (780, .2, 90), (2300, .04, 170)])
def choir(f, dur, r, vel=1., vowel="o", voices=3, a=.4, rel=1.):
    """Chœur : voix additives filtrées par formants, vibratos et dérives indépendants, stéréo."""
    n = ns(dur + rel); t = T(n); env = adsr(dur, rel, a, .5, 1.)
    out = np.zeros((2, n))
    for ch in range(2):
        for v in range(voices):
            fv = f * 2 ** (r.normal(0, 7) / 1200)
            fi = fv * (1 + r.uniform(.004, .007) * np.sin(2 * np.pi * r.uniform(4.8, 6.) * t + r.uniform(0, 6))
                       + .002 * np.sin(2 * np.pi * r.uniform(.2, .5) * t + r.uniform(0, 6)))
            ph = 2 * np.pi * np.cumsum(fi) / SR + r.uniform(0, 6.28)
            s1 = np.sin(ph); c2 = 2 * np.cos(ph); sp = np.zeros(n); sk = s1; y = np.zeros(n)
            for k in range(1, 60):
                fk = fv * k
                if fk > 5500: break
                g = sum(am * np.exp(-.5 * ((fk - fc) / bw) ** 2) for fc, am, bw in _FORM[vowel]) + .02 / k
                y += g / np.sqrt(k) * sk
                sk, sp = c2 * sk - sp, sk
            out[ch] += y
        out[ch] += .03 * fft_filter(r.standard_normal(n), 800, 4000)
    out *= env
    return out / (np.abs(out).max() + 1e-9) * vel

# préréglages de synthé
def strings(f, dur, r, vel=1., a=.3, rel=.7, fc=2600.):
    return synth(f, dur, r, vel, "saw", fc, a=a, d=.5, s=.9, rel=rel, vib=.0045, vrate=5.4, vdel=.4,
                 voices=3, det=9, stereo=True, velfc=.6)
def pad(f, dur, r, vel=1., fc=1300., wave="saw", a=1.2, rel=2.2):
    return synth(f, dur, r, vel, wave, fc, a=a, d=1, s=1, rel=rel, voices=3, det=12, stereo=True, drift=.0015)
def sub(f, dur, r, vel=1., rel=.2):
    return synth(f, dur, r, vel, "soft", 400, a=.02, d=.5, s=.85, rel=rel, kmax=3)


# =====================================================================================
#  Percussions et ambiances
# =====================================================================================
def kick(r, f0=50, f1=140, pdec=.035, dec=.32, click=.12):
    n = ns(dec * 3.5); t = T(n)
    fi = f0 + (f1 - f0) * np.exp(-t / pdec); ph = 2 * np.pi * np.cumsum(fi) / SR
    y = np.sin(ph) * np.exp(-t / dec) + click * fft_filter(r.standard_normal(n), 1500, 7000) * np.exp(-t / .003)
    return endfade(attack(y, .001), .02) / 1.1

def snare(r, tone=185, snap=.8, dec=.15):
    n = ns(dec * 4); t = T(n)
    y = (np.sin(2 * np.pi * tone * t) + .5 * np.sin(2 * np.pi * tone * 1.62 * t)) * np.exp(-t / .045)
    nz = fft_filter(r.standard_normal(n), 1200, 9000); nz /= np.abs(nz).max()
    y = .6 * y + snap * nz * np.exp(-t / dec)
    return endfade(attack(y, .001), .02) / (np.abs(y).max() + 1e-9)

def rim(r):
    n = ns(.08); t = T(n)
    y = (.6 * np.sin(2 * np.pi * 1700 * t) + np.sin(2 * np.pi * 520 * t)) * np.exp(-t / .009)
    y += .4 * fft_filter(r.standard_normal(n), 2000, 8000) * np.exp(-t / .004)
    return endfade(attack(y, .0005)) / (np.abs(y).max() + 1e-9)

def clap(r):
    n = ns(.3); t = T(n); e = np.zeros(n)
    for d in (0, .011, .022): e += np.where(t >= d, np.exp(-(t - d) / .004), 0)
    e += .5 * np.where(t > .03, np.exp(-(t - .03) / .08), 0)
    y = fft_filter(r.standard_normal(n), 900, 6000) * e
    return endfade(attack(y, .0005)) / (np.abs(y).max() + 1e-9)

_HATF = [205.3, 304.4, 369.6, 522.7, 540., 800.]
def hat(r, dec=.035):
    n = ns(max(dec * 5, .05)); t = T(n)
    y = sum(np.sign(np.sin(2 * np.pi * f * 1.5 * t + r.uniform(0, 6))) for f in _HATF)
    y = fft_filter(y + .5 * r.standard_normal(n), 6500, 16000) * np.exp(-t / dec)
    return endfade(attack(y, .0005)) / (np.abs(y).max() + 1e-9)

def shaker(r, dec=.05):
    n = ns(dec * 4 + .02); t = T(n)
    y = fft_filter(r.standard_normal(n), 4500, 12000) * np.minimum(1, t / .012) * np.exp(-t / dec)
    return endfade(y) / (np.abs(y).max() + 1e-9)

def tom(r, f=110, dec=.35):
    n = ns(dec * 3.5); t = T(n)
    ph = 2 * np.pi * np.cumsum(f * (1 + .45 * np.exp(-t / .05))) / SR
    y = np.sin(ph) * np.exp(-t / dec) + .2 * fft_filter(r.standard_normal(n), 300, 3000) * np.exp(-t / .01)
    return endfade(attack(y, .001)) / (np.abs(y).max() + 1e-9)

def taiko(r, f0=62, dec=.45):
    n = ns(dec * 3.5); t = T(n)
    ph = 2 * np.pi * np.cumsum(f0 * (1 + 1.1 * np.exp(-t / .035))) / SR
    y = np.sin(ph) * np.exp(-t / dec) + .9 * fft_filter(r.standard_normal(n), 60, 400) * np.exp(-t / .03) * 3
    y += .15 * fft_filter(r.standard_normal(n), 400, 2500) * np.exp(-t / .012)
    return endfade(attack(y, .002)) / (np.abs(y).max() + 1e-9)

def ka(r):
    n = ns(.06); t = T(n)
    y = fft_filter(r.standard_normal(n), 1800, 6000) * np.exp(-t / .01) + .6 * np.sin(2 * np.pi * 950 * t) * np.exp(-t / .012)
    return endfade(attack(y, .0005)) / (np.abs(y).max() + 1e-9)

def woodblock(r, f=760, dec=.05):
    n = ns(dec * 5); t = T(n)
    y = np.sin(2 * np.pi * f * t) * np.exp(-t / dec) + .35 * np.sin(2 * np.pi * f * 2.38 * t) * np.exp(-t / (dec * .5))
    y += .2 * fft_filter(r.standard_normal(n), 2000, 7000) * np.exp(-t / .003)
    return endfade(attack(y, .0005)) / (np.abs(y).max() + 1e-9)

def bamboo(r, f=600):
    n = ns(.4); t = T(n)
    y = np.sin(2 * np.pi * f * t) * np.exp(-t / .09) + .4 * np.sin(2 * np.pi * f * 2.92 * t) * np.exp(-t / .03)
    y += .3 * fft_filter(r.standard_normal(n), 1500, 6000) * np.exp(-t / .003)
    return endfade(attack(y, .0005)) / (np.abs(y).max() + 1e-9)

def brush_tap(r, dec=.09):
    n = ns(dec * 4); t = T(n)
    y = fft_filter(r.standard_normal(n), 1200, 7000) * np.minimum(1, t / .005) * np.exp(-t / dec)
    return endfade(y) / (np.abs(y).max() + 1e-9)

def brush_swish(r, dur=.5):
    n = ns(dur); t = T(n)
    y = fft_filter(r.standard_normal(n), 2500, 8000) * np.sin(np.pi * t / dur) ** 2
    return y / (np.abs(y).max() + 1e-9)

def ride(r, dec=1.1):
    n = ns(dec * 3); t = T(n)
    fr = r.uniform(2800, 8500, 26)
    y = sum(np.sin(2 * np.pi * f * t + r.uniform(0, 6)) * np.exp(-t / (dec * r.uniform(.4, 1))) for f in fr) / 6
    y += .6 * np.sin(2 * np.pi * 620 * t) * np.exp(-t / .5) + .3 * fft_filter(r.standard_normal(n), 4000, 12000) * np.exp(-t / .01)
    y = fft_filter(y, 500, 13000)
    return endfade(attack(y, .0005), .05) / (np.abs(y).max() + 1e-9)

def swell(r, dur=2.):
    n = ns(dur); t = T(n)
    y = fft_filter(r.standard_normal(n), 3000, 12000) * (t / dur) ** 3
    return endfade(y, .03) / (np.abs(y).max() + 1e-9)

def crash(r, dec=1.6):
    n = ns(dec * 3); t = T(n)
    y = fft_filter(r.standard_normal(n), 2500, 13000) * np.exp(-t / dec)
    return endfade(attack(y, .001), .1) / (np.abs(y).max() + 1e-9)

def bed_noise(r, L, lo, hi):
    y = fft_filter(r.standard_normal(L), lo, hi)
    return y / (np.sqrt((y ** 2).mean()) + 1e-12)


# =====================================================================================
#  Morceau : tampons circulaires, bus, effets
# =====================================================================================
class Song:
    def __init__(self, name, bpm, meter, bars, seed, swing=0.):
        self.name, self.bpm, self.meter, self.bars = name, bpm, meter, bars
        self.spb = 60 / bpm; self.barlen = meter * self.spb
        d = bars * self.barlen; self.dur = int(round(d))
        assert abs(d - self.dur) < 1e-6, f"{name}: la boucle doit faire un nombre entier de secondes ({d})"
        self.L = self.dur * SR; self.r = np.random.default_rng(seed); self.swing = swing
        self.buses = {}; self.mix = {}
        self.rev = dict(t60=2.2, pre=.02, damp=6000, seed=seed)
        self.dly = dict(time=.75 * self.spb, fb=.38, n=6, hi=3500, lo=250, rev=.3)
        self._cache = {}

    def at(self, bar, beat=0.):
        if self.swing:
            b = np.floor(beat); fr = beat - b
            fr = fr * (1 + 2 * self.swing) if fr <= .5 else .5 + self.swing + (fr - .5) * (1 - 2 * self.swing)
            beat = b + fr
        return bar * self.barlen + beat * self.spb

    def bus(self, name):
        if name not in self.buses: self.buses[name] = np.zeros((2, self.L))
        return self.buses[name]

    def put(self, name, sig, t, g=1., pan=0.):
        b = self.bus(name); sig = np.asarray(sig, float)
        if sig.ndim == 1: sig = np.stack([sig, sig])
        n = sig.shape[1]; L = self.L; i0 = int(round(t * SR)) % L
        gl = g * np.cos((pan + 1) * np.pi / 4) * np.sqrt(2); gr = g * np.sin((pan + 1) * np.pi / 4) * np.sqrt(2)
        while n > 0:
            k = min(n, L - i0)
            b[0, i0:i0 + k] += gl * sig[0, :k]; b[1, i0:i0 + k] += gr * sig[1, :k]
            sig = sig[:, k:]; n -= k; i0 = 0

    def hit(self, name, kind, t, g=1., pan=0., var=6, **kw):
        """Percussion tirée d'un petit jeu de variantes (vivant mais rapide)."""
        key = (kind.__name__, tuple(sorted(kw.items())))
        if key not in self._cache: self._cache[key] = [kind(self.r, **kw) for _ in range(var)]
        v = self._cache[key][self.r.integers(len(self._cache[key]))]
        self.put(name, v, t + self.r.normal(0, .002), g * (1 + self.r.normal(0, .06)), pan)

    def play(self, name, inst, notes, bar=0, g=1., pan=0., leg=1., hum=.005, vel=1., tr=0, beat=0., porta=False,
             spread=0., **kw):
        prev = None; prev_end = -9
        for p, d, m, v in notes:
            t0 = self.at(bar, beat + p) + self.r.normal(0, hum)
            dur = d * self.spb * leg
            extra = dict(kw)
            if porta and prev is not None and abs(p - prev_end) < .05: extra["glide"] = prev
            sig = inst(mf(m + tr), dur, self.r, min(1.3, vel * v * (1 + self.r.normal(0, .04))), **extra)
            self.put(name, sig, t0, g, float(np.clip(pan + (self.r.uniform(-spread, spread) if spread else 0), -1, 1)))
            prev = mf(m + tr); prev_end = p + d

    def chord(self, name, inst, notes, bar, beat=0., dur=4., g=1., pan=0., spread=.5, strum=0., vel=1., **kw):
        k = len(notes)
        for i, m in enumerate(notes):
            pp = pan + (spread * (2 * i / (k - 1) - 1) if k > 1 else 0)
            t0 = self.at(bar, beat) + i * strum + self.r.normal(0, .003)
            self.put(name, inst(mf(m), dur * self.spb, self.r, vel * (1 + self.r.normal(0, .05)), **kw), t0, g, float(np.clip(pp, -1, 1)))


def reverb_wet(x, t60, seed, pre=.02, damp=6000, lo=180, er=.35):
    """Réverbération stéréo par convolution circulaire : bruit à décroissance exponentielle, aigus
    qui s'éteignent plus vite, réflexions précoces, pré-délai."""
    L = x.shape[1]; r = np.random.default_rng(seed)
    n = int(min(t60 * 1.25, L / SR - 1) * SR); t = T(n); p = ns(pre)
    X = np.fft.rfft(x, axis=-1); out = np.zeros_like(x)
    for c in range(2):
        br = fft_filter(r.standard_normal(n), hi=damp * 1.5) * np.exp(-6.9 * t / (t60 * .45))
        dk = fft_filter(r.standard_normal(n), hi=damp * .45) * np.exp(-6.9 * t / t60)
        ir = .6 * br + dk
        ir *= np.minimum(1, t / .012)
        rms = np.sqrt((ir[:ns(.05)] ** 2).mean())
        for k in range(10):
            i = ns(r.uniform(.004, .07)); ir[i] += er * rms * 8 * r.uniform(.3, 1) * (-1) ** k * np.exp(-i / SR / .05)
        ir = np.concatenate([np.zeros(p), ir]); ir = fft_filter(ir, lo=lo, n=1); ir /= np.sqrt((ir ** 2).sum())
        H = np.fft.rfft(ir, L)
        out[c] = np.fft.irfft((.75 * X[c] + .25 * X[1 - c]) * H, L)
    return out

def delay_wet(x, time, fb, n, hi, lo, pingpong=True):
    """Écho circulaire calculé dans le domaine fréquentiel (répétitions filtrées, ping-pong)."""
    L = x.shape[1]; X = np.fft.rfft(x, axis=-1); f = np.fft.rfftfreq(L, 1 / SR)
    H = 1 / np.sqrt(1 + (f / hi) ** 2) / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** 2)
    sh = np.exp(-2j * np.pi * f * time)
    Y = np.zeros_like(X); cur = X.copy()
    for k in range(1, n + 1):
        cur = cur * H * sh
        tap = cur * fb ** (k - 1)
        Y += tap[::-1] if (pingpong and k % 2 == 1) else tap
    return np.fft.irfft(Y, L, axis=-1)

def chorus(x, depth=.0025, rate_cycles=None, mix=.5, base=.012):
    L = x.shape[1]; out = x.copy()
    cyc = rate_cycles or max(1, int(round(L / SR * .6)))
    for c in range(2):
        d = (base + depth * clfo(L, cyc, c * np.pi / 2)) * SR
        p = (np.arange(L) - d) % L; i = np.floor(p).astype(int); fr = p - i
        out[c] = (1 - mix) * x[c] + mix * (x[c][i] * (1 - fr) + x[c][(i + 1) % L] * fr)
    return out

def widen(x, w):
    m = (x[0] + x[1]) / 2; s = (x[0] - x[1]) / 2 * w
    return np.stack([m + s, m - s])

def compress(x, thr_db, ratio, win=.02, makeup=0.):
    p = (x ** 2).mean(0); env = np.sqrt(np.maximum(circ_smooth(p, win), 1e-12))
    lvl = 20 * np.log10(env + 1e-12)
    gr = np.minimum(0, (thr_db - lvl) * (1 - 1 / ratio))
    g = 10 ** ((circ_smooth(gr, .015) + makeup) / 20)
    return x * g

def duck(L, times, depth=.35, rel=.18):
    """Pompage léger déclenché par la grosse caisse (circulaire)."""
    e = np.zeros(L); t = T(ns(rel * 4)); k = 1 - np.exp(-t / rel)
    for tt in times:
        i0 = int(tt * SR) % L; n = len(t); idx = (np.arange(n) + i0) % L
        e[idx] = np.maximum(e[idx], 1 - k)
    return 1 - depth * circ_smooth(e, .004)


def limiter(x, ceil_db=CEIL_DB, look=.003, rel=.06):
    """Limiteur circulaire : enveloppe de crête (maximum glissant), gain lissé, sans raccord."""
    c = 10 ** (ceil_db / 20); a = np.abs(x).max(0); L = len(a)
    blk = ns(look); nb = int(np.ceil(L / blk)); pad = nb * blk - L
    ap = np.concatenate([a, a[:pad]]) if pad else a
    bm = ap.reshape(nb, blk).max(1)
    bm = np.maximum(np.maximum(bm, np.roll(bm, 1)), np.roll(bm, -1))
    env = np.repeat(bm, blk)[:L]
    g = np.minimum(1, c / np.maximum(env, 1e-9))
    gs = np.minimum(g, circ_smooth(g, look * .7))
    y = x * gs
    over = np.abs(y) > c
    if over.any(): y = np.where(over, np.sign(y) * c * (1 - .02 * np.tanh(np.abs(y) / c - 1)), y)
    return y


# ---------- mesure de sonie (UIT-R BS.1770, circulaire) ----------
def _biquad_resp(b, a, f):
    w = 2 * np.pi * f / SR; z = np.exp(-1j * w)
    return (b[0] + b[1] * z + b[2] * z * z) / (a[0] + a[1] * z + a[2] * z * z)

def k_weight(x):
    N = x.shape[-1]; f = np.fft.rfftfreq(N, 1 / SR)
    G, Q, fc = 3.999843853973347, .7071752369554196, 1681.974450955533
    A = 10 ** (G / 40); w0 = 2 * np.pi * fc / SR; al = np.sin(w0) / (2 * Q); cw = np.cos(w0)
    b = [A * ((A + 1) + (A - 1) * cw + 2 * np.sqrt(A) * al), -2 * A * ((A - 1) + (A + 1) * cw),
         A * ((A + 1) + (A - 1) * cw - 2 * np.sqrt(A) * al)]
    a = [(A + 1) - (A - 1) * cw + 2 * np.sqrt(A) * al, 2 * ((A - 1) - (A + 1) * cw), (A + 1) - (A - 1) * cw - 2 * np.sqrt(A) * al]
    H = _biquad_resp(b, a, f)
    Q2, fc2 = .5003270373238773, 38.13547087602444
    w0 = 2 * np.pi * fc2 / SR; al = np.sin(w0) / (2 * Q2); cw = np.cos(w0)
    H = H * _biquad_resp([(1 + cw) / 2, -(1 + cw), (1 + cw) / 2], [1 + al, -2 * cw, 1 - al], f)
    return np.fft.irfft(np.fft.rfft(x, axis=-1) * H, N, axis=-1)

def lufs(x):
    y = k_weight(x); L = y.shape[1]; blk = ns(.4); hop = ns(.1)
    p = (y ** 2).sum(0); c = np.concatenate([[0], np.cumsum(np.concatenate([p, p[:blk]]))])
    starts = np.arange(0, L, hop); z = (c[starts + blk] - c[starts]) / blk
    l = -.691 + 10 * np.log10(z + 1e-15)
    z1 = z[l > -70]
    if not len(z1): return -99.
    rel = -.691 + 10 * np.log10(z1.mean()) - 10
    z2 = z[(l > -70) & (l > rel)]
    return -.691 + 10 * np.log10(z2.mean())


def render(s):
    """Mixage des bus (gain, égalisation, largeur, chorus, envois réverb/écho) puis mastering."""
    L = s.L; dry = np.zeros((2, L)); rv = np.zeros((2, L)); dl = np.zeros((2, L)); levels = {}
    for name, b in s.buses.items():
        c = s.mix.get(name, {})
        x = b * c.get("g", 1.)
        if c.get("lo") or c.get("hi") or c.get("bells") or c.get("shelves"):
            x = fft_filter(x, c.get("lo"), c.get("hi"), c.get("n", 2), c.get("bells", ()), c.get("shelves", ()))
        if c.get("chorus"): x = chorus(x, mix=c["chorus"])
        if c.get("width") is not None: x = widen(x, c["width"])
        if c.get("comp"): x = compress(x, *c["comp"])
        if c.get("env") is not None: x = x * c["env"]
        dry += x * c.get("dry", 1.); rv += x * c.get("rev", .15); dl += x * c.get("dly", 0.)
        levels[name] = 20 * np.log10(np.sqrt((x ** 2).mean()) + 1e-12)
    if np.abs(dl).max() > 0:
        d = s.dly; w = delay_wet(dl, d["time"], d["fb"], d["n"], d["hi"], d["lo"])
        dry += w; rv += w * d["rev"]
    R = s.rev
    dry += reverb_wet(rv, R["t60"], R["seed"], R["pre"], R["damp"])
    return master(dry, s), levels

def master(x, s=None):
    x = x - x.mean(axis=1, keepdims=True)
    x = fft_filter(x, 34, 16500, 2, shelves=(("lo", 140, -5.5),))      # graves plus sobres (casques, haut-parleurs de téléphone)
    x = compress(x, 20 * np.log10(np.sqrt((x ** 2).mean()) + 1e-12) + 6, 1.6, .05)   # colle douce
    for _ in range(3):
        x = x * 10 ** ((TARGET_LUFS - lufs(x)) / 20)
        x = limiter(x, CEIL_DB - .3)
    return x


# =====================================================================================
#  Aides d'arrangement
# =====================================================================================
def arp(s, bus, inst, prog, bars, pattern, step, g, pan=0., vel=(1., .7), oct=0, spread=.3, leg=1.5, every=1, **kw):
    """Arpège : prog = liste d'accords (notes midi) par mesure ; pattern = indices dans l'accord."""
    per_bar = int(round(s.meter / step))
    for bi in bars:
        ch = sorted(prog[bi % len(prog)]); ch = ch + [m + 12 for m in ch]
        for k in range(per_bar):
            if k % every: continue
            idx = pattern[k % len(pattern)]
            if idx is None: continue
            m = ch[idx % len(ch)] + oct
            v = vel[0] if k % 2 == 0 else vel[1]
            sig = inst(mf(m), step * leg * s.spb, s.r, v * (1 + s.r.normal(0, .06)), **kw)
            s.put(bus, sig, s.at(bi, k * step) + s.r.normal(0, .003), g,
                  float(np.clip(pan + spread * np.sin(k * 1.3 + bi), -1, 1)))

def walking(chords, lo=33, hi=50):
    """Ligne de basse « walking » : chords = par mesure, liste de (temps, fondamentale, intervalles).
    Fondamentale au 1er temps de chaque accord, notes de l'accord ensuite, approche chromatique
    vers l'accord suivant."""
    out = []; prev = None; nb = len(chords)
    def near(pc, ref):
        c = [m for m in range(lo, hi + 1) if m % 12 == pc % 12]
        return min(c, key=lambda m: abs(m - ref)) if ref is not None else c[len(c) // 2]
    for bi in range(nb):
        chs = chords[bi]; nxt = chords[(bi + 1) % nb][0][1]
        for beat in range(4):
            cur = [c for c in chs if c[0] <= beat][-1]; st, root, ints = cur
            later = [c for c in chs if c[0] > beat]
            target = later[0][1] if later else nxt
            last_before_change = (beat + 1 == (later[0][0] if later else 4))
            if beat == st:
                m = near(root, prev if prev is not None else 40)
            elif last_before_change:
                tn = near(target, prev)
                m = tn + (1 if prev is not None and prev > tn else -1)
                if m == prev: m = tn + (-1 if m > tn else 1)
            else:
                cands = [near(root + i, prev) for i in ints[1:]]
                tn = near(target, prev)
                cands = [c for c in cands if c != prev] or cands
                m = min(cands, key=lambda c: abs(c - prev) + .5 * abs(c - tn))
            m = int(np.clip(m, lo, hi)); out.append((bi * 4 + beat, 1., m, 1. if beat % 2 == 0 else .85)); prev = m
    return out


# =====================================================================================
#  1. CODEC : base militaire la nuit, radar, transmission. Infiltration feutrée.
# =====================================================================================
def song_codec():
    s = Song("codec", 120, 4, 32, 101)
    A = [("a", 45, [60, 64, 67, 71]), ("a", 45, [60, 64, 67, 71]),            # Am9
         ("f", 41, [57, 60, 64, 71]), ("f", 41, [57, 60, 64, 71]),            # Fmaj7(#11)
         ("d", 38, [60, 64, 65, 69]), ("d", 38, [60, 64, 65, 69]),            # Dm9
         ("e", 40, [57, 59, 62, 64]), ("e", 40, [56, 59, 62, 65])]            # Esus4 / E7(b9)
    Bp = [("f", 41, [57, 60, 64, 67]), ("g", 43, [59, 62, 64, 67]), ("e", 40, [62, 64, 67, 71]), ("a", 45, [60, 64, 67, 69]),
          ("f", 41, [57, 60, 64, 67]), ("g", 43, [59, 62, 67, 71]), ("e", 40, [57, 59, 62, 64]), ("e", 40, [56, 59, 62, 64])]
    prog = A + A + Bp + A                                                        # 32 mesures
    sec = lambda b: b // 8                                                       # 0 repérage, 1 thème, 2 alerte, 3 reprise

    # --- nappe de cordes sourdes, qui s'ouvre au fil des sections
    for bi, (_, root, v) in enumerate(prog):
        fc = (700, 1100, 1500, 1300)[sec(bi)]
        if bi % 2 == 0 or sec(bi) == 2 or bi % 8 >= 6:
            ln = 2 if (sec(bi) == 2 or bi % 8 >= 6) else 4
            if bi % 8 == 6: ln = 1
            for m in v: s.put("pad", pad(mf(m), ln * s.barlen + .3, s.r, .8, fc=fc, a=.6, rel=1.5), s.at(bi), .5)
    # --- basse : ostinato syncopé (le cœur de l'infiltration)
    pat = [(0, 0, .45, 1), (.75, 0, .2, .55), (1.5, 7, .4, .8), (2, 0, .45, .9), (2.75, 12, .2, .6), (3, 10, .4, .75), (3.5, 7, .4, .7)]
    for bi, (_, root, v) in enumerate(prog):
        if bi in (30, 31):                                                       # respiration avant la reprise
            s.put("bass", synth(mf(root), 3.6, s.r, .9, "saw", 380, 1.2, .12, a=.004, d=.6, s=.5, rel=.4), s.at(bi), 1.)
            continue
        st = (5, 6) if v == [56, 59, 62, 65] else ()
        for p, iv, d, vv in pat:
            iv2 = iv + (1 if iv == 0 and bi % 8 == 7 and p >= 2 else 0)
            fc = (330, 420, 520, 460)[sec(bi)]
            s.put("bass", synth(mf(root + iv2), d * s.spb, s.r, vv, "saw", fc, 1.6, .06, q=.25, a=.003, d=.15, s=.45, rel=.06),
                  s.at(bi, p) + s.r.normal(0, .002), 1.)
        s.put("bass", sub(mf(root - 12), 2 * s.spb, s.r, .8), s.at(bi), .55)
        s.put("bass", sub(mf(root - 12), 1.5 * s.spb, s.r, .7), s.at(bi, 2), .5)
    # --- radar : ping toutes les deux mesures, réponse en écho
    for bi in range(0, 32, 2):
        s.put("ping", synth(mf(93), .05, s.r, 1., "sine", 5000, a=.002, d=.25, s=0, rel=.6), s.at(bi), .55, -.2)
        if sec(bi) in (0, 3): s.put("ping", synth(mf(88), .05, s.r, .6, "sine", 5000, a=.002, d=.2, s=0, rel=.5), s.at(bi, 2.5), .3, .35)
    # --- transmission : double bip et parasites aux charnières
    for bi in (0, 16):
        for k, m in enumerate((86, 91)):
            s.put("radio", fft_filter(synth(mf(m), .07, s.r, 1., "square", 3000, a=.002, d=.05, s=.8, rel=.02), 400, 4000),
                  s.at(bi, .5) + k * .11, .22, .5)
    for bi, bt in ((7, 3.), (15, 3.5), (23, 2.5), (29, 1.), (31, 2.)):
        n = ns(.35); g = np.clip(fft_filter(s.r.standard_normal(n), hi=30) * 10 + .4, 0, 1)
        st = fft_filter(s.r.standard_normal(n), 1500, 5000) * np.sin(np.pi * np.arange(n) / n) ** 2 * g
        s.put("radio", st / np.abs(st).max(), s.at(bi, bt), .08, -.5)
    # --- batterie feutrée (demi-temps)
    for bi in range(32):
        S = sec(bi)
        last = bi in (30, 31)
        if S == 0:
            s.hit("drums", kick, s.at(bi), .55, f0=48, dec=.3)
            if bi % 2: s.hit("drums", kick, s.at(bi, .4), .3, f0=48, dec=.25)
        elif not last:
            for p, g in ((0, .8), (1.5, .45), (2.75, .4)): s.hit("drums", kick, s.at(bi, p), g, f0=50, dec=.3)
            s.hit("drums", rim, s.at(bi, 2), .42, .1)
            if bi % 2: s.hit("drums", rim, s.at(bi, 3.75), .14, .2)
        # charleston
        step = .5 if S == 0 or last else .25
        for k in range(int(4 / step)):
            acc = (1, .35, .6, .35)[k % 4] if step == .25 else (.8, .45)[k % 2]
            s.hit("hats", hat, s.at(bi, k * step), .2 * acc, .25 + .1 * np.sin(k), dec=.03)
        if S == 2:
            if bi % 2 == 1:
                for p, f in ((2.5, 110), (3, 98), (3.5, 82)): s.hit("drums", tom, s.at(bi, p), .45, -.3 + p * .15, f=f)
            for k in range(8): s.hit("drums", shaker, s.at(bi, k * .5 + .25), .12, .4)
        if bi == 23:
            for k in range(16): s.hit("drums", snare, s.at(bi, k * .25), .05 + .3 * (k / 16) ** 2, 0, dec=.08)
    for bi in (8, 24): s.hit("fx", crash, s.at(bi), .22, .3)
    for bi in (7, 15, 23): s.put("fx", swell(s.r, 2 * s.spb), s.at(bi, 2), .2, -.2)
    # --- mélodie : lead « transmission » (impulsion feutrée, portamento)
    mA, _ = mel("a4:.75 c5:.25 e5:1.5 d5:.5 c5:.5 b4:.5 | a4:3 r:1 | a4:.75 c5:.25 f5:1.5 e5:.5 c5:.5 a4:.5 | b4:3 r:1 |"
                "a4:.75 d5:.25 f5:1.5 e5:.5 d5:.5 c5:.5 | d5:.5 e5:2.5 r:1 | a4:1 b4:1 d5:1 e5:1 | g#4:1 f5:1 e5:1.5 r:.5")
    lead = dict(wave="pulse", duty=.32, fc=2100, fenv=.6, ftau=.12, a=.012, d=.4, s=.75, rel=.15, vib=.007, vdel=.35, gtau=.03)
    s.play("lead", synth, mA, 8, .9, porta=True, **lead)
    s.play("lead", synth, mA, 24, .8, porta=True, **lead)
    s.play("lead2", strings, shift(mA, 0, -12), 24, .5, a=.12, rel=.4, fc=1800)           # doublure grave à la reprise
    teaser, _ = mel("a3:.75 c4:.25 e4:1.5 d4:.5 c4:.5 b3:.5 | a3:3 r:1")
    s.play("lead", synth, teaser, 4, .55, porta=True, **dict(lead, fc=900))
    # --- pont « alerte » : cuivres de synthé + cordes à l'octave
    mB, _ = mel("a4:1.5 g4:.5 a4:1 c5:1 | b4:1.5 a4:.5 b4:1 d5:1 | e5:3 d5:.5 b4:.5 | c5:2 a4:2 |"
                "a4:1.5 g4:.5 a4:1 c5:1 | b4:1 d5:1 g5:2 | a5:2 g5:1 e5:1 | d5:1 b4:1 g#4:2")
    s.play("brass", synth, mB, 16, .8, wave="saw", fc=1300, fswell=.12, a=.05, d=.6, s=.8, rel=.25, vib=.004, voices=2, det=6)
    s.play("lead2", strings, shift(mB, 0, -12), 16, .55, a=.2, rel=.5, fc=1500)
    # --- arpège de tension (pont et reprise)
    for bi in range(16, 30):
        ch = sorted(prog[bi][2]); seq = [0, 1, 2, 3, 2, 1, 3, 2]
        for k in range(16):
            if sec(bi) == 3 and k % 2: continue
            m = ch[seq[k % 8]] + 12
            s.put("arp", synth(mf(m), .1, s.r, (1, .6, .8, .6)[k % 4], "pulse", 2400, 1.5, .03, duty=.25, a=.002, d=.08, s=.2, rel=.05),
                  s.at(bi, k * .25), .55 if sec(bi) == 2 else .35, .5 * np.sin(k * .8))
    s.mix = dict(pad=dict(g=.30, lo=120, rev=.35, width=1.3), bass=dict(g=.62, hi=2500, rev=.04),
                 ping=dict(g=.22, rev=.35, dly=.6), radio=dict(g=.5, rev=.2, lo=300),
                 drums=dict(g=.62, rev=.12, lo=35), hats=dict(g=.38, rev=.1, lo=4000, hi=12000),
                 lead=dict(g=.50, rev=.28, dly=.32, bells=((2500, -2, .7),)), lead2=dict(g=.26, rev=.3, lo=150),
                 brass=dict(g=.34, rev=.32, dly=.12), arp=dict(g=.15, rev=.2, dly=.45, lo=400, width=1.4),
                 fx=dict(g=.3, rev=.3))
    s.rev.update(t60=1.8, damp=5500); s.dly.update(time=.75 * s.spb, fb=.4, hi=3000)
    return s


# =====================================================================================
#  2. ARCTIQUE : nuit polaire, aurore, glace.
# =====================================================================================
def song_arctique():
    s = Song("arctique", 80, 4, 20, 202)
    I = [(50, [57, 61, 64, 66]), (43, [59, 61, 66, 69]), (50, [57, 61, 64, 66]), (43, [59, 61, 66, 69])]
    A = [(50, [57, 61, 64, 66]), (42, [57, 61, 64, 69]), (43, [59, 62, 66, 69]), (43, [58, 62, 64, 67]),
         (42, [57, 62, 66, 69]), (40, [55, 59, 62, 66]), (43, [59, 61, 66, 69]), (45, [55, 59, 62, 64])]
    B = [(47, [57, 61, 62, 66]), (43, [59, 62, 66, 69]), (42, [57, 62, 66, 69]), (40, [55, 59, 62, 67]),
         (47, [57, 61, 62, 66]), (43, [59, 62, 66, 69]), (40, [55, 59, 62, 66]), (45, [55, 59, 62, 64])]
    prog = I + A + B
    # --- nappe « aurore » : deux couches (sombre / claire) fondues par un LFO lent
    for bi, (root, v) in enumerate(prog):
        for m in v:
            s.put("aurD", pad(mf(m), s.barlen + .6, s.r, .8, fc=900, a=1.0, rel=2.), s.at(bi) - .3, .5)
            s.put("aurB", pad(mf(m + 12), s.barlen + .6, s.r, .6, fc=3500, a=1.4, rel=2.5), s.at(bi) - .3, .35)
        s.put("bass", sub(mf(root), s.barlen * .95, s.r, .9, rel=.6), s.at(bi), 1.)
        s.put("bass", synth(mf(root), s.barlen * .9, s.r, .5, "tri", 600, a=.08, d=1, s=.7, rel=.6), s.at(bi), .45)
        if bi in (3, 11) or bi == 19:
            s.put("bass", sub(mf(root + 7), s.barlen * .45, s.r, .6), s.at(bi, 2), .5)
    L = s.L; w = .5 + .5 * clfo(L, 3, -1.2)
    # --- arpège de cristal en doubles croches (l'aurore qui ondule)
    pat = [0, 2, 4, 6, 7, 5, 3, 1, 0, 2, 4, 5, 7, 6, 4, 2]
    for bi, (root, v) in enumerate(prog):
        ch = sorted(v) + [m + 12 for m in sorted(v)]
        for k in range(16):
            if bi < 4 and k % 2: continue
            m = ch[pat[k] % 8] + (12 if bi >= 12 else 0)
            vv = (1, .55, .75, .55)[k % 4]
            sig = synth(mf(m), .12, s.r, vv, "tri", 4000, 2, .02, a=.002, d=.18, s=0, rel=.4)
            s.put("arp", sig, s.at(bi, k * .25), .5, .6 * np.sin(k * .7 + bi))
    # --- célesta : mélodie A ; cordes + verre : mélodie B (célesta à l'octave)
    mA, _ = mel("a4:.5 d5:.5 e5:.5 f#5:2.5 | e5:.5 c#5:.5 a4:1 e5:2 | d5:.5 e5:.5 f#5:.5 b5:1.5 a5:1 | g5:1 bb5:1 a5:1 e5:1 |"
                "f#5:1.5 e5:.5 d5:1 a4:1 | b4:.5 d5:.5 f#5:1 e5:2 | b4:.5 c#5:.5 d5:.5 f#5:2.5 | e5:2 d5:1 r:1")
    mB, _ = mel("d6:1 c#6:.5 b5:.5 f#5:2 | a5:1 b5:.5 a5:.5 f#5:1 d5:1 | e5:1.5 f#5:.5 a5:2 | g5:1 f#5:1 e5:1 a5:1 |"
                "d6:1 c#6:.5 b5:.5 f#6:2 | e6:1 d6:.5 b5:.5 a5:2 | g5:1 f#5:.5 e5:.5 b5:1 a5:1 | e5:4")
    s.play("cel", lambda f, d, r, v: celesta(f, max(d, 1.2) + 1.5, r, v), mA, 4, .9, -.1)
    s.play("lead", strings, mB, 12, .75, a=.18, rel=.8, fc=3200)
    s.play("lead", synth, mB, 12, .45, wave="soft", fc=4000, a=.06, d=1, s=.85, rel=.5, vib=.005, vdel=.45, stereo=True)
    s.play("cel", lambda f, d, r, v: celesta(f, max(d, 1.) + 1.2, r, v), shift(mB, 0, 12), 12, .4, .25)
    # réponses de célesta dans l'intro
    intro, _ = mel("r:2 f#6:.5 e6:.5 c#6:1 | r:4 | r:2 a5:.5 b5:.5 c#6:1 | e6:3 r:1")
    s.play("cel", lambda f, d, r, v: celesta(f, 2.5, r, v), intro, 0, .5, .3)
    # --- cloches de glace et pulsation de cœur très douce
    for bi in range(4, 20):
        s.hit("perc", kick, s.at(bi), .35 if bi >= 12 else .25, f0=45, dec=.4, click=0.)
        if bi >= 12: s.hit("perc", kick, s.at(bi, 2), .2, f0=45, dec=.35, click=0.)
        for k in range(8):
            if s.r.random() < (.5 if bi >= 12 else .3):
                s.hit("ice", shaker, s.at(bi, k * .5 + .25), .2 * s.r.uniform(.5, 1), s.r.uniform(-.7, .7), dec=.025)
    for bi in (0, 12):
        for m in (86, 93, 98): s.put("bells", chime(mf(m), 5, s.r, .8), s.at(bi) + (m - 86) * .02, .3, s.r.uniform(-.6, .6))
    s.put("fx", swell(s.r, 3 * s.spb), s.at(11, 1), .25, 0)
    s.put("fx", swell(s.r, 3 * s.spb), s.at(3, 1), .15, 0)
    # vent polaire
    wv = bed_noise(s.r, L, 250, 1800) * (.6 + .4 * clfo(L, 2, .5)); wv2 = bed_noise(s.r, L, 250, 1800) * (.6 + .4 * clfo(L, 2, 2.1))
    s.bus("wind")[:] += np.stack([wv, wv2])
    s.mix = dict(aurD=dict(g=.30, lo=100, rev=.4, width=1.4, env=.55 + .45 * (1 - w)),
                 aurB=dict(g=.16, lo=400, hi=7000, rev=.6, width=1.6, env=.25 + .75 * w),
                 bass=dict(g=.55, hi=900, rev=.08), arp=dict(g=.22, lo=300, rev=.45, dly=.45, width=1.3),
                 cel=dict(g=.42, rev=.45, dly=.15), lead=dict(g=.38, rev=.5, dly=.1),
                 perc=dict(g=.45, hi=2000, rev=.1), ice=dict(g=.25, rev=.4), bells=dict(g=.3, rev=.6),
                 fx=dict(g=.3, rev=.5), wind=dict(g=.012, rev=.0))
    s.rev.update(t60=3.6, damp=7000, pre=.03); s.dly.update(time=.75 * s.spb, fb=.45, hi=5000, n=7)
    return s


# =====================================================================================
#  3. ÉCARLATE (réarrangement) : Japon sous la lune rouge. Gamme in sur ré, 90 BPM.
# =====================================================================================
def song_ecarlate():
    s = Song("ecarlate", 90, 4, 24, 303)
    Dm, Bb, Gm, As, Eb = ((38, [50, 57, 62, 65]), (34, [46, 53, 58, 62]), (31, [43, 50, 55, 58]),
                          (33, [45, 52, 57, 62]), (39, [51, 58, 63, 67]))
    harm = [Dm, Dm, Bb, Bb, Gm, Gm, As, As, Gm, Gm, Eb, Eb, Bb, Bb, As, As, Dm, Dm, Bb, Bb, Gm, Gm, As, As]
    koto_ch = {38: ["d4", "a4", "d5", "eb5"], 34: ["bb3", "f4", "bb4", "d5"], 31: ["g3", "d4", "g4", "bb4"],
               33: ["a3", "e4", "a4", "bb4"], 39: ["eb4", "g4", "bb4", "d5"]}
    sec = lambda b: b // 8
    # --- cordes graves tenues (deux mesures par accord), plus pleines à la reprise
    for bi in range(0, 24, 2):
        root, v = harm[bi]
        for m in v: s.put("str", strings(mf(m), 2 * s.barlen + .2, s.r, .7, a=.6, rel=1.2, fc=1400 if sec(bi) < 2 else 2000), s.at(bi), .5)
        s.put("str", strings(mf(root + 12), 2 * s.barlen, s.r, .7, a=.4, rel=1., fc=900), s.at(bi), .6)
    # --- koto : ostinato en croches (motif qui suit l'accord), respiration en fin de phrase
    pat = [0, 1, 2, 1, 3, 2, 1, 2]
    for bi in range(24):
        root, _ = harm[bi]; ch = [nm(x) for x in koto_ch[root]]
        for k, p in enumerate(pat):
            if bi % 8 == 7 and k >= 5: continue
            if sec(bi) == 0 and k % 2 and bi < 2: continue
            g = (.9 if k % 2 == 0 else .62) * (.8 if sec(bi) == 0 else 1)
            s.put("koto", ks(mf(ch[p]), 1.6, s.r, .45, .8, pick=.25), s.at(bi, k * .5) + s.r.normal(0, .004), g, -.35 + .1 * (k % 4))
        s.put("koto", ks(mf(root + 12), 3, s.r, .9, .6, pick=.2), s.at(bi), .95, -.05)
        if bi % 2: s.put("koto", ks(mf(root + 19), 2, s.r, .7, .6, pick=.15), s.at(bi, 2.5), .55, .1)
    # gliss de koto en fin de section
    for bi in (7, 15, 23):
        for k, x in enumerate(["d5", "eb5", "g5", "a5", "bb5", "d6"]):
            s.put("koto", ks(mf(nm(x)), 1.2, s.r, .4, .9, pick=.3), s.at(bi, 2.5) + k * .06, .45, -.4 + k * .1)
    # --- shamisen : contre-chant rythmique (section B et reprise)
    sham = mel("x:.5 r:.5 x:.25 x:.25 r:.5 x:.5 r:.25 x:.25 r:.5 x:.5".replace("x", "d4"))[0]
    for bi in range(8, 24):
        if sec(bi) == 2 and bi % 2 == 0: continue
        root, _ = harm[bi]
        for p, d, m, v in sham:
            nn = root + 24 + (7 if p in (1.5, 3.5) else 0)
            sig = ks(mf(nn), .5, s.r, .2, 1., pick=.5)
            sig = sig + .35 * np.tanh(5 * sig) * np.exp(-T(len(sig)) / .12)
            s.put("sham", sig, s.at(bi, p) + s.r.normal(0, .003), .7 * v, .45)
    # --- shakuhachi : A (doux), B (plus haut), A' ornée
    mA, _ = mel("d5:1.5 eb5:.5 d5:1 a4:1 | d5:1 g5:2 a5:.5 g5:.5 | eb5:2 d5:1 bb4:1 | d5:3 r:1 |"
                "bb4:1.5 a4:.5 g4:1 bb4:1 | a4:.5 g4:.5 d4:1 g4:2 | a4:3 bb4:.5 a4:.5 | a4:3 r:1")
    mB, _ = mel("g5:1.5 a5:.5 bb5:2 | a5:1 g5:1 d5:2 | g5:1.5 bb5:.5 a5:2 | g5:1 eb5:1 d5:2 |"
                "d5:1.5 eb5:.5 g5:1 a5:1 | bb5:2 a5:1 g5:1 | a5:1.5 g5:.5 eb5:1 d5:1 | a4:4")
    s.play("shaku", lambda f, d, r, v: shaku(f, d + .15, r, v, scoop=d > .45), mA, 0, .85, .05)
    s.play("shaku", lambda f, d, r, v: shaku(f, d + .15, r, v, scoop=d > .45), mB, 8, .95, .05)
    s.play("shaku", lambda f, d, r, v: shaku(f, d + .15, r, v, scoop=d > .45), mA, 16, .95, .05)
    # à la reprise, le koto double la mélodie à l'octave inférieure
    s.play("koto2", lambda f, d, r, v: ks(f, max(1.2, d), r, .5, .9, pick=.25), shift(mA, 0, -12), 16, .55, .3)
    # ornements (notes de passage brèves)
    for bi, bt, x in ((17, .9, "a5"), (20, .4, "a4"), (22, 2.6, "bb4")):
        s.put("shaku", shaku(mf(nm(x)), .22, s.r, .6, scoop=False), s.at(bi, bt), .45, .05)
    # --- taikos : « don » sur le 1, « don-don » en fin de mesure, ka sur les contretemps
    for bi in range(24):
        S = sec(bi)
        if S == 0:
            if bi % 2 == 0: s.hit("taiko", taiko, s.at(bi), .7, 0, f0=58)
            s.hit("perc", ka, s.at(bi, 3.5), .2, .5)
            continue
        s.hit("taiko", taiko, s.at(bi), .85, 0, f0=58)
        s.hit("taiko", taiko, s.at(bi, 2.5), .4, -.15, f0=66, dec=.3)
        if bi % 2: s.hit("taiko", taiko, s.at(bi, 3), .5, .2, f0=64, dec=.3); s.hit("taiko", taiko, s.at(bi, 3.5), .45, -.2, f0=64, dec=.3)
        for k in (1, 3): s.hit("perc", ka, s.at(bi, k + .5), .28, -.5 if k == 1 else .5)
        s.hit("perc", ka, s.at(bi, 1), .16, .6)
        if bi % 4 == 3: s.hit("taiko", taiko, s.at(bi, 2), .65, 0, f0=46, dec=.7)
    for k, (p, g) in enumerate(((0, .5), (.5, .55), (1, .6), (1.5, .65), (2, .7), (2.5, .5), (2.75, .55), (3, .8), (3.5, .9))):
        s.hit("taiko", taiko, s.at(23, p), g, (-1) ** k * .25, f0=60 + k)
    # cloches de temple aux changements de section
    for bi, m in ((0, 86), (8, 89), (16, 86)):
        s.put("bell", kane(mf(m), 5, s.r, 1), s.at(bi), .5, -.4); s.put("bell", kane(mf(m - 5), 5, s.r, 1), s.at(bi) + .05, .35, .4)
    wv = bed_noise(s.r, s.L, 600, 2800) * (.7 + .3 * clfo(s.L, 3, 0))
    s.bus("wind")[:] += np.stack([wv, np.roll(wv, SR // 3)])
    s.mix = dict(str=dict(g=.26, lo=70, hi=5000, rev=.35, width=1.3), koto=dict(g=.30, lo=90, rev=.22, bells=((3000, -2, .8),)),
                 koto2=dict(g=.20, lo=150, rev=.25), sham=dict(g=.16, lo=250, hi=6000, rev=.18),
                 shaku=dict(g=.42, lo=200, rev=.32, dly=.08), taiko=dict(g=.62, rev=.18, lo=35),
                 perc=dict(g=.30, rev=.2), bell=dict(g=.22, rev=.5), wind=dict(g=.006, rev=0))
    s.rev.update(t60=2.4, damp=6000); s.dly.update(time=.5 * s.spb * 3, fb=.3, hi=3000)
    return s


# =====================================================================================
#  4. SAKURA (réarrangement) : rivière, pont en dos d'âne, cerisiers la nuit. 96 BPM.
# =====================================================================================
def song_sakura():
    s = Song("sakura", 96, 4, 24, 404)
    A = [(48, [64, 67, 71, 74]), (45, [60, 64, 67, 71]), (41, [57, 60, 64, 67]), (43, [60, 62, 64, 67]),
         (48, [64, 67, 71, 74]), (40, [59, 62, 64, 67]), (41, [60, 64, 65, 69]), (43, [60, 62, 65, 67])]
    B = [(41, [60, 64, 65, 69]), (40, [59, 62, 64, 67]), (38, [60, 64, 65, 69]), (40, [60, 64, 67, 72]),
         (41, [60, 64, 65, 69]), (41, [59, 62, 67, 71]), (40, [59, 62, 64, 67]), (38, [60, 64, 65, 69])]
    prog = A + B + A
    half = {14: (45, [60, 64, 67, 69]), 15: (43, [60, 64, 65, 69])}               # 2e moitié de mesure
    sec = lambda b: b // 8
    # --- nappe de cordes très douce
    for bi, (root, v) in enumerate(prog):
        for m in v: s.put("pad", pad(mf(m), s.barlen + .4, s.r, .7, fc=1500 if sec(bi) != 1 else 2200, a=.8, rel=1.6), s.at(bi), .45)
    # --- la rivière : arpège de harpe/koto en croches, qui coule sans arrêt
    pat = [0, 2, 3, 4, 5, 4, 3, 2]
    for bi, (root, v) in enumerate(prog):
        for k in range(8):
            r2, v2 = half[bi] if (bi in half and k >= 4) else (root, v)
            ch = sorted([r2 + 12] + v2 + [v2[1] + 12])
            m = ch[pat[k] % len(ch)]
            g = (.85 if k % 2 == 0 else .6) * (1.1 if k == 0 else 1)
            s.put("harp", ks(mf(m), 1.8, s.r, .9, .55, pick=.08), s.at(bi, k * .5) + s.r.normal(0, .004), g, -.3 + .6 * k / 7)
    # --- basse : piano feutré sur les temps 1 et 3
    for bi, (root, v) in enumerate(prog):
        s.put("bass", piano(mf(root), 1.8 * s.spb, s.r, .7, bright=.5), s.at(bi), 1.)
        r2 = half[bi][0] if bi in half else root
        s.put("bass", piano(mf(r2 + (7 if r2 == root else 0)), 1.8 * s.spb, s.r, .55, bright=.5), s.at(bi, 2), .8)
    # --- mélodie : boîte à musique (A), flûte (B), les deux à l'octave (A')
    mA, _ = mel("e5:1 g5:1 a5:1.5 g5:.5 | e5:2 d5:1 c5:1 | a4:1 c5:1 d5:1.5 e5:.5 | d5:3 r:1 |"
                "e5:1 g5:1 c6:1.5 a5:.5 | g5:2 e5:1 d5:1 | c5:1 d5:1 e5:1 a5:1 | g5:3 r:1")
    mA2, _ = mel("e5:1 g5:1 a5:1.5 g5:.5 | e5:2 d5:1 c5:1 | a4:1 c5:1 d5:1.5 e5:.5 | d5:3 r:1 |"
                 "e5:1 g5:1 c6:1.5 a5:.5 | g5:2 e5:1 d5:1 | c5:1 d5:1 e5:1 a5:1 | g5:2 e5:1 d5:1")
    mB, _ = mel("a5:1.5 g5:.5 e5:1 g5:1 | g5:1.5 e5:.5 d5:2 | c5:1 d5:1 e5:1 a5:1 | g5:3 e5:1 |"
                "a5:1.5 c6:.5 a5:1 g5:1 | d6:2 c6:1 a5:1 | g5:1.5 a5:.5 e5:2 | d5:2 r:2")
    mb = lambda f, d, r, v: mbox(f, 2.4, r, v)
    s.play("mbox", mb, shift(mA, 0, 12), 0, .75, .15)
    fl = lambda f, d, r, v: flute(f, d + .1, r, v, vib=.009, breath=.18, scoop=-.004)
    s.play("flute", fl, mB, 8, .9, -.05)
    s.play("flute", fl, mA2, 16, .85, -.05)
    s.play("mbox", mb, shift(mA2, 0, 12), 16, .45, .25)
    # contre-chant de boîte à musique pendant B
    cB, _ = mel("r:2 e6:.5 d6:.5 c6:1 | r:4 | r:2 g6:.5 e6:.5 d6:1 | r:4 | r:2 a6:.5 g6:.5 e6:1 | r:4 | r:2 e6:.5 d6:.5 b5:1 | r:2 g5:.5 a5:.5 d6:1")
    s.play("mbox", mb, cB, 8, .35, .4)
    # --- percussions légères à la reprise : grelot doux et petit tambour sur le 1
    for bi in range(16, 24):
        for k in range(8): s.hit("perc", shaker, s.at(bi, k * .5), (.35 if k % 2 == 0 else .2), .35, dec=.04)
        s.hit("perc", tom, s.at(bi), .35, -.2, f=90, dec=.3)
        if bi % 2: s.hit("perc", tom, s.at(bi, 2.5), .2, .2, f=120, dec=.25)
    for bi in (0, 8, 16):
        for k, m in enumerate((84, 88, 91, 96)): s.put("chime", chime(mf(m), 4, s.r, .7), s.at(bi) + k * .07, .25, -.5 + k * .3)
    # --- l'eau : courant doux + quelques bulles
    L = s.L; wa = bed_noise(s.r, L, 300, 2400) * (.75 + .25 * clfo(L, 5, 0)); wb = bed_noise(s.r, L, 300, 2400) * (.75 + .25 * clfo(L, 4, 1))
    s.bus("water")[:] += np.stack([wa, wb])
    for k in range(70):
        n = ns(.03); t = T(n); f0 = s.r.uniform(500, 1400)
        b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 1.2 * t / .03)) / SR) * np.sin(np.pi * t / .03) ** 2
        s.put("water", b * 6, s.r.uniform(0, s.dur), s.r.uniform(.2, .6), s.r.uniform(-.8, .8))
    s.mix = dict(pad=dict(g=.22, lo=150, rev=.4, width=1.4), harp=dict(g=.30, lo=120, rev=.3, dly=.1, bells=((2500, -1.5, .8),)),
                 bass=dict(g=.38, hi=1500, rev=.15), mbox=dict(g=.30, rev=.4, dly=.18),
                 flute=dict(g=.40, lo=250, rev=.35, dly=.1), perc=dict(g=.25, rev=.2), chime=dict(g=.2, rev=.5),
                 water=dict(g=.008, rev=.15))
    s.rev.update(t60=2.6, damp=6500); s.dly.update(time=.75 * s.spb, fb=.3, hi=4000)
    return s


# =====================================================================================
#  5. AMBRE (réarrangement) : ville Art déco la nuit, jazz swing des années 30, AABA.
# =====================================================================================
def song_ambre():
    s = Song("ambre", 120, 4, 32, 505, swing=.14)
    C = dict(Dm9=(2, [53, 57, 60, 64], [0, 3, 7, 10]), G13=(7, [53, 59, 64, 69], [0, 4, 7, 10]),
             Cmaj9=(0, [52, 55, 59, 62], [0, 4, 7, 11]), A7b9=(9, [55, 58, 61, 64], [0, 4, 7, 10]),
             G7b9=(7, [53, 56, 59, 62], [0, 4, 7, 10]), C6=(0, [52, 55, 57, 62], [0, 4, 7, 9]),
             Em7=(4, [55, 59, 62, 64], [0, 3, 7, 10]), Gm7=(7, [53, 58, 62, 65], [0, 3, 7, 10]),
             C7b9=(0, [52, 55, 58, 61], [0, 4, 7, 10]), Fmaj7=(5, [52, 57, 60, 64], [0, 4, 7, 11]),
             Bb9=(10, [56, 60, 62, 65], [0, 4, 7, 10]), D9=(2, [54, 57, 60, 64], [0, 4, 7, 10]),
             G7alt=(7, [53, 56, 59, 63], [0, 4, 8, 10]))
    Aform = [["Dm9"], ["G13"], ["Cmaj9"], ["A7b9"], ["Dm9"], ["G7b9"], ["Cmaj9"], ["Em7", "A7b9"]]
    A2 = Aform[:6] + [["C6"], ["Gm7", "C7b9"]]
    Bf = [["Fmaj7"], ["Bb9"], ["Em7"], ["A7b9"], ["D9"], ["D9"], ["Dm9"], ["G7b9", "G7alt"]]
    form = Aform + A2 + Bf + Aform
    sec = lambda b: b // 8
    def chords_of(bi):
        cs = form[bi]; return [(0 if i == 0 else 2, c) for i, c in enumerate(cs)]
    # --- contrebasse : en « deux » au premier A, puis walking
    wl = walking([[(b, C[c][0] + 36, C[c][2]) for b, c in chords_of(bi)] for bi in range(32)], 33, 50)
    for bt, d, m, v in wl:
        bi = int(bt // 4); p = bt % 4
        if sec(bi) == 0 and p in (1, 3): continue
        dd = 1.9 if sec(bi) == 0 else .95
        s.put("bass", upright(mf(m), dd * s.spb, s.r, v), s.at(bi, p) + s.r.normal(0, .004), 1.)
    # --- piano : Charleston au A, nappes au pont, stride à la reprise
    for bi in range(32):
        for b, c in chords_of(bi):
            root, v, _ = C[c]
            if sec(bi) in (0, 1):
                hits = ((b, .55), (b + 1.5, .3)) if len(form[bi]) == 1 else ((b, .5),)
                for p, d in hits:
                    s.chord("piano", piano, v, bi, p, d, .55, -.15, .3, .008, .62 if p == b else .5)
            elif sec(bi) == 2:
                s.chord("piano", piano, v, bi, b, 1.6 if len(form[bi]) == 1 else 1., .4, -.15, .3, .02, .45)
                if len(form[bi]) == 1: s.chord("piano", piano, [x + 12 for x in v[1:]], bi, b + 2.5, .4, .3, -.1, .3, .01, .4)
            else:
                for p in ([0, 1, 2, 3] if len(form[bi]) == 1 else [b, b + 1]):
                    if p % 2 == 0:
                        s.put("piano", piano(mf(root + 36 + (7 if p == 2 else 0)), .5 * s.spb, s.r, .6), s.at(bi, p), .55, -.3)
                    else:
                        s.chord("piano", piano, v, bi, p, .45, .42, -.05, .25, .006, .5)
    # remplissage de piano dans les silences de la mélodie
    fill, _ = mel("c6:.5 b5:.5 a5:.5 g5:.5 e5:.5 eb5:.5 d5:.5 c5:.5")
    s.play("piano", lambda f, d, r, v: piano(f, d, r, v * .7), fill, 15, .45, .15)
    run2, _ = mel("r:1 e5:.5 g5:.5 b5:.5 d6:.5 c#6:1")
    s.play("piano", lambda f, d, r, v: piano(f, d, r, v * .7), run2, 6, .35, .15)
    # --- pupitre d'anches : nappe douce au pont et à la reprise
    for bi in range(16, 32):
        for b, c in chords_of(bi):
            ln = (4 if len(form[bi]) == 1 else 2) - .2
            for i, m in enumerate(C[c][1]):
                s.put("reeds", horn(mf(m + 12), ln * s.spb, s.r, .55, "sax", rel=.25), s.at(bi, b), .5, -.4 + .27 * i)
    # --- mélodie : trompette bouchée (A), clarinette (pont), réponse de clarinette à la reprise
    mA, _ = mel("r:.5 a4:.5 c5:.5 d5:.5 f5:1 e5:.5 d5:.5 | e5:2 r:1 d5:.5 e5:.5 | b4:1.5 d5:.5 g5:1 e5:1 | c#5:1.5 bb4:.5 a4:1 g4:1 |"
                "r:.5 c5:.5 e5:.5 f5:.5 a5:1 g5:.5 f5:.5 | f5:1 ab5:1 g5:1 f5:1")
    end1, _ = mel("e5:3 r:1 | r:1 c#5:.5 e5:.5 g5:.5 bb5:.5 a5:1")
    end2, _ = mel("e5:1 d5:.5 c5:2.5 | r:4")
    end3, _ = mel("e5:2.5 d5:.5 c5:1 | g4:1 bb4:1 c#5:1 e5:1")
    mBr, _ = mel("c5:1 e5:1 a5:2 | ab5:2 f5:1 d5:1 | g5:1 e5:1 b4:1.5 d5:.5 | c#5:2 e5:1 g5:1 |"
                 "f#5:2 a5:1 f#5:1 | e5:1 d5:1 c5:1 a4:1 | f5:2 e5:1 d5:1 | b4:2 d5:.5 f5:.5 ab5:1")
    tp = lambda f, d, r, v: horn(f, d * .92, r, v, "harmon")
    s.play("tpt", tp, mA, 0, .85, .1, hum=.008); s.play("tpt", tp, end1, 6, .85, .1)
    s.play("tpt", tp, mA, 8, .9, .1, hum=.008); s.play("tpt", tp, end2, 14, .9, .1)
    s.play("tpt", tp, mA, 24, .95, .1, hum=.008); s.play("tpt", tp, end3, 30, .9, .1)
    cl = lambda f, d, r, v: horn(f, d * .95, r, v, "clarinet")
    s.play("clar", cl, mBr, 16, .9, -.1, hum=.008)
    resp, _ = mel("r:2 a5:.5 g5:.5 e5:1 | r:4 | r:2 g5:.5 f5:.5 d5:1 | r:4 | r:2 c6:.5 a5:.5 f5:1")
    s.play("clar", cl, resp, 24, .45, -.35)
    # --- balais : tourbillon continu, frappes 2 et 4, cymbale ride au pont et à la reprise
    for bi in range(32):
        S = sec(bi)
        for k in range(2): s.put("drums", brush_swish(s.r, 2 * s.spb), s.at(bi, k * 2), .16, -.25)
        for p in range(4): s.hit("drums", kick, s.at(bi, p), .16, 0, f0=55, dec=.18, click=0.)
        if S >= 1:
            for p in (1, 3): s.hit("drums", brush_tap, s.at(bi, p), .55, .1)
        if S >= 2:
            for p in (0, 1, 1.5, 2, 3, 3.5):
                s.hit("cym", ride, s.at(bi, p), .5 if p == int(p) else .3, .45, var=4)
        elif S == 1:
            for p in (1, 3): s.hit("cym", hat, s.at(bi, p), .4, .4, dec=.025)
        if S == 3:
            s.hit("drums", rim, s.at(bi, 3.5), .08, .2)
    s.mix = dict(bass=dict(g=.62, hi=2500, lo=40, rev=.1), piano=dict(g=.36, lo=80, rev=.22, shelves=(("hi", 5000, -3),)),
                 reeds=dict(g=.16, lo=200, hi=5000, rev=.3, width=1.2), tpt=dict(g=.40, lo=300, rev=.25, dly=.06),
                 clar=dict(g=.34, lo=150, rev=.28), drums=dict(g=.42, rev=.12, hi=9000), cym=dict(g=.16, lo=900, rev=.15, hi=10000))
    s.rev.update(t60=1.7, damp=5000, pre=.025); s.dly.update(time=1 * s.spb, fb=.25, hi=2500, n=3)
    return s


# =====================================================================================
#  6. NÉON : ruelle de Tokyo sous la pluie, city pop. 105 BPM.
# =====================================================================================
def song_neon():
    s = Song("neon", 105, 4, 28, 606)
    V = dict(Bbmaj9=(46, [57, 60, 62, 65]), Bbmaj7=(46, [57, 62, 65, 69]), Am7=(45, [55, 60, 64, 67]),
             Gm9=(43, [58, 62, 65, 69]), C9sus=(48, [58, 62, 65, 67]), A7=(45, [55, 61, 64, 67]),
             Dm9=(38, [53, 57, 60, 64]), Cm7=(48, [51, 55, 58, 62]), F7=(41, [55, 57, 60, 63]),
             C9=(48, [52, 58, 62, 67]), Fmaj9=(41, [57, 60, 64, 67]), A7alt=(45, [55, 61, 65, 70]))
    intro = [["Bbmaj9"], ["Am7"], ["Gm9"], ["C9sus"]]
    verse = [["Bbmaj7"], ["A7"], ["Dm9"], ["Cm7", "F7"]] * 2
    cho = [["Bbmaj7"], ["C9"], ["Am7"], ["Dm9"], ["Gm9"], ["C9sus", "C9"], ["Fmaj9"], ["A7alt"]]
    cho2 = cho[:6] + [["Am7", "Dm9"], ["Gm9", "C9sus"]]
    form = intro + verse + cho + cho2
    sec = lambda b: 0 if b < 4 else 1 if b < 12 else 2 if b < 20 else 3
    def chords_of(bi): return [(0 if i == 0 else 2, c) for i, c in enumerate(form[bi])]
    kicks = []
    # --- batterie
    for bi in range(28):
        S = sec(bi)
        if S == 0:
            for p in (0, 2.5): s.hit("drums", kick, s.at(bi, p), .5, f0=50, dec=.25)
            for k in range(8): s.hit("hats", hat, s.at(bi, k * .5), .3 if k % 2 else .18, .3, dec=.03)
            if bi == 3:
                for k in range(8): s.hit("drums", snare, s.at(bi, 2 + k * .25), .1 + .3 * k / 8, 0, dec=.1)
            continue
        for p in (0, 1, 2, 3) if S >= 2 else (0, 1.75, 2, 2.5):
            s.hit("drums", kick, s.at(bi, p), .75 if p in (0, 2) else .5, f0=50, dec=.28); kicks.append(s.at(bi, p))
        for p in (1, 3):
            s.hit("snare", snare, s.at(bi, p), .7, .05, tone=200, dec=.17)
            if S >= 2: s.hit("snare", clap, s.at(bi, p), .4, -.05)
        for k in range(16):
            g = (.55, .25, .4, .25)[k % 4]
            if k == 14: s.hit("hats", hat, s.at(bi, k * .25), .5, .3, dec=.16)
            else: s.hit("hats", hat, s.at(bi, k * .25), g * (.8 if S == 1 else 1), .3, dec=.03)
        if S >= 2:
            for k in range(8): s.hit("perc", shaker, s.at(bi, k * .5 + .25), .3, -.45, dec=.05)
        if bi in (11, 19):
            for p, f in ((3, 140), (3.25, 120), (3.5, 100), (3.75, 85)): s.hit("drums", tom, s.at(bi, p), .5, (p - 3.4) * 1.5, f=f, dec=.25)
    for bi in (12, 20): s.hit("cym", crash, s.at(bi), .35, -.3)
    s.put("cym", swell(s.r, 4 * s.spb), s.at(11), .25, .2)
    # --- basse aux doigts : syncopes en doubles croches, octaves
    pat = [(0, 0, .7, 1), (.75, 0, .2, .55), (1, 12, .2, .7), (1.5, 0, .45, .75), (2, 0, .5, .9), (2.75, 7, .2, .6), (3, 12, .2, .7), (3.5, 10, .4, .7)]
    for bi in range(28):
        for b, c in chords_of(bi):
            root = V[c][0]; root = root if root < 46 else root - 12
            for p, iv, d, v in pat:
                if not (b <= p < b + (4 if len(form[bi]) == 1 else 2)): continue
                if sec(bi) == 0 and p not in (0, 2, 2.75) and p - b not in (0,): continue
                sig = synth(mf(root + iv), d * s.spb, s.r, v, "saw", 650, 2.2, .05, q=.2, a=.003, d=.25, s=.6, rel=.05)
                sb = sub(mf(root + iv), d * s.spb, s.r, v, rel=.05); k = min(len(sig), len(sb))
                sig = sig[:k] + .7 * sb[:k]
                s.put("bass", sig, s.at(bi, p), 1.)
    # --- piano électrique : accords syncopés
    for bi in range(28):
        for b, c in chords_of(bi):
            v = V[c][1]; one = len(form[bi]) == 1
            hits = ((b, 1.4), (b + 1.75, .6), (b + 2.5, 1.4)) if one else ((b, .7), (b + 1.5, .45))
            if sec(bi) == 0: hits = ((b, 3.8 if one else 1.8),)
            for p, d in hits:
                s.chord("ep", ep, v, bi, p, d, .5, 0, .5, .01, .7 if p == b else .55)
    # --- guitare claire « cocotte » (sections 2 et 3)
    for bi in range(12, 28):
        for b, c in chords_of(bi):
            v = V[c][1]
            for p in (.5, 1.5, 2.5, 3.5):
                if not (b <= p < b + (4 if len(form[bi]) == 1 else 2)): continue
                for i, m in enumerate(sorted(v)[1:]):
                    s.put("gtr", ks(mf(m + 12), .3, s.r, .15, .9, pick=.2, damp=.08), s.at(bi, p) + i * .006, .5, .55)
    # --- cuivres de synthé : ponctuations du refrain
    for bi in (12, 14, 16, 18, 20, 22, 24, 26):
        v = V[form[bi][0]][1]
        short = bi % 4 != 0                                  # stab bref sur le « et » du 4, ou accord tenu sur le 1
        for m in v:
            sig = synth(mf(m + 12), (.25 if short else .9) * s.spb, s.r, .8, "saw", 1500, 2, .06, a=.01, d=.2, s=.7, rel=.15,
                        voices=2, det=8, stereo=True)
            s.put("brass", sig, s.at(bi, 3.5 if short else 0), .25, 0)
    # --- nappe (refrains) pompée par la grosse caisse
    for bi in range(4, 28):
        for b, c in chords_of(bi):
            for m in V[c][1]: s.put("pad", pad(mf(m + 12), (4 if len(form[bi]) == 1 else 2) * s.spb + .2, s.r, .6, fc=2200, a=.3, rel=.8), s.at(bi, b), .35)
    # --- mélodies : couplet (flûte de synthé), refrain (lead « lyricon »), harmonie au 2e refrain
    mV, _ = mel("r:.5 f5:.5 a5:.75 g5:.75 f5:.5 d5:1 | e5:.5 c#5:.5 e5:.5 g5:1 f5:.5 e5:1 | r:.5 d5:.5 e5:.5 f5:.5 a5:1.5 g5:.5 |"
                "g5:1 f5:.5 eb5:.5 d5:1 c5:1 | r:.5 f5:.5 a5:.75 g5:.75 f5:.5 d5:1 | e5:.5 c#5:.5 e5:.5 g5:1 f5:.5 e5:1 |"
                "r:.5 d5:.5 e5:.5 f5:.5 a5:1.5 g5:.5 | g5:1 a5:1 c6:2")
    mC, _ = mel("d6:1.5 c6:.5 a5:1 f5:1 | c6:1.5 bb5:.5 g5:1 e5:1 | a5:1.5 g5:.5 e5:1 c5:1 | d5:1 e5:.5 f5:.5 a5:2 |"
                "bb5:1.5 a5:.5 g5:1 f5:1 | g5:1.5 f5:.5 e5:1 c5:1")
    endC1, _ = mel("f5:.5 g5:.5 a5:1 c6:1 a5:1 | g5:1.5 f5:.5 c#5:2")
    endC2, _ = mel("e5:1 g5:1 a5:1 c6:1 | bb5:1.5 a5:.5 g5:2")
    s.play("lead", synth, mV, 4, .7, porta=True, wave="soft", fc=3500, a=.02, d=.5, s=.8, rel=.2, vib=.006, vdel=.3, gtau=.04)
    ly = dict(wave="saw", fc=1600, fenv=1.2, ftau=.08, q=.4, a=.015, d=.4, s=.8, rel=.18, vib=.008, vdel=.25, gtau=.05, voices=2, det=5)
    s.play("lead", synth, mC, 12, .85, porta=True, **ly); s.play("lead", synth, endC1, 18, .85, porta=True, **ly)
    s.play("lead", synth, mC, 20, .9, porta=True, **ly); s.play("lead", synth, endC2, 26, .9, porta=True, **ly)
    harm3 = [(p, d, m - (4 if (m % 12) in (2, 9, 4) else 3), v) for p, d, m, v in mC + shift(endC2, 24)]
    s.play("lead2", synth, harm3, 20, .55, **dict(ly, fc=1200, voices=1))
    # --- la pluie et les enseignes (bourdonnement très bas)
    L = s.L
    ra = bed_noise(s.r, L, 600, 9000); rb = bed_noise(s.r, L, 600, 9000)
    s.bus("rain")[:] += np.stack([ra, rb])
    for k in range(260):
        n = ns(.015); t = T(n); f0 = s.r.uniform(2500, 6000)
        dr = np.sin(2 * np.pi * f0 * (1 - .3 * t / .015) * t) * np.exp(-t / .004)
        s.put("rain", dr * s.r.uniform(1, 4), s.r.uniform(0, s.dur), 1., s.r.uniform(-.9, .9))
    s.mix = dict(drums=dict(g=.55, rev=.06, lo=35), snare=dict(g=.30, rev=.35, lo=150), hats=dict(g=.22, lo=5000, rev=.06),
                 perc=dict(g=.15, rev=.1), cym=dict(g=.22, rev=.2), bass=dict(g=.52, hi=3000, rev=.02),
                 ep=dict(g=.30, lo=150, chorus=.5, rev=.25, width=1.3, shelves=(("hi", 6000, -2),)), gtr=dict(g=.16, lo=400, hi=7000, chorus=.5, rev=.2, dly=.15),
                 brass=dict(g=.18, lo=250, rev=.25), pad=dict(g=.13, lo=300, rev=.35, width=1.5),
                 lead=dict(g=.40, rev=.28, dly=.25, lo=200), lead2=dict(g=.22, rev=.3, dly=.2, lo=250), rain=dict(g=.010, hi=8000, rev=.1))
    dk = duck(s.L, kicks, .35, .16)
    s.mix["pad"]["env"] = dk; s.mix["ep"]["env"] = .85 + .15 * dk
    s.rev.update(t60=2.0, damp=6500, pre=.03); s.dly.update(time=.75 * s.spb, fb=.38, hi=4000)
    return s


# =====================================================================================
#  7. JADE : temple de montagne dans la brume, bambous, lanternes. 75 BPM.
# =====================================================================================
def song_jade():
    s = Song("jade", 75, 4, 20, 707)
    Em, C, Am, Bm, G_B, D6, Am11 = ((40, [59, 62, 64, 69]), (36, [59, 62, 64, 67]), (45, [55, 60, 62, 64]),
                                     (47, [57, 62, 64, 66]), (47, [55, 62, 64, 67]), (38, [57, 59, 62, 64]), (45, [55, 60, 62, 64]))
    prog = [Em, C, Em, D6] + [Em, C, Am, Bm, Em, C, G_B, Em] + [C, G_B, Am, Em, C, D6, Am11, Bm]
    sec = lambda b: 0 if b < 4 else 1 if b < 12 else 2
    # --- brume : nappe de voix « ou » et cordes très douces
    for bi, (root, v) in enumerate(prog):
        for m in v: s.put("mist", choir(mf(m), s.barlen + .3, s.r, .6, "u", 2, a=1., rel=2.), s.at(bi) - .2, .4)
        s.put("low", strings(mf(root), s.barlen + .2, s.r, .7, a=.8, rel=1.5, fc=700), s.at(bi), 1.)
        if sec(bi) == 2: s.put("low", strings(mf(root + 12), s.barlen, s.r, .5, a=.8, rel=1.5, fc=900), s.at(bi), .6)
    # --- guzheng : arpège pentatonique ondulant, glissandos aux charnières
    pent = [nm(x) for x in "e3 g3 a3 b3 d4 e4 g4 a4 b4 d5 e5 g5 a5 b5".split()]
    def near_pent(m): return min(pent, key=lambda p: abs(p - m))
    for bi, (root, v) in enumerate(prog):
        base = sorted(set(near_pent(x) for x in [root + 12, root + 19, v[1], v[2], v[3]]))
        seq = [0, 2, 1, 3, 2, 4, 3, 1] if bi % 2 == 0 else [0, 1, 3, 2, 4, 3, 2, 1]
        for k, i in enumerate(seq):
            if sec(bi) == 1 and k in (5, 7) and bi % 2: continue
            m = base[i % len(base)]
            s.put("zheng", ks(mf(m), 2.2, s.r, .8, .75, pick=.35), s.at(bi, k * .5) + s.r.normal(0, .006),
                  (.85 if k % 2 == 0 else .55), -.35 + .1 * (k % 3))
        if bi in (3, 11):
            for k, m in enumerate([p for p in pent if 52 <= p <= 83]):
                s.put("zheng", ks(mf(m), 1.4, s.r, .5, .9, pick=.4), s.at(bi, 3) + k * .045, .35 + .02 * k, -.6 + k * .1)
    # notes pincées avec glissé (geste typique de la cithare)
    for bi, x in ((5, "a4"), (9, "d5"), (14, "a4"), (17, "d5")):
        f = mf(nm(x)); bend = lambda t: np.where(t < .35, 1., 2 ** (2 / 12 * np.clip((t - .35) / .15, 0, 1)))
        s.put("zheng", ks(f, 2.5, s.r, .9, .8, bend=bend, pick=.3), s.at(bi, 3), .6, .3)
    # --- mélodie : xiao grave (A), dizi brillant (B)
    mA, _ = mel("b4:2 a4:.5 g4:.5 e4:1 | g4:1.5 a4:.5 b4:2 | d5:1.5 b4:.5 a4:1 g4:1 | a4:3 r:1 |"
                "b4:1 d5:1 e5:1.5 d5:.5 | b4:2 g4:1 a4:1 | b4:1 a4:.5 g4:.5 e4:1 d4:1 | e4:3 r:1")
    mB, _ = mel("g5:1.5 a5:.5 b5:2 | d6:1.5 b5:.5 a5:1 g5:1 | e5:1 g5:1 a5:1.5 g5:.5 | e5:3 d5:1 |"
                "g5:1 a5:1 b5:1 d6:1 | e6:2 d6:1 b5:1 | a5:1.5 g5:.5 e5:1 d5:1 | e5:2 r:2")
    s.play("xiao", lambda f, d, r, v: xiao(f, d + .2, r, v), mA, 4, .9, -.05, hum=.01)
    s.play("dizi", lambda f, d, r, v: dizi(f, d + .12, r, v), shift(mB, 0, -12), 12, .9, .05, hum=.01)
    s.play("dizi", lambda f, d, r, v: xiao(f, d + .2, r, v), shift(mB, 0, -24), 16, .35, -.2, hum=.01)
    # --- temple : poisson de bois, bol chantant, grande cloche, carillon de bambou
    for bi in range(4, 20):
        for p in range(4):
            if sec(bi) == 1 and p % 2: continue
            s.hit("wood", woodblock, s.at(bi, p), (.5 if p == 0 else .3), .25, f=640, dec=.06)
        if sec(bi) == 2:
            s.hit("drum", taiko, s.at(bi), .35, 0, f0=55, dec=.6)
            if bi % 2: s.hit("drum", taiko, s.at(bi, 2.5), .2, .1, f0=60, dec=.4)
    s.put("bell", kane(mf(45), 9, s.r, 1), s.at(0), .7, 0); s.put("bell", kane(mf(52), 7, s.r, .7), s.at(12), .5, .2)
    for bi, m in ((4, 64), (12, 69), (16, 64)): s.put("bell", bowl(mf(m), 10, s.r, .8), s.at(bi), .45, -.3)
    bpent = [nm(x) for x in "e5 g5 a5 b5 d6".split()]
    for k in range(40):
        bi = s.r.integers(0, 20)
        if sec(bi) == 1 and s.r.random() < .6: continue
        f = mf(int(s.r.choice(bpent)) - 12)
        t0 = s.at(bi, s.r.integers(0, 8) * .5)
        for j in range(s.r.integers(1, 4)): s.put("chime", bamboo(s.r, f * s.r.choice([1, 1.5, 2])), t0 + j * s.r.uniform(.05, .14), s.r.uniform(.15, .4), s.r.uniform(-.8, .8))
    L = s.L; a1 = bed_noise(s.r, L, 200, 1200) * (.6 + .4 * clfo(L, 2, 0)); a2 = bed_noise(s.r, L, 200, 1200) * (.6 + .4 * clfo(L, 3, 1.5))
    s.bus("air")[:] += np.stack([a1, a2])
    s.mix = dict(mist=dict(g=.20, lo=200, hi=5000, rev=.6, width=1.5), low=dict(g=.30, lo=50, hi=1500, rev=.3),
                 zheng=dict(g=.30, lo=120, rev=.35, dly=.12, bells=((3000, -2, .7),)), xiao=dict(g=.46, lo=180, rev=.45, dly=.12),
                 dizi=dict(g=.40, lo=250, hi=7000, rev=.42, dly=.15), wood=dict(g=.22, rev=.25, lo=300),
                 drum=dict(g=.40, rev=.25, hi=3000), bell=dict(g=.24, rev=.55), chime=dict(g=.15, rev=.5, lo=300),
                 air=dict(g=.008, rev=0))
    s.rev.update(t60=3.4, damp=5500, pre=.04); s.dly.update(time=.75 * s.spb, fb=.38, hi=3500)
    return s


# =====================================================================================
#  8. ARGENT : pleine lune monochrome, grues de papier sur une mer de nuages. Valse 72 BPM.
# =====================================================================================
def song_argent():
    s = Song("argent", 72, 3, 24, 808)
    prog = [(39, [55, 58, 62, 65]), (36, [58, 62, 63, 67]), (44, [60, 62, 63, 67]), (46, [55, 58, 63, 65]),
            (43, [58, 62, 65, 67]), (44, [55, 58, 60, 63]), (41, [56, 60, 63, 67]), (46, [56, 58, 62, 65]),
            (44, [56, 60, 63, 67]), (43, [58, 62, 65, 70]), (41, [56, 60, 63, 67]), (46, [55, 58, 63, 67]),
            (47, [54, 58, 63, 66]), (46, [55, 58, 63, 65]), (44, [56, 60, 62, 67]), (46, [56, 60, 63, 65]),
            (39, [55, 58, 62, 65]), (43, [53, 58, 62, 65]), (36, [58, 62, 63, 67]), (39, [56, 61, 65, 68]),
            (44, [56, 60, 63, 67]), (44, [56, 59, 63, 65]), (43, [58, 62, 63, 67]), (46, [56, 60, 63, 67])]
    sec = lambda b: b // 8
    # --- harpe : croches ascendantes/descendantes (6 par mesure)
    for bi, (root, v) in enumerate(prog):
        ch = sorted([root + 12] + v + [v[1] + 12])
        seq = [0, 1, 2, 3, 4, 5] if bi % 2 == 0 else [5, 4, 3, 2, 1, 2]
        for k, i in enumerate(seq):
            s.put("harp", ks(mf(ch[i]), 2.2, s.r, 1.1, .5, pick=.05), s.at(bi, k * .5) + s.r.normal(0, .004),
                  .8 if k == 0 else .55, -.4 + .8 * k / 5)
    # --- basse : violoncelles + main gauche du piano
    for bi, (root, v) in enumerate(prog):
        s.put("low", strings(mf(root), s.barlen + .1, s.r, .7, a=.5, rel=1.2, fc=900), s.at(bi), 1.)
        s.put("piano", piano(mf(root), 2.8 * s.spb, s.r, .55, bright=.6), s.at(bi), .6, -.2)
    # --- chœur et cordes (B et C)
    for bi, (root, v) in enumerate(prog):
        if sec(bi) >= 1:
            for m in v: s.put("choir", choir(mf(m), s.barlen + .4, s.r, .6, "o" if sec(bi) == 1 else "a", 3, a=.7, rel=1.4), s.at(bi) - .1, .5)
        if sec(bi) == 2:
            for m in v: s.put("str", strings(mf(m + 12), s.barlen + .2, s.r, .6, a=.5, rel=1.2, fc=3000), s.at(bi), .4)
    # --- mélodies
    mA, _ = mel("g5:1.5 f5:.5 bb5:1 | g5:2 d5:1 | c5:1.5 d5:.5 eb5:1 | f5:3 | g5:1.5 f5:.5 bb5:1 | c6:2 bb5:1 | ab5:1.5 g5:.5 eb5:1 | f5:2 eb5:.5 d5:.5")
    mB, _ = mel("eb5:1 c6:2 | bb5:1.5 g5:.5 f5:1 | g5:2 ab5:1 | g5:3 | bb5:2 gb5:1 | f5:3 | eb5:1 g5:1 c6:1 | bb5:2 ab5:1")
    mC, _ = mel("g5:1.5 f5:.5 bb5:1 | d6:2 bb5:1 | eb6:1.5 d6:.5 bb5:1 | db6:1.5 c6:.5 bb5:1 | c6:3 | cb6:1.5 bb5:.5 ab5:1 | g5:3 | f5:2 r:1")
    pn = lambda f, d, r, v: piano(f, d + .4, r, v, rel=.4)
    s.play("piano", pn, mA, 0, .9, .1, hum=.006)
    s.play("piano", pn, shift(mA, 0, -12, .5), 0, .5, .1, hum=.006)
    s.play("lead", strings, mB, 8, .8, a=.25, rel=.9, fc=3200)
    s.play("glock", lambda f, d, r, v: glock(f, 2.5, r, v), shift(mB, 0, 12), 8, .32, .35)
    s.play("lead", strings, mC, 16, .8, a=.2, rel=1., fc=3600)
    s.play("piano", pn, mC, 16, .75, .1, hum=.006)
    s.play("lead", strings, shift(mC, 0, -12), 16, .45, a=.25, rel=1., fc=2500)
    # scintillements de glockenspiel (lune) dans A
    sp, _ = mel("r:2 bb6:1 | r:3 | r:2 g6:1 | r:2 f6:.5 g6:.5 | r:2 d7:1 | r:3 | r:2 eb7:1 | r:3")
    s.play("glock", lambda f, d, r, v: glock(f, 2.5, r, v), sp, 0, .25, -.3)
    # timbale douce et cymbale avant la section C, puis grand calme pour reboucler
    for k in range(12): s.hit("perc", tom, s.at(15, k * .25), .1 + .25 * (k / 12) ** 2, 0, f=70, dec=.4)
    s.put("perc", swell(s.r, 3 * s.spb), s.at(15), .25, 0); s.hit("perc", crash, s.at(16), .15, .2)
    s.hit("perc", tom, s.at(16), .5, 0, f=62, dec=.8); s.hit("perc", tom, s.at(8), .3, 0, f=62, dec=.8)
    L = s.L; a1 = bed_noise(s.r, L, 1500, 7000) * (.5 + .5 * clfo(L, 2, 0)); a2 = bed_noise(s.r, L, 1500, 7000) * (.5 + .5 * clfo(L, 2, 2))
    s.bus("air")[:] += np.stack([a1, a2])
    s.mix = dict(harp=dict(g=.28, lo=100, rev=.35, bells=((2500, -1.5, .8),)), low=dict(g=.30, lo=40, hi=1500, rev=.3),
                 piano=dict(g=.40, lo=60, rev=.35), choir=dict(g=.22, lo=200, rev=.55, width=1.4),
                 str=dict(g=.16, lo=200, rev=.5, width=1.3), lead=dict(g=.36, lo=150, rev=.45),
                 glock=dict(g=.20, rev=.5, dly=.2), perc=dict(g=.35, rev=.4), air=dict(g=.004, rev=.2))
    s.rev.update(t60=3.2, damp=6500, pre=.035); s.dly.update(time=1 * s.spb, fb=.35, hi=4000)
    return s


SONGS = dict(codec=song_codec, arctique=song_arctique, ecarlate=song_ecarlate, sakura=song_sakura,
             ambre=song_ambre, neon=song_neon, jade=song_jade, argent=song_argent)


# =====================================================================================
#  Export, analyse, aperçus
# =====================================================================================
def encode_ogg(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = np.ascontiguousarray(x.T.astype("<f4")).tobytes()
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", str(QUALITY), path], input=data, check=True)

def decode(path):
    p = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "f32le", "-ac", "2", "-ar", str(SR), "-"],
                       capture_output=True, check=True)
    return np.frombuffer(p.stdout, "<f4").reshape(-1, 2).T.astype(float)

def analyse(x, dur=None):
    """Mesures : durée, sonie, crête, facteur de crête, spectre par bandes, raccord de boucle."""
    L = x.shape[1]
    out = dict(frames=L, seconds=L / SR, integer=(L % SR == 0))
    out["lufs"] = round(lufs(x), 2)
    pk = np.abs(x).max(); out["peak_db"] = round(20 * np.log10(pk + 1e-12), 2)
    rms = np.sqrt((x ** 2).mean()); out["crest_db"] = round(20 * np.log10(pk / (rms + 1e-12)), 1)
    X = np.abs(np.fft.rfft(x.mean(0))) ** 2; f = np.fft.rfftfreq(L, 1 / SR); tot = X.sum()
    bands = [(20, 60), (60, 250), (250, 1000), (1000, 4000), (4000, 10000), (10000, 22050)]
    out["bands_db"] = {f"{a}-{b}": round(10 * np.log10(X[(f >= a) & (f < b)].sum() / tot + 1e-12), 1) for a, b in bands}
    d = np.abs(np.diff(x, axis=1)).max(0)
    seam = np.abs(x[:, 0] - x[:, -1]).max()
    hp = fft_filter(x, lo=8000, n=4); e = (hp ** 2).sum(0)
    w = ns(.005); seam_e = np.concatenate([e[-w:], e[:w]]).mean(); typ_e = e.mean()
    out["seam_jump"] = float(seam); out["diff_p999"] = float(np.percentile(d, 99.9)); out["diff_max"] = float(d.max())
    out["seam_hf_ratio_db"] = round(10 * np.log10((seam_e + 1e-15) / (typ_e + 1e-15)), 1)
    # pire rapport HF local sur toute la boucle (pour situer le raccord parmi les attaques normales)
    blk = e[: (L // w) * w].reshape(-1, w).mean(1)
    out["hf_blocks_p99_db"] = round(10 * np.log10(np.percentile(blk, 99) / (typ_e + 1e-15)), 1)
    return out

def preview(x, path, pre=12., length=40., gain_db=5.):
    """Extrait d'écoute : les `pre` dernières secondes de la boucle puis son début (raccord audible)."""
    L = x.shape[1]; a = int((L / SR - pre) * SR); n = int(length * SR)
    idx = (np.arange(n) + a) % L; y = x[:, idx] * 10 ** (gain_db / 20)
    fi = ns(1.); fo = ns(2.5)
    y[:, :fi] *= np.sin(np.linspace(0, np.pi / 2, fi)) ** 2; y[:, -fo:] *= np.cos(np.linspace(0, np.pi / 2, fo)) ** 2
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    "-c:a", "libmp3lame", "-q:a", "2", path], input=np.ascontiguousarray(y.T.astype("<f4")).tobytes(), check=True)

def build(th, out_dir, previews):
    s = SONGS[th]()
    x, levels = render(s)
    path = os.path.join(out_dir, f"{th}.ogg"); encode_ogg(path, x)
    dec = decode(path)
    res = dict(theme=th, bpm=s.bpm, bars=s.bars, loop_s=s.dur, size_kb=round(os.path.getsize(path) / 1024, 1),
               pcm=analyse(x), ogg=analyse(dec), bus_rms_db={k: round(v, 1) for k, v in levels.items()})
    if previews: preview(dec, os.path.join(PREVIEW_DIR, f"{th}.mp3"))
    return res

if __name__ == "__main__":
    args = [a for a in sys.argv[1:]]
    out_dir = os.path.join(ROOT, "music"); jobs = 4; previews = False; check = False
    if "--out" in args: i = args.index("--out"); out_dir = args[i + 1]; del args[i:i + 2]
    if "--jobs" in args: i = args.index("--jobs"); jobs = int(args[i + 1]); del args[i:i + 2]
    if "--previews" in args: previews = True; args.remove("--previews")
    if "--check" in args: check = True; args.remove("--check")
    themes = args or THEMES
    if check:
        for th in themes:
            p = os.path.join(out_dir, f"{th}.ogg")
            print(th, json.dumps(analyse(decode(p)), ensure_ascii=False))
        sys.exit(0)
    from multiprocessing import Pool
    with Pool(min(jobs, len(themes))) as pool:
        results = pool.starmap(build, [(th, out_dir, previews) for th in themes])
    tot = 0
    for r in results:
        tot += r["size_kb"]; o = r["ogg"]
        print(f"{r['theme']:<9} {r['bpm']:>5} BPM {r['bars']:>3} mes. {r['loop_s']:>3} s  {r['size_kb']:>7.1f} Ko  "
              f"{o['lufs']:>6.1f} LUFS  crête {o['peak_db']:>5.1f} dBFS  facteur de crête {o['crest_db']:>4.1f} dB  "
              f"raccord {o['seam_jump']:.4f} (p99.9 {o['diff_p999']:.4f})  HF raccord {o['seam_hf_ratio_db']:+.1f} dB")
        print("          bandes", r["ogg"]["bands_db"])
        print("          bus   ", r["bus_rms_db"])
        if not (o["integer"] and o["frames"] == r["loop_s"] * SR):
            print("          ATTENTION : longueur décodée", o["frames"], "au lieu de", r["loop_s"] * SR)
    print(f"Total : {tot:.1f} Ko ({tot / 1024:.2f} Mo)")
