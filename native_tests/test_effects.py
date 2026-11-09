"""Pruebas objetivas de cada efecto: el audio de salida debe cambiar de forma medible y correcta."""
from voice import *
import subprocess, sys
def eng(x, args, name="e"):
    x.astype(np.float32).tofile(f"/tmp/{name}_in.f32")
    r = subprocess.run(["/tmp/t_engine", f"/tmp/{name}_in.f32", f"/tmp/{name}_out.f32"] + args, capture_output=True, text=True)
    if r.returncode: raise RuntimeError(r.stderr)
    return np.fromfile(f"/tmp/{name}_out.f32", dtype=np.float32).astype(np.float64), r.stdout.strip()
def band(x, lo, hi, a=0, b=None):
    seg = x[a:b]; S = np.abs(np.fft.rfft(seg * np.hanning(len(seg)))) ** 2; fr = np.fft.rfftfreq(len(seg), 1 / FS)
    return S[(fr >= lo) & (fr < hi)].sum() + 1e-18
def db(a, b): return 10 * np.log10(a / b)
results = []
def check(name, ok, detail):
    results.append((name, ok, detail)); print(("PASS " if ok else "FAIL ") + f"{name:34s} {detail}")
base = ["wet=100", "compression=0", "reverb=0"]          # base limpia
tr, _ = contour([(0.3, 0), (2.5, 196), (1.2, 0)], vib_cents=0, drift_cents=0); voice = vowel(tr)
voice_vib, _ = contour([(0.3, 0), (2.5, 196), (1.2, 0)], vib_cents=30, drift_cents=0); vv = vowel(voice_vib)
rng = np.random.default_rng(3); noise = 0.2 * rng.standard_normal(FS * 3)
tone = 0.3 * np.sin(2 * np.pi * 440 * np.arange(FS * 2) / FS)

# 0) Bypass / transparencia
y, _ = eng(voice, base); 
corr = np.corrcoef(y[2000:], voice[2000:len(y)])[0, 1]; dl = db(np.mean(y[2000:]**2), np.mean(voice[2000:len(y)]**2))
check("Transparencia (todo en neutro)", corr > 0.95 and abs(dl) < 0.5, f"correlación {corr:.3f} (filtro anti-rumble 60 Hz), diferencia de nivel {dl:+.2f} dB")
y, _ = eng(voice, base + ["bypass=1", "reverb=60", "echo=50", "at_on=1"]); y2 = y[3000:]
check("Bypass (voz seca exacta)", np.max(np.abs(y2 - voice[3000:len(y)])) < 1e-3, f"error máx {np.max(np.abs(y2 - voice[3000:len(y)])):.5f}")
# 1) Reverb: cola tras terminar la nota (nota termina en t=2.8 s)
y0, _ = eng(voice, base); y1, _ = eng(voice, ["wet=100", "compression=0", "reverb=70", "reverb_size=80"])
a, b = int(3.0 * FS), int(3.6 * FS)
check("Reverb (cola)", np.sqrt(np.mean(y1[a:b] ** 2)) > 20 * np.sqrt(np.mean(y0[a:b] ** 2)) + 1e-6, f"cola con reverb {20*np.log10(np.sqrt(np.mean(y1[a:b]**2))+1e-9):.1f} dBFS vs seco {20*np.log10(np.sqrt(np.mean(y0[a:b]**2))+1e-9):.1f} dBFS")
# 2) Echo: pico de correlación a 240 ms
click = np.zeros(FS * 2); click[24000] = 0.8; yc, _ = eng(click, ["wet=100", "compression=0", "reverb=0", "echo=60", "echo_time=300", "echo_fb=40"])
pk = 24000 + int(0.3 * FS); seg = yc[pk - 200:pk + 200]
check("Echo (repetición a 300 ms)", np.abs(seg).max() > 0.2 and np.argmax(np.abs(seg)) in range(180, 220), f"eco a {(24000+np.argmax(np.abs(yc[24200:]))+200-24000)/FS*1000:.0f} ms, nivel {np.abs(seg).max():.2f}")
yc2, _ = eng(click, ["wet=100", "compression=0", "reverb=0", "echo=60", "echo_sync=1", "bpm=120", "echo_div=1"])
exp = int(0.25 * FS); seg = yc2[24000 + exp - 300:24000 + exp + 300]
check("Echo sincronizado (120 BPM, 1/8)", np.abs(seg).max() > 0.2, f"nivel en 250 ms: {np.abs(seg).max():.2f}")
# 3) Chorus / Flanger / Phaser con ruido blanco: cambio espectral
def spec_rough(x, lo=500, hi=6000):
    seg = x[FS // 2:FS // 2 + 32768]; S = np.abs(np.fft.rfft(seg * np.hanning(len(seg)))); fr = np.fft.rfftfreq(len(seg), 1 / FS)
    L = 20 * np.log10(S[(fr > lo) & (fr < hi)] + 1e-9); L = np.convolve(L, np.ones(9) / 9, 'same')[20:-20]  # suaviza microdetalle
    return L.std()
n0, _ = eng(noise, base); n0v, _ = eng(voice, base)
for nm, args in [("Flanger", ["flanger=100", "flanger_rate=0.01", "flanger_fb=70"]), ("Phaser", ["phaser=100", "phaser_rate=0.01"])]:
    yy, _ = eng(noise, base + args); r0, r1 = spec_rough(n0), spec_rough(yy)
    check(f"{nm} (muescas/peine espectral)", r1 > r0 * 1.8, f"rugosidad espectral {r0:.2f} -> {r1:.2f} dB")
yc_, _ = eng(voice, base + ["chorus=100"]); dif = np.sqrt(np.mean((yc_[3000:] - n0v[3000:len(yc_)])**2)) / np.sqrt(np.mean(n0v[3000:len(yc_)]**2))
f_c = [f0_at(yc_, t, 0.04) for t in np.arange(1.0, 2.6, 0.05)]; f_c = [v for v in f_c if v > 0]
check("Chorus (voces moduladas)", dif > 0.3 and np.std(1200*np.log2(np.array(f_c)/196)) > 1.5, f"diferencia con la seca {dif:.2f}; modulación de tono {np.std(1200*np.log2(np.array(f_c)/196)):.1f} cents")
# 4) Vibrato: oscilación de tono (tono sostenido sin vibrato natural)
yv, _ = eng(voice, base + ["vibrato=100", "vibrato_rate=5.5"])
ts = np.arange(0.9, 2.6, 0.02); c = np.array([f0_at(yv, t, 0.04) for t in ts]); c = 1200 * np.log2(c[c > 0] / 196)
check("Vibrato (±cents a 5,5 Hz)", (c.max() - c.min()) > 40, f"oscilación pico-pico {c.max()-c.min():.0f} cents (esperado ~100-120)")
# 5) Compresor: reduce diferencia entre pasajes fuertes y débiles
amp = np.where(np.arange(FS * 2) < FS, 0.5, 0.05); sig = amp * np.sin(2 * np.pi * 300 * np.arange(FS * 2) / FS)
yc0, _ = eng(sig, ["wet=100", "compression=0", "reverb=0"]); yc1, _ = eng(sig, ["wet=100", "compression=80", "reverb=0"])
r = lambda y: db(np.sqrt(np.mean(y[FS//4:FS//2]**2)), np.sqrt(np.mean(y[FS+FS//4:FS+FS//2]**2)))
check("Compresor (rango dinámico)", r(yc1) < r(yc0) - 4, f"diferencia fuerte/débil {r(yc0):.1f} dB -> {r(yc1):.1f} dB")
# 6) De-esser: ruido de 7 kHz atenuado
sib = np.zeros(FS * 2); t = np.arange(FS * 2) / FS; sib += 0.15 * np.sin(2 * np.pi * 7000 * t) * (np.sin(2 * np.pi * 2 * t) > 0)
sib += 0.2 * np.sin(2 * np.pi * 300 * t)
d0, _ = eng(sib, base); d1, _ = eng(sib, base + ["deess=80"])
check("De-esser (banda sibilante)", db(band(d1, 6000, 8000, FS//2), band(d0, 6000, 8000, FS//2)) < -4, f"banda 6-8 kHz: {db(band(d1,6000,8000,FS//2), band(d0,6000,8000,FS//2)):.1f} dB; graves intactos {db(band(d1,200,400,FS//2), band(d0,200,400,FS//2)):.1f} dB")
# 7) Puerta de ruido: reduce ruido bajo en silencio
low = 0.004 * rng.standard_normal(FS * 2); g0, _ = eng(low, base); g1, _ = eng(low, base + ["gate=85"])
check("Puerta de ruido", np.sqrt(np.mean(g1[FS//2:]**2)) < 0.3 * np.sqrt(np.mean(g0[FS//2:]**2)), f"ruido {20*np.log10(np.sqrt(np.mean(g0[FS//2:]**2))):.1f} -> {20*np.log10(np.sqrt(np.mean(g1[FS//2:]**2))+1e-12):.1f} dBFS")
# 8) EQ
noise = 0.03 * rng.standard_normal(FS * 3)
e0, _ = eng(noise, base)
for nm, args, lo, hi, sign in [("EQ graves +", ["bass=100"], 70, 110, 1), ("EQ graves -", ["bass=0"], 70, 110, -1), ("EQ medios +", ["mid=100"], 1000, 2000, 1), ("EQ agudos +", ["treble=100"], 10000, 16000, 1), ("EQ agudos -", ["treble=0"], 10000, 16000, -1)]:
    e1, _ = eng(noise, base + args); dd = db(band(e1, lo, hi, FS//2), band(e0, lo, hi, FS//2))
    check(nm, sign * dd > 5, f"{lo}-{hi} Hz: {dd:+.1f} dB")
# 9) Drive: aparecen armónicos
t0, _ = eng(tone, base); t1, _ = eng(tone, base + ["drive=70"])
h = lambda y: db(band(y, 1250, 1450, FS//2), band(y, 400, 480, FS//2))
check("Drive / distorsión (armónicos)", h(t1) > h(t0) + 20, f"3er armónico respecto a fundamental: {h(t0):.0f} dB -> {h(t1):.0f} dB")
# 10) Telephone
p0, _ = eng(noise, base); p1, _ = eng(noise, base + ["phone=100"])
check("Telephone (banda limitada)", db(band(p1, 20, 150, FS//2), band(p0, 20, 150, FS//2)) < -15 and db(band(p1, 5000, 12000, FS//2), band(p0, 5000, 12000, FS//2)) < -15, f"<150 Hz {db(band(p1,20,150,FS//2),band(p0,20,150,FS//2)):.0f} dB; >5 kHz {db(band(p1,5000,12000,FS//2),band(p0,5000,12000,FS//2)):.0f} dB")
# 11) Robot metálico: bandas laterales
m1, _ = eng(tone, base + ["robot_metal=100"])
check("Robot metálico (ring-mod)", band(m1, 320, 340, FS//2) > 100 * band(tone[:len(m1)], 320, 340, FS//2) and band(m1, 540, 560, FS//2) > 100 * band(tone, 540, 560, FS//2), f"bandas laterales 330/550 Hz presentes: {db(band(m1,320,340,FS//2), band(tone,320,340,FS//2)):.0f} dB")
# 12) Robot monotonía: tono constante pese al vibrato
ym, _ = eng(vv, base + ["robot_mono=100", "at_key=9", "at_speed=0"])
ts = np.arange(0.9, 2.6, 0.03); c = np.array([f0_at(ym, t + 0.024, 0.04) for t in ts]); c = 1200 * np.log2(c[c > 0] / np.median(c[c > 0]))
yi, _ = eng(vv, base); ci = np.array([f0_at(yi, t + 0.024, 0.04) for t in ts]); ci = 1200 * np.log2(ci[ci > 0] / np.median(ci[ci > 0]))
check("Robot monótono (pitch bloqueado)", c.std() < 0.2 * ci.std() + 1, f"desviación de tono {ci.std():.1f} c (natural) -> {c.std():.1f} c (robot); nota {np.median([f0_at(ym,t+0.024,0.04) for t in ts]):.1f} Hz (A3=220)")
# 13) Armonías
hh = lambda args: eng(voice, base + args)[0]
yh = hh(["at_key=0", "at_scale=1", "harm_3=100"]); f0v = 196.0   # G3 en Do mayor: tercera = B3 (246.9), quinta = D4 (293.7)
check("Armonía de tercera (G3->B3)", band(yh, 240, 254, int(1.2*FS), int(2.4*FS)) > 5 * band(hh([]), 240, 254, int(1.2*FS), int(2.4*FS)), f"energía 247 Hz: {db(band(yh,240,254,int(1.2*FS),int(2.4*FS)), band(hh([]),240,254,int(1.2*FS),int(2.4*FS))):+.1f} dB")
yh = hh(["at_key=0", "at_scale=1", "harm_5=100"])
check("Armonía de quinta (G3->D4)", band(yh, 288, 300, int(1.2*FS), int(2.4*FS)) > 5 * band(hh([]), 288, 300, int(1.2*FS), int(2.4*FS)), f"energía 294 Hz: {db(band(yh,288,300,int(1.2*FS),int(2.4*FS)), band(hh([]),288,300,int(1.2*FS),int(2.4*FS))):+.1f} dB")
sn, _ = contour([(0.3, 0), (2.5, 196), (1.2, 0)], vib_cents=0, drift_cents=0)
sine = 0.3*np.sin(2*np.pi*np.cumsum(sn)/FS)*(sn>0)
yh = eng(sine, base + ["harm_oct_up=100"])[0]; yz = eng(sine, base)[0]
check("Octava arriba", band(yh, 385, 400, int(1.2*FS), int(2.4*FS)) > 5 * band(yz, 385, 400, int(1.2*FS), int(2.4*FS)), f"energía 392 Hz: {db(band(yh,385,400,int(1.2*FS),int(2.4*FS)), band(yz,385,400,int(1.2*FS),int(2.4*FS))):+.1f} dB")
yh = hh(["harm_oct_dn=100"])
check("Octava abajo", band(yh, 94, 102, int(1.2*FS), int(2.4*FS)) > 5 * band(yz, 94, 102, int(1.2*FS), int(2.4*FS)), f"energía 98 Hz: {db(band(yh,94,102,int(1.2*FS),int(2.4*FS)), band(yz,94,102,int(1.2*FS),int(2.4*FS))):+.1f} dB")
# 14) Doblador: aparece copia retardada (correlación a ~17 ms)
yd = hh(["doubler=100", "doubler_detune=15"]); yz = hh([])
dm = np.sqrt(np.mean((yd[int(1.2*FS):int(2.4*FS)] - yz[int(1.2*FS):int(2.4*FS)]) ** 2)) / np.sqrt(np.mean(yz[int(1.2*FS):int(2.4*FS)] ** 2))
check("Doblador de voz", dm > 0.4, f"energía añadida relativa a la voz: {dm:.2f}")
# 15) Cambio de formantes: centroide espectral sube y f0 se mantiene
cen = lambda y: (lambda S, fr: (S * fr).sum() / S.sum())(np.abs(np.fft.rfft(y[int(1.2*FS):int(2.0*FS)])) ** 2, np.fft.rfftfreq(len(y[int(1.2*FS):int(2.0*FS)]), 1 / FS))
yf = hh(["formant_semi=5"]); yz = hh(["pitch_semi=0.01"])
check("Formantes +5 st (timbre)", cen(yf) > 1.08 * cen(yz) and abs(f0_at(yf, 1.5, 0.05) / f0_at(yz, 1.5, 0.05) - 1) < 0.01, f"centroide {cen(yz):.0f} -> {cen(yf):.0f} Hz; f0 {f0_at(yz,1.5,0.05):.1f} -> {f0_at(yf,1.5,0.05):.1f} Hz")
yf = hh(["formant_semi=-5"])
check("Formantes -5 st (timbre)", cen(yf) < 0.93 * cen(yz), f"centroide {cen(yz):.0f} -> {cen(yf):.0f} Hz")
# 16) Pitch +/- 
for st in (7, -7, 12):
    yp = hh([f"pitch_semi={st}"]); fo = f0_at(yp, 1.5, 0.05, 60, 800); fe = 196 * 2 ** (st / 12)
    check(f"Cambio de pitch {st:+d} st", abs(1200 * np.log2(fo / fe)) < 5, f"{fo:.1f} Hz (esperado {fe:.1f} Hz, error {1200*np.log2(fo/fe):+.1f} c)")
# 17) Mezcla procesada
m0 = hh(["wet=0", "reverb=80", "echo=60"]); mm = np.corrcoef(m0[3000:], voice[3000 - 0:len(m0)])[0, 1]
check("Mezcla 0 % = voz seca", mm > 0.95, f"correlación con la seca {mm:.4f}")
print("\nRESUMEN:", sum(1 for r in results if r[1]), "/", len(results), "pruebas aprobadas")
open("/tmp/effects_results.txt", "w").write("\n".join(("PASS " if r[1] else "FAIL ") + r[0] + " — " + r[2] for r in results) + f"\nRESUMEN: {sum(1 for r in results if r[1])}/{len(results)}\n")

import sys
sys.exit(0 if all(r[1] for r in results) else 1)
