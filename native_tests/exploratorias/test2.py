from voice import *
import scipy.signal as sg
def lpc_formants(x, order=12):
    x = x * np.hamming(len(x)); x = np.append(x[0], x[1:] - 0.97 * x[:-1])
    r = np.correlate(x, x, 'full')[len(x) - 1:len(x) + order]
    R = np.array([[r[abs(i - j)] for j in range(order)] for i in range(order)])
    a = np.linalg.solve(R + 1e-9 * np.eye(order), r[1:order + 1])
    roots = np.roots(np.concatenate(([1], -a))); roots = roots[np.imag(roots) > 0]
    f = np.sort(np.angle(roots) * FS / (2 * np.pi)); bw = -np.log(np.abs(roots)) * FS / np.pi
    return [round(float(fi)) for fi, b in sorted(zip(f, bw)) if fi > 200 and b < 400][:3]

# A) vibrato natural: 25 cents a 5.5 Hz, nota ~ A3 con deriva. Comparar velocidades/humanize
tr, cin = contour([(0.2, 0), (3.0, 220 * 2 ** (-20 / 1200)), (0.2, 0)], vib_cents=25, drift_cents=8)
x = vowel(tr); x *= 0.3 / np.abs(x).max()
print("== A) vibrato 25c, nota A3 con deriva ==")
def contour_est(sig, t0, t1, step=0.01, delay=0.0):
    ts = np.arange(t0, t1, step); c = [f0_at(sig, t + delay, win=0.04) for t in ts]
    c = np.array([1200 * np.log2(v / 220) if v > 0 else np.nan for v in c]); return ts, c
for name, args in [("hard speed=0 hum=0", ["speed=0", "hum=0"]), ("natural speed=40 hum=50", ["speed=40", "hum=0.5"]),
                   ("sutil speed=120 hum=80", ["speed=120", "hum=0.8"]), ("apagado correct=0", ["correct=0"])]:
    y, _, _ = run(x, args, "t2")
    ts, ci = contour_est(x, 0.6, 3.0); _, co = contour_est(y, 0.6, 3.0, delay=0.024)
    ok = ~np.isnan(ci) & ~np.isnan(co)
    print(f"{name:26s} entrada: media {np.nanmean(ci):+6.1f}c  rango p-p {np.nanmax(ci)-np.nanmin(ci):5.1f}c | salida: media {np.nanmean(co):+6.1f}c  desv {np.nanstd(co):5.1f}c  rango p-p {np.nanmax(co)-np.nanmin(co):5.1f}c")
# B) formantes: el PSOLA debe conservarlos al subir 4 semitonos
print("== B) formantes (vocal /a/ F1=700 F2=1220 F3=2600) ==")
tr, _ = contour([(1.0, 150)], vib_cents=0, drift_cents=0); x = vowel(tr); x *= 0.3 / np.abs(x).max()
for st in (0, 4, -5):
    y, _, _ = run(x, ["correct=0", f"semi={st}"], "t3")
    # sin corrección el motor usa pitch_semi: correct=0 -> sólo semi
    seg = y[int(0.5 * FS):int(0.5 * FS) + 2400]
    print(f"semitonos {st:+d}: f0 salida {f0_at(y, 0.55, 0.05):6.1f} Hz (esperado {150*2**(st/12):6.1f})  formantes LPC: {lpc_formants(seg)}")
print("entrada:", lpc_formants(x[int(0.5*FS):int(0.5*FS)+2400]))
