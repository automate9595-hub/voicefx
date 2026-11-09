from voice import *
import sys
tr, _ = contour([(2.0, 196 * 2 ** (-30 / 1200))], vib_cents=0, drift_cents=0); x = vowel(tr)
def hnr(x, t, win=0.06, fmin=60):
    c = int(t * FS); h = int(win * FS / 2); seg = x[c - h:c + h]; seg = seg - seg.mean()
    ac = np.correlate(seg, seg, 'full')[len(seg) - 1:]; ac /= ac[0] + 1e-12
    lo, hi = int(FS / 600), int(FS / fmin); r = ac[lo:hi].max(); return 10 * np.log10(max(r, 1e-3) / (1 - r + 1e-9))
def sub_ratio(y, f_exp, t=1.05):
    c = int(t*FS); seg = y[c:c+16384]*np.hanning(16384); S = np.abs(np.fft.rfft(seg)); fr = np.fft.rfftfreq(16384, 1/FS)
    def amp(f): return S[(np.abs(fr-f)<f*0.03)].max()
    return 20*np.log10(amp(f_exp)/amp(f_exp*2))   # >0: f0 domina sobre 2·f0 (sin duplicación de periodo)
print("grano máx =", sys.argv[1] if len(sys.argv)>1 else "1.25")
for st in (-3, -5, -7, -12):
    y, _, _ = run(x, ["correct=0", f"semi={st}"], "d"); fexp = 192.7 * 2 ** (st / 12)
    print(f"  {st:+3d} st: f0 {f0_at(y,1.03,0.06,60,500):6.1f} (esp {fexp:5.1f})  HNR {hnr(y,1.03):5.1f} dB  f0/2f0 {sub_ratio(y,fexp):+5.1f} dB")
