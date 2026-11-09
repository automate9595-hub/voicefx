import numpy as np, subprocess, scipy.signal as sg
FS = 48000
def glottal(f0_track, rng, jitter=0.003, shimmer=0.02):
    """Fuente glotal suave (pulso sen^2 con asimetría) con jitter/shimmer; salida = derivada del flujo."""
    n = len(f0_track); out = np.zeros(n); phase = 0.0; amp = 1.0; op = 0.62
    for i in range(n):
        f = f0_track[i]
        if f <= 0: continue
        phase += f / FS
        if phase >= 1.0:
            phase -= 1.0; amp = 1 + shimmer * rng.standard_normal(); phase += jitter * rng.standard_normal() * f / FS * 8
        p = phase
        if p < op:
            q = p / op + 0.12 * np.sin(np.pi * p / op) ** 2
            out[i] = amp * np.sin(np.pi * min(q, 1.0)) ** 2
    return np.diff(out, prepend=0) * 60

def formants(x, F=((700, 90), (1220, 110), (2600, 160), (3300, 200))):
    y = x
    for fc, bw in F:
        r = np.exp(-np.pi * bw / FS); th = 2 * np.pi * fc / FS
        a = [1, -2 * r * np.cos(th), r * r]
        y = sg.lfilter([1 - r], a, y)
    return y
def vowel(f0_track, seed=1, F=None):
    rng = np.random.default_rng(seed)
    g = glottal(f0_track, rng)
    v = formants(g) if F is None else formants(g, F)
    v = v / (np.abs(v).max() + 1e-12) * 0.3
    v += 0.002 * rng.standard_normal(len(v)) * (f0_track > 0)   # respiración ~ -43 dB respecto al pico
    return v
def contour(segments, vib_hz=5.5, vib_cents=25, drift_cents=6, seed=2):
    """segments: [(dur_s, f0_hz or 0)] -> contorno con vibrato y deriva lenta; 0 = sin tono."""
    rng = np.random.default_rng(seed); tr = []
    for d, f in segments:
        tr.append(np.full(int(d * FS), float(f)))
    base = np.concatenate(tr); n = len(base); t = np.arange(n) / FS
    drift = np.cumsum(rng.standard_normal(n)); drift = sg.lfilter([1], [1, -0.99995], drift)
    drift = drift / (np.std(drift) + 1e-9) * drift_cents
    cents = vib_cents * np.sin(2 * np.pi * vib_hz * t) * np.minimum(1, t / 0.3) + drift
    return base * 2 ** (cents / 1200.0), cents
def run(x, args, name):
    x.astype(np.float32).tofile(f"/tmp/{name}_in.f32")
    r = subprocess.run(["/tmp/t_psola", f"/tmp/{name}_in.f32", f"/tmp/{name}_out.f32"] + args, capture_output=True, text=True)
    y = np.fromfile(f"/tmp/{name}_out.f32", dtype=np.float32).astype(np.float64)
    raw = np.fromfile(f"/tmp/{name}_out.f32.f0", dtype=np.float32)
    k = len(raw) // 2
    return y, raw[:k], raw[k:]
def f0_at(x, t, win=0.04, fmin=70, fmax=1000):
    """Estimador independiente SIN sesgo: función de diferencia (ventana fija) + interpolación parabólica (Hz)."""
    c = int(t * FS); W = int(win * FS); tmax = int(FS / fmin) + 2
    seg = x[c - W // 2: c - W // 2 + W + tmax]
    if len(seg) < W + tmax or np.sqrt(np.mean(seg[:W] ** 2)) < 1e-4: return 0.0
    seg = seg - seg[:W].mean()
    n = len(seg); nf = 1 << (n + W).bit_length()
    F = np.fft.rfft(seg, nf); G = np.fft.rfft(seg[:W], nf)
    r = np.fft.irfft(F * np.conj(G), nf)[:tmax]           # r[t] = sum_j seg[j]*seg[j+t]
    e = np.cumsum(seg ** 2); e0 = e[W - 1]
    et = np.array([e[W - 1 + t] - e[t - 1] if t > 0 else e[W - 1] for t in range(tmax)])
    d = e0 + et - 2 * r; cm = np.ones(tmax); run = np.cumsum(d[1:])
    cm[1:] = d[1:] * np.arange(1, tmax) / (run + 1e-12)
    lo, hi = int(FS / fmax), tmax - 2
    l = None
    for t in range(lo, hi):
        if cm[t] < 0.2 and cm[t] <= cm[t - 1] and cm[t] <= cm[t + 1]: l = t; break
    if l is None: return 0.0
    a, b, cc = d[l - 1], d[l], d[l + 1]; den = a - 2 * b + cc
    sh = 0.5 * (a - cc) / den if abs(den) > 1e-12 else 0
    return FS / (l + sh)
def cents(a, b): return 1200 * np.log2(a / b)
