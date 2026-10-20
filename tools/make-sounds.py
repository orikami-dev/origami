#!/usr/bin/env python3
# Génération des sons d'interface et des musiques d'ambiance (synthèse pure, sans échantillon)
import numpy as np, subprocess, os

SR = 44100
ROOT = os.environ.get("ORIGAMI_ASSETS", "/mnt/project-files/foldplay/app/assets")
THEMES = ["codec", "arctique", "ecarlate", "sakura", "ambre"]
DUR = 64                      # durée des boucles musicales (s)
L = DUR * SR                  # longueur en échantillons

def mf(m): return 440.0 * 2 ** ((m - 69) / 12)      # midi -> Hz
def T(n): return np.arange(n) / SR
def snap(f): return round(f * DUR) / DUR            # fréquence à nombre entier de cycles sur la boucle

# ---------- filtres / effets ----------
def filt(x, lo=None, hi=None, n=2):
    # filtre zéro-phase par FFT (circulaire, donc sans raccord sur les boucles)
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR); m = np.ones_like(f)
    if lo: m *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** (2 * n))
    if hi: m *= 1 / np.sqrt(1 + (f / hi) ** (2 * n))
    return np.fft.irfft(X * m, len(x))

def reverb(x, t60, wet, seed, damp=6000):
    # réverbération par convolution circulaire avec un bruit exponentiel
    Ln = x.shape[-1]; r = np.random.default_rng(seed); out = np.zeros_like(x)
    ni = int(min(t60 * 1.3 * SR, Ln - 1)); t = np.arange(ni) / SR
    for c in range(x.shape[0]):
        ir = r.standard_normal(ni) * np.exp(-6.9 * t / t60) * np.minimum(1, t / 0.01)
        ir = filt(ir, hi=damp, n=1); ir /= np.sqrt((ir ** 2).sum())
        out[c] = np.fft.irfft(np.fft.rfft(x[c]) * np.fft.rfft(ir, Ln), Ln)
    return x * (1 - wet) + out * wet

def echo(x, d, fb, n):
    y = x.copy()
    for k in range(1, n + 1): y += fb ** k * np.roll(x, int(d * SR * k), axis=-1)
    return y

def wow(x, depth=16, cyc=37):
    # léger flottement de bande, périodique donc sans raccord
    n = np.arange(L); p = (n + depth * np.sin(2 * np.pi * cyc * n / L)) % L
    i = np.floor(p).astype(int); fr = p - i
    return x[..., i] * (1 - fr) + x[..., (i + 1) % L] * fr

def lfo(c, ph=0): return .75 + .25 * np.sin(2 * np.pi * c * np.arange(L) / L + ph)

# ---------- briques sonores ----------
def endfade(y, s=.02):
    k = min(int(s * SR), len(y) // 3); y[-k:] *= np.cos(np.linspace(0, np.pi / 2, k)) ** 2; return y

def ad(n, a, tau):
    t = T(n); return np.sin(np.minimum(1, t / max(a, 1e-4)) * np.pi / 2) ** 2 * np.exp(-t / tau)

def voice(f, dur, parts, a=.004, tau=.3):
    # somme de partiels (ratio, amplitude, multiplicateur de decay)
    n = int(dur * SR); t = T(n); y = np.zeros(n)
    for r_, amp, tm in parts:
        if f * r_ < SR / 2.2: y += amp * np.sin(2 * np.pi * f * r_ * t) * np.exp(-t / (tau * tm))
    return endfade(y * np.sin(np.minimum(1, t / a) * np.pi / 2) ** 2)

BELL = [(1, 1, 1), (2.76, .45, .5), (5.4, .2, .3), (8.93, .1, .2)]
BOX = [(1, 1, 1), (2, .2, .4), (3, .07, .3), (4.01, .08, .15), (5.4, .03, .1)]
def bell(f, dur, tau): return voice(f, dur, BELL, .002, tau)
def box(f, dur, tau): return voice(f, dur, BOX, .001, tau)

def pulse(f, dur, duty=.5):
    return np.where((f * T(int(dur * SR))) % 1 < duty, 1., -1.)

def pluck(f, dur, r, hl=.35):
    # Karplus-Strong accordé par ré-échantillonnage
    N = max(int(SR / f - .5), 2); fa = SR / (N + .5)
    n = int(dur * SR * fa / f) + N + 3; dec = .5 ** (1 / (f * hl))
    y = np.zeros(n + 1); ex = np.convolve(r.uniform(-1, 1, N), [.5, .5], 'same'); y[1:N + 1] = ex
    for s in range(N + 1, n + 1, N):
        e = min(s + N, n + 1); k = e - s
        y[s:e] = dec * .5 * (y[s - N:s - N + k] + y[s - N - 1:s - N - 1 + k])
    m = int(dur * SR); o = np.interp(np.arange(m) * fa / f, np.arange(n), y[1:])
    o /= abs(o).max() + 1e-9
    return endfade(o * np.sin(np.minimum(1, T(m) / .001) * np.pi / 2))

def taiko(dur, r, f0=70):
    n = int(dur * SR); t = T(n)
    ph = np.cumsum(2 * np.pi * f0 * (1 + 1.2 * np.exp(-t / .04)) / SR)
    y = np.sin(ph) * np.exp(-t / (dur * .3)) + .5 * filt(r.standard_normal(n), hi=250) * np.exp(-t / .03) * 8
    return endfade(y * np.sin(np.minimum(1, t / .003) * np.pi / 2))

def rhodes(f, dur, vel=.8, tau=.6):
    n = int(dur * SR); t = T(n)
    idx = vel * 2.2 * np.exp(-t / (tau * .5)) + .25
    y = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t))
    if f * 14 < 18000: y += .15 * vel * np.sin(2 * np.pi * f * 14 * t) * np.exp(-t / .03)
    y *= (1 + .08 * np.sin(2 * np.pi * 4.5 * t)) * ad(n, .003, tau)
    return endfade(y)

def padnote(f, dur, r, hmax=6, det=(.9985, 1, 1.0015)):
    n = int(dur * SR); t = T(n); y = np.zeros(n)
    for d in det:
        for k in range(1, hmax + 1):
            if f * d * k < 8000: y += np.sin(2 * np.pi * f * d * k * t + r.uniform(0, 6.28)) / k
    return y * np.sin(np.pi * np.arange(n) / n) ** 2 / len(det)

def put(b, s, t, g=1., pan=.5):
    # ajout circulaire dans le tampon stéréo (les queues rebouclent au début)
    idx = (np.arange(len(s)) + int(t * SR)) % L
    b[0][idx] += g * s * np.cos(pan * np.pi / 2); b[1][idx] += g * s * np.sin(pan * np.pi / 2)

def cnoise(r, lo, hi): return filt(r.standard_normal(L), lo, hi)

# ---------- sons UI ----------
def mixl(n, items):
    y = np.zeros(n)
    for t, s, g in items:
        i = int(t * SR); m = min(len(s), n - i)
        if m > 0: y[i:i + m] += g * s[:m]
    return y

def fin(x, maxdur, peak=.501):
    # durée fixe, fondu d'entrée/sortie, normalisation à -6 dBFS
    n = int(maxdur * SR * .985); y = np.zeros(n); m = min(n, len(x)); y[:m] = x[:m]
    fo = int(min(.03, maxdur * .25) * SR); fi = int(.002 * SR)
    y[:fi] *= np.sin(np.linspace(0, np.pi / 2, fi)) ** 2
    y[-fo:] *= np.cos(np.linspace(0, np.pi / 2, fo)) ** 2
    return y * peak / abs(y).max()

def srev(x, n, t60, wet, seed=5):
    z = np.zeros(n); z[:min(n, len(x))] = x[:n]; return reverb(z[None], t60, wet, seed)[0]

DUR_UI = dict(move=.08, select=.22, back=.22, open=.55, launch=1.6)
def N(k): return int(DUR_UI[k] * SR)

def sfx_codec(r):
    def blip(f, d, duty=.5, tau=.05): return filt(pulse(f, d, duty), hi=4500) * ad(int(d * SR), .002, tau)
    def stat(d, g=1): n = int(d * SR); return filt(r.standard_normal(n), 1200, 4500) * ad(n, .004, d * .35) * g
    o = {}
    o['move'] = blip(1900, .05, .25, .02)
    o['select'] = mixl(N('select'), [(0, blip(1200, .06, .5, .03), 1), (.06, blip(1800, .08, .5, .04), 1)])
    o['back'] = mixl(N('back'), [(0, blip(1500, .06, .5, .03), 1), (.06, blip(900, .08, .5, .04), 1)])
    o['open'] = mixl(N('open'), [(0, stat(.06, .6), 1)] + [(.05 + .05 * i, blip(f, .16, .5, .08), 1) for i, f in enumerate((660, 990, 1320))])
    ar = [(.08 + .09 * i, blip(f, .22, .25, .08), .6 + .08 * i) for i, f in enumerate((220, 330, 440, 660, 880, 1320))]
    o['launch'] = mixl(N('launch'), [(0, stat(.08, 1), 1), (.7, blip(1320, .45, .5, .25), .8), (.1, stat(1.0, .35), 1)] + ar)
    return o

def sfx_arctique(r):
    o = {}
    o['move'] = voice(2637, .05, [(1, 1, 1), (2.76, .3, .5)], .002, .015)
    o['select'] = mixl(N('select'), [(0, bell(1568, .14, .06), 1), (.03, bell(2349, .11, .05), .8)])
    o['back'] = mixl(N('back'), [(0, bell(2349, .12, .05), .9), (.045, bell(1568, .1, .06), 1)])
    air = filt(r.standard_normal(N('open')), 4000, 9000) * np.sin(np.linspace(0, np.pi, N('open'))) ** 2 * .06
    o['open'] = srev(mixl(N('open'), [(0, bell(1175, .3, .12), 1), (.07, bell(1480, .28, .12), .9), (.14, bell(1760, .25, .14), .9)]) + air, N('open'), .3, .3)
    n = N('launch'); air = filt(r.standard_normal(n), 3500, 9000) * np.sin(np.linspace(0, np.pi, n)) ** 2 * .12
    notes = [74, 78, 81, 86, 90, 93]
    it = [(.09 * i, bell(mf(m), .6, .25), .8) for i, m in enumerate(notes)] + [(.6, bell(mf(m), .55, .35), .7) for m in (86, 93)]
    o['launch'] = srev(mixl(n, it) + air, n, .5, .3)
    return o

def sfx_ecarlate(r):
    p = lambda m, d, hl=.15: pluck(mf(m), d, r, hl)
    o = {}
    o['move'] = p(74, .055, .05)
    o['select'] = mixl(N('select'), [(0, p(67, .14, .1), 1), (.04, p(74, .1, .1), 1)])
    o['back'] = mixl(N('back'), [(0, p(74, .12, .1), 1), (.045, p(62, .1, .1), 1)])
    o['open'] = mixl(N('open'), [(0, taiko(.38, r, 65), .5)] + [(.04 * i, p(m, .3, .25), .8) for i, m in enumerate((62, 69, 74))])
    n = N('launch')
    run = [(.12 + .08 * i, p(m, .6, .35), .8) for i, m in enumerate((62, 67, 69, 74, 81))]
    o['launch'] = srev(mixl(n, [(0, taiko(.5, r, 62), 1.1)] + run), n, .6, .2)
    return o

def sfx_sakura(r):
    o = {}
    o['move'] = box(mf(88), .05, .02)
    o['select'] = mixl(N('select'), [(0, box(mf(88), .14, .08), 1), (.05, box(mf(91), .1, .07), .9)])
    o['back'] = mixl(N('back'), [(0, box(mf(91), .12, .07), .9), (.05, box(mf(86), .1, .08), 1)])
    o['open'] = srev(mixl(N('open'), [(.06 * i, box(mf(m), .3, .15), 1) for i, m in enumerate((84, 88, 91, 93))]), N('open'), .3, .3)
    n = N('launch'); sc = [72, 74, 76, 79, 81, 84, 86, 88]
    it = [(.08 * i, box(mf(m), .5, .25), .9) for i, m in enumerate(sc)] + [(.7, box(mf(m), .45, .3), .6) for m in (96, 100)]
    o['launch'] = srev(mixl(n, it), n, .5, .3)
    return o

def sfx_ambre(r):
    def warm(x): return np.tanh(1.5 * filt(x, hi=7000)) 
    o = {}
    o['move'] = rhodes(mf(79), .055, .6, .03)
    o['select'] = mixl(N('select'), [(0, rhodes(mf(72), .14, .7, .08), 1), (.045, rhodes(mf(79), .1, .7, .08), .9)])
    o['back'] = mixl(N('back'), [(0, rhodes(mf(79), .12, .7, .07), .9), (.045, rhodes(mf(72), .1, .7, .08), 1)])
    o['open'] = warm(mixl(N('open'), [(.06 * i, rhodes(mf(m), .34, .7, .25), 1) for i, m in enumerate((60, 64, 67, 71, 74))]))
    n = N('launch'); t = T(int(.5 * SR))
    scope = np.sin(2 * np.pi * np.cumsum(300 + 1200 * (t / .5) ** 2) / SR) * np.sin(np.pi * t / .5) ** 2 * .08
    it = [(.1 * i, rhodes(mf(m), .9, .8, .5), .9) for i, m in enumerate((50, 57, 65, 69, 72, 76))] + [(.05, scope, 1)]
    o['launch'] = srev(warm(mixl(n, it)), n, .5, .2)
    return o

SFX = dict(codec=sfx_codec, arctique=sfx_arctique, ecarlate=sfx_ecarlate, sakura=sfx_sakura, ambre=sfx_ambre)

# ---------- musiques (tampon circulaire de 64 s) ----------
def m_codec(r):
    b = np.zeros((2, L)); t = T(L)
    for f, g in ((55, .5), (110, .2), (82.5, .15)):          # drone grave en la
        for c, dt in ((0, 1.), (1, 1.003)):
            fs = snap(f * dt); b[c] += g * (np.sin(2 * np.pi * fs * t) + .35 * np.sin(4 * np.pi * fs * t)) * lfo(3 + c, c)
    for i, ch in enumerate([[57, 60, 64], [53, 57, 60], [50, 53, 57], [52, 55, 59]] * 2):
        for m in ch: put(b, filt(padnote(mf(m), 11, r), hi=1100), i * 8 - 1.5, .09, r.uniform(.3, .7))
    bl = np.zeros((2, L)); pent = [69, 72, 74, 76, 79]
    for k in range(32):
        if r.random() < .6:
            s = filt(pulse(mf(r.choice(pent)), .09, .25), hi=3500) * ad(int(.09 * SR), .003, .04)
            put(bl, s, k * 2 + r.choice([0, .5]), .1, r.uniform(.25, .75))
    b += echo(bl, .75, .45, 4)
    for k in range(14):                                       # bouffées de parasites radio
        d = r.uniform(.12, .4); n = int(d * SR)
        g = np.clip(filt(r.standard_normal(n), hi=40) * 8 + .5, 0, 1)
        put(b, filt(r.standard_normal(n), 1500, 5000) * np.sin(np.pi * np.arange(n) / n) ** 2 * g, r.uniform(0, DUR), .05, r.uniform(.2, .8))
    b[0] += .012 * cnoise(r, 3000, 9000); b[1] += .012 * cnoise(r, 3000, 9000)
    for i, m in enumerate([33, 29, 26, 28] * 2):              # basse ronde sous chaque accord
        put(b, filt(padnote(mf(m), 8.5, r, 4), hi=300), i * 8 - .3, .22, .5)
    for k in range(32):                                       # pulsation sourde, comme un cœur à l'écoute
        put(b, taiko(.6, r, 48) * .6, k * 2, .12, .5)
    return reverb(b, 1.5, .25, 11)

def m_arctique(r):
    b = np.zeros((2, L)); t = T(L)
    for i, ch in enumerate([[62, 66, 69, 73], [59, 62, 66, 69], [55, 59, 62, 66], [57, 62, 64, 69]] * 2):
        for m in ch:
            s = padnote(mf(m), 12, r, 3) + .4 * padnote(mf(m + 12), 12, r, 2)
            put(b, s, i * 8 - 2, .07, r.uniform(.2, .8))
    for c in (0, 1):
        b[c] += .07 * cnoise(r, 2500, 9000) * (.5 + .5 * np.sin(2 * np.pi * (2 + c) * np.arange(L) / L + c * 2)) ** 2
        b[c] += .2 * np.sin(2 * np.pi * snap(146.8 * (1 + .002 * c)) * t) * lfo(2, c)
    bl = np.zeros((2, L)); sc = [74, 76, 78, 81, 83, 86]
    for k in range(22):
        m = int(r.choice(sc)); t0 = k * 2.9 + r.uniform(0, 1.2)
        put(bl, bell(mf(m), 6, 2.0), t0, .12, r.uniform(.15, .85))
        if r.random() < .3: put(bl, bell(mf(m - 7), 6, 2.0), t0 + .25, .08, r.uniform(.15, .85))
    b += echo(bl, 1.5, .4, 3)
    for i, m in enumerate([38, 35, 31, 33] * 2):              # basses tenues sous la glace
        put(b, filt(padnote(mf(m), 9, r, 4), hi=350), i * 8 - .5, .2, .5)
    return reverb(b, 4, .5, 12, 8000)

def shaku(f, dur, r, vib=.012, scoop=True):
    # shakuhachi : souffle accordé, attaque en « meri » (le son monte depuis un demi-ton plus bas),
    # vibrato qui s'élargit en fin de note, bruit de souffle filtré autour de la hauteur
    n = int(dur * SR); t = T(n)
    bend = (2 ** (-1 / 12) - 1) * np.exp(-t / .09) if scoop else 0
    vd = vib * np.minimum(1, t / max(dur * .6, .01)) ** 1.5
    fi = f * (1 + bend + vd * np.sin(2 * np.pi * 5.2 * t))
    ph = 2 * np.pi * np.cumsum(fi) / SR
    tone = np.sin(ph) + .28 * np.sin(2 * ph) + .1 * np.sin(3 * ph) + .04 * np.sin(4 * ph)
    breath = filt(r.standard_normal(n), f * .8, f * 3.5) * .35 + filt(r.standard_normal(n), 2500, 7000) * .06
    env = np.minimum(1, t / .12) ** 1.6 * np.minimum(1, (dur - t) / .25).clip(0, 1) ** 1.2
    puff = np.exp(-t / .05) * .5
    return endfade((tone * (.85 + .15 * np.sin(2 * np.pi * .7 * t)) + breath * (1 + 2 * puff)) * env)

def kotoq(f, dur, r, hl=.6, bright=.35):
    # koto : corde pincée (Karplus-Strong) + attaque d'onglet + léger glissé de la main gauche
    y = pluck(f, dur, r, hl); n = len(y); t = T(n)
    pick = filt(r.standard_normal(n), 1500, 6000) * np.exp(-t / .006) * bright
    return endfade(y + pick)

def kane(f, dur):
    # petite cloche de temple
    return voice(f, dur, [(1, 1, 1), (2.4, .5, .5), (3.9, .3, .35), (5.6, .15, .25)], .001, dur * .35)

def ka(r, d=.05):
    # « ka » : frappe sur le cerclage du taiko
    n = int(d * SR); t = T(n)
    return endfade(filt(r.standard_normal(n), 1800, 6000) * np.exp(-t / .012) + .5 * np.sin(2 * np.pi * 900 * t) * np.exp(-t / .01))

def m_ecarlate(r):
    # « Éventail écarlate » : 90 BPM, 24 mesures (64 s). Koto en ostinato, shakuhachi qui chante
    # une phrase en trois parties (A, B, A'), taikos et ka, cordes graves tenues. Gamme in sur ré.
    b = np.zeros((2, L)); t = T(L)
    beat = 60 / 90; bar = 4 * beat
    D = 50                                                    # ré 3
    sc = [0, 1, 5, 7, 8]                                      # ré mib sol la sib
    def deg(k, o=0): return D + 12 * (o + k // 5) + sc[k % 5]
    # cordes graves tenues (accords ré-la, sib-fa, sol-ré, la-mi) toutes les 4 mesures
    roots = [(38, 45), (34, 41), (31, 38), (33, 40)]
    for i in range(6):
        lo, hi = roots[i % 4]
        for m, g in ((lo, .1), (hi, .07), (lo + 12, .05)):
            put(b, filt(padnote(mf(m), 4 * bar + 2, r, 5), hi=1400), i * 4 * bar - 1, g, r.uniform(.35, .65))
    # bourdon très doux pour l'assise
    for c, dt in ((0, 1.), (1, 1.002)):
        b[c] += .08 * filt(np.sin(2 * np.pi * snap(36.7 * dt) * t), hi=200) * lfo(4 + c, c)
    # koto : ostinato en croches, motif qui suit l'accord
    pat = [0, 2, 4, 2, 5, 4, 2, 1]
    off = [0, -2, -3, -1]
    for bi in range(24):
        o = off[(bi // 4) % 4]
        for k, p in enumerate(pat):
            if bi % 8 == 7 and k >= 6: continue               # respiration en fin de phrase
            m = deg(p + o + 5)
            put(b, kotoq(mf(m), 1.6, r, .45, .25), bi * bar + k * beat / 2, .16 if k % 2 == 0 else .11, .3 + .05 * (k % 4))
    # basse de koto sur les temps forts
    for bi in range(24):
        lo = roots[(bi // 4) % 4][0]
        put(b, kotoq(mf(lo), 3, r, .9, .15), bi * bar, .26, .5)
        if bi % 2: put(b, kotoq(mf(lo + 7), 2, r, .7, .15), bi * bar + 2.5 * beat, .14, .55)
    # shakuhachi : phrase A (mesures 0-7), B plus haute (8-15), A' ornée (16-23)
    A = [(5, 0, 3), (7, 3, 1), (6, 4, 2), (5, 6, 2), (4, 8, 3), (2, 11, 1), (3, 12, 4)]
    B = [(7, 0, 2), (8, 2, 2), (9, 4, 3), (8, 7, 1), (7, 8, 2), (6, 10, 2), (5, 12, 4)]
    for sect, phrase, lift in ((0, A, 0), (8, B, 0), (16, A, 1)):
        for k, (d, st, ln) in enumerate(phrase):
            t0 = sect * bar + st * beat * 2 + .02
            dur = ln * beat * 2 + .3
            put(b, shaku(mf(deg(d, 1)), dur, r), t0, .22, .45 + .1 * np.sin(k))
            if lift and k in (1, 4):                          # ornement : note de passage brève
                put(b, shaku(mf(deg(d + 1, 1)), beat * .7, r, scoop=False), t0 - beat * .4, .14, .5)
    # taiko : « don » sur le 1, « don-don » en fin de mesure, ka sur les contretemps
    for bi in range(24):
        t0 = bi * bar
        put(b, taiko(1.4, r, 58), t0, .42, .5)
        if bi % 2 == 1:
            put(b, taiko(.9, r, 64), t0 + 3 * beat, .28, .42)
            put(b, taiko(.9, r, 64), t0 + 3.5 * beat, .24, .58)
        for k in (1, 3):
            put(b, ka(r), t0 + k * beat + beat / 2, .1, .3 if k == 1 else .7)
        if bi % 4 == 3: put(b, taiko(2.2, r, 46), t0 + 2 * beat, .35, .5)
    # cloches de temple aux changements de section
    for t0, m in ((0, 86), (8 * bar, 89), (16 * bar, 86)):
        put(b, kane(mf(m), 5), t0, .1, .3); put(b, kane(mf(m - 5), 5), t0 + .04, .07, .7)
    # souffle de vent léger, comme un éventail
    for c in (0, 1): b[c] += .02 * cnoise(r, 600, 3000) * lfo(3 + c, c * 2)
    return reverb(b, 2.4, .32, 13)

def m_sakura(r):
    b = np.zeros((2, L)); t = T(L)
    cps = [[60, 64, 67, 74], [57, 60, 64, 67], [53, 57, 60, 67], [55, 59, 62, 64]] * 2
    arp = [[60, 64, 67, 62], [57, 60, 64, 67], [57, 60, 62, 67], [55, 57, 62, 64]] * 2
    for i, ch in enumerate(cps):
        for m in ch: put(b, padnote(mf(m), 11, r, 2), i * 8 - 1.5, .1, r.uniform(.3, .7))
        notes = sorted(arp[i]); order = [0, 1, 2, 3, 2, 1] * 3
        for s in range(16):
            if r.random() < .78:
                m = notes[order[s]] + 12 + (12 if r.random() < .15 else 0)
                put(b, box(mf(m), 3, .9), i * 8 + s * .5, .14, r.uniform(.3, .7))
        for tt in (0, 3, 5):
            m = int(r.choice([79, 81, 84, 86, 88])); put(b, box(mf(m + 12 * (m < 84)), 3.5, 1.5), i * 8 + tt + .25, .13, r.uniform(.35, .65))
    for c in (0, 1): b[c] += .015 * cnoise(r, 4000, 10000)
    for i, m in enumerate([36, 33, 29, 31] * 2):              # basse douce sur les temps 1 et 3
        for k in (0, 4):
            put(b, filt(rhodes(mf(m), 3.5, .35, 1.4), hi=500), i * 8 + k, .2, .5)
    return reverb(echo(b, .75, .25, 2), 2.5, .35, 14)

def m_ambre(r):
    b = np.zeros((2, L)); t = T(L)
    voic = [[65, 69, 72, 76], [65, 71, 74, 76], [64, 67, 71, 74], [67, 71, 72, 76]] * 2
    bass = [38, 43, 48, 45] * 2
    for i, ch in enumerate(voic):
        for j, m in enumerate(ch):
            put(b, rhodes(mf(m), 5, .55, 1.6), i * 8 + .05 * j, .1, .35 + .1 * j)
            put(b, rhodes(mf(m), 4, .4, 1.2), i * 8 + 3.5 + .05 * j, .06, .35 + .1 * j)
            put(b, padnote(mf(m - 12), 11, r, 3), i * 8 - 1.5, .05, .5)
        put(b, rhodes(mf(bass[i]), 4, .4, 1.2), i * 8, .3, .5)
        put(b, rhodes(mf(bass[i] + 7), 3, .4, 1.0), i * 8 + 4, .22, .5)
    ml = np.zeros((2, L))
    for k in range(32):
        if r.random() < .5: put(ml, rhodes(mf(int(r.choice([72, 74, 76, 79, 81, 84]))), 3, .85, .8), k * 2 + r.choice([0, .75, 1.25]), .08, r.uniform(.3, .7))
    b += echo(ml, 1.0, .35, 3)
    for k in range(DUR):                                      # balai léger sur les temps 2 et 4
        if k % 2: n = int(.12 * SR); put(b, filt(r.standard_normal(n), 3000, 9000) * np.sin(np.pi * np.arange(n) / n) ** 2, k, .025, .5)
    b = reverb(b, 1.6, .2, 15)
    for c in (0, 1): b[c] += .01 * cnoise(r, 3000, 10000)
    return np.tanh(1.3 * wow(filt(b[0], hi=9000)[None].repeat(2, 0) * 0 + np.stack([filt(b[0], hi=9000), filt(b[1], hi=9000)])))

MUSIC = dict(codec=m_codec, arctique=m_arctique, ecarlate=m_ecarlate, sakura=m_sakura, ambre=m_ambre)

# ---------- export ----------
def body(x, k):
    # un peu de corps sous les bruits : écho grave court, plus marqué sur les gros événements
    n = len(x); t = T(n); f0 = dict(move=0, select=180, back=150, open=110, launch=70)[k]
    if f0 == 0: return x
    thump = np.sin(2 * np.pi * f0 * t) * np.exp(-t / .05) * np.minimum(1, t / .003)
    return x + thump * abs(x).max() * (.35 if k != 'launch' else .5)

def stereo_ui(x, k, seed):
    # petite pièce stéréo : chaque côté a sa propre réverbération (les bruits ne sonnent plus « plats »)
    n = len(x); wet = .12 if k == 'move' else .2
    t60 = dict(move=.12, select=.25, back=.25, open=.45, launch=.8)[k]
    pad = int(t60 * 1.4 * SR); z = np.concatenate([x, np.zeros(pad)])
    y = reverb(np.stack([z, z]), t60, wet, seed)[:, :n]       # pas de queue rebouclée au début
    fo = int(min(.04, n / SR * .25) * SR); y[:, -fo:] *= np.cos(np.linspace(0, np.pi / 2, fo)) ** 2
    y[0] = .92 * y[0] + .08 * y[1]; y[1] = .92 * y[1] + .08 * y[0]
    return y * .501 / abs(y).max()

def encode(path, x, ch, q):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2')
    data = (pcm if ch == 1 else pcm.T).tobytes()
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(SR), "-ac", str(ch), "-i", "-",
                    "-c:a", "libvorbis", "-q:a", str(q), path], input=data, check=True)

def master(x, peak_db):
    x = x - x.mean(axis=1, keepdims=True)
    x = np.stack([filt(c, 30, 14000) for c in x])
    return x * 10 ** (peak_db / 20) / abs(x).max()

def probe(p):
    try:
        return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                                    capture_output=True, text=True).stdout)
    except Exception: return float('nan')

if __name__ == "__main__":
    rows = []
    for ti, th in enumerate(THEMES):
        for k, x in SFX[th](np.random.default_rng(1000 + ti)).items():
            p = f"{ROOT}/sounds/{th}/{k}.ogg"
            encode(p, stereo_ui(fin(body(x, k), DUR_UI[k]), k, 300 + ti), 2, 6); rows.append(p)
        m = master(MUSIC[th](np.random.default_rng(2000 + ti)), -10)
        jump = abs(m[:, 0] - m[:, -1]).max(); typ = np.sqrt((np.diff(m, axis=1) ** 2).mean())
        print(f"{th}: saut boucle {jump:.2e}, diff rms typique {typ:.2e}, pic {20*np.log10(abs(m).max()):.1f} dBFS")
        p = f"{ROOT}/music/{th}.ogg"; encode(p, m, 2, 5); rows.append(p)
    tot = 0
    print(f"\n{'fichier':<70}{'durée (s)':>10}{'taille (Ko)':>12}")
    for p in rows:
        s = os.path.getsize(p); tot += s
        print(f"{p.replace(ROOT + '/', ''):<70}{probe(p):>10.3f}{s / 1024:>12.1f}")
    print(f"\nTotal: {tot / 1024:.1f} Ko ({tot / 1048576:.2f} Mo)")
