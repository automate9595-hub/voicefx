from voice import *
def hnr(x, t, win=0.04):
    c = int(t * FS); h = int(win * FS / 2); seg = x[c - h:c + h]; seg = seg - seg.mean()
    ac = np.correlate(seg, seg, 'full')[len(seg) - 1:]; ac /= ac[0] + 1e-12
    lo, hi = int(FS / 600), int(FS / 80); r = ac[lo:hi].max()
    return 10 * np.log10(r / (1 - r + 1e-9))
print("== HNR (dB) estable, entrada vs salida ==")
tr, _ = contour([(2.0, 196 * 2 ** (-30 / 1200))], vib_cents=0, drift_cents=0); x = vowel(tr)
print(f"entrada {hnr(x,1.0):5.1f} dB")
for name, args in [("A.T. 30c correccion", ["speed=0","hum=0","key=0"]), ("pitch +3 st", ["correct=0","semi=3"]), ("pitch +7 st", ["correct=0","semi=7"]),
                   ("pitch +12 st", ["correct=0","semi=12"]), ("pitch -5 st", ["correct=0","semi=-5"]), ("pitch -12 st", ["correct=0","semi=-12"]), ("formant +4", ["correct=0","formant=4"])]:
    y, _, _ = run(x, args, "q"); print(f"{name:20s} salida {hnr(y,1.0+0.024):5.1f} dB  f0 {f0_at(y,1.024):6.1f}")
print("== Melodía con consonantes: cambio de notas y silencios (clics) ==")
segs = [(0.3,0),(0.5,196*2**(-40/1200)),(0.12,0),(0.5,247*2**(30/1200)),(0.1,0),(0.6,262*2**(-15/1200)),(0.3,0)]
tr, _ = contour(segs, vib_cents=18, drift_cents=5); x = vowel(tr)
# consonantes: ráfagas de ruido en los huecos
rng = np.random.default_rng(5); mask = (tr == 0)
x = x + 0.05 * rng.standard_normal(len(x)) * np.convolve(mask.astype(float), np.hanning(960) / 480, 'same') * 3
x *= 0.3 / np.abs(x).max()
y, f0d, tg = run(x, ["speed=30", "hum=0.5", "key=0", "scale=1"], "m")
dx = np.abs(np.diff(x)).max(); dy = np.abs(np.diff(y)).max()
print(f"máx salto muestra-a-muestra: entrada {dx:.3f} salida {dy:.3f}; pico entrada {np.abs(x).max():.3f} salida {np.abs(y).max():.3f}")
# clics: energía de alta frecuencia anómala (diferencia de segunda derivada)
d2 = np.abs(np.diff(y, 2)); print("p99.99 |d2| salida", np.percentile(d2, 99.99), " entrada", np.percentile(np.abs(np.diff(x,2)), 99.99))
for t in (0.6, 1.25, 1.95):
    print(f"t={t}: f0 entrada {f0_at(x,t):6.1f}  salida {f0_at(y,t+0.024):6.1f}")
print("notas objetivo esperadas: G3 196.0, B3 246.9, C4 261.6")
