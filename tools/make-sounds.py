#!/usr/bin/env python3
# Génération des sons d'interface et des musiques d'ambiance (synthèse pure, sans échantillon)
import numpy as np, subprocess, os

SR = 44100
ROOT = "/mnt/project-files/foldplay/app/assets"
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

DUR_UI = dict(move=.06, select=.15, back=.15, open=.4, launch=1.2)
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
    return reverb(b, 4, .5, 12, 8000)

def m_ecarlate(r):
    b = np.zeros((2, L)); t = T(L)
    for f, g in ((73.4, .3), (110, .12)):                     # bourdon ré / la
        for c, dt in ((0, 1.), (1, 1.002)):
            b[c] += g * filt(np.sin(2 * np.pi * snap(f * dt) * t), hi=400) * lfo(2 + c, c)
    deg = [0, 1, 5, 7, 8]                                     # gamme in : ré mib sol la sib
    sc = [50 + 12 * o + d for o in range(3) for d in deg]
    pos = 9
    for i in range(8):
        k = 0
        while k < 7:
            pos = int(np.clip(pos + r.choice([-2, -1, -1, 0, 1, 1, 2, 3]), 0, len(sc) - 1))
            t0 = i * 8 + k * 1.1 + r.uniform(0, .5) + 0.3
            if r.random() < .8: put(b, pluck(mf(sc[pos]), 3, r, .5), t0, .3, r.uniform(.35, .65))
            if r.random() < .25:                              # petite montée rapide
                for j in range(1, 3):
                    pos = min(pos + 1, len(sc) - 1); put(b, pluck(mf(sc[pos]), 2.5, r, .5), t0 + .13 * j, .22, .5)
            k += 1 if r.random() < .5 else 2
        put(b, pluck(mf(38), 4, r, .8), i * 8, .4, .5)
        put(b, taiko(1.2, r, 55), i * 8, .45, .5); put(b, taiko(1.0, r, 55), i * 8 + 4, .2, .5)
    return reverb(b, 2.5, .3, 13)

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
            encode(p, fin(x, DUR_UI[k]), 1, 3); rows.append(p)
        m = master(MUSIC[th](np.random.default_rng(2000 + ti)), -10)
        jump = abs(m[:, 0] - m[:, -1]).max(); typ = np.sqrt((np.diff(m, axis=1) ** 2).mean())
        print(f"{th}: saut boucle {jump:.2e}, diff rms typique {typ:.2e}, pic {20*np.log10(abs(m).max()):.1f} dBFS")
        p = f"{ROOT}/music/{th}.ogg"; encode(p, m, 2, 1); rows.append(p)
    tot = 0
    print(f"\n{'fichier':<70}{'durée (s)':>10}{'taille (Ko)':>12}")
    for p in rows:
        s = os.path.getsize(p); tot += s
        print(f"{p.replace(ROOT + '/', ''):<70}{probe(p):>10.3f}{s / 1024:>12.1f}")
    print(f"\nTotal: {tot / 1024:.1f} Ko ({tot / 1048576:.2f} Mo)")
