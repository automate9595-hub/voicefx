"""Pruebas objetivas del Auto-Tune (afinación, vibrato, transparencia). Falla (exit 1) si algo no se cumple."""
from voice import *
import subprocess, sys
def eng(x, args):
    x.astype(np.float32).tofile("/tmp/a_in.f32")
    subprocess.run(["/tmp/t_engine", "/tmp/a_in.f32", "/tmp/a_out.f32"] + args, capture_output=True, check=True)
    return np.fromfile("/tmp/a_out.f32", dtype=np.float32).astype(float)
base = ["wet=100", "compression=0", "reverb=0", "at_key=0", "at_scale=1"]
fails = []
def check(name, ok, detail):
    print(("PASS " if ok else "FAIL ") + f"{name:46s} {detail}")
    if not ok: fails.append(name)
def note_err(sig, tgt, spans, args, delay=0.024):
    out = []
    for (a, b), f in zip(spans, tgt):
        v = [f0_at(sig, t + delay, 0.04) for t in np.arange(a + 0.2, b - 0.05, 0.02)]
        v = np.array([1200 * np.log2(q / f) for q in v if q > 0]); out.append((v.mean(), v.std()))
    return out
# 1) nota sostenida 35 cents bemol, modo duro -> A3 exacto
tr, _ = contour([(0.2, 0), (2.0, 220 * 2 ** (-35 / 1200)), (0.2, 0)], vib_cents=0, drift_cents=0); x = vowel(tr)
y = eng(x, base + ["at_on=1", "at_speed=0", "at_humanize=0"])
errs = [1200 * np.log2(f0_at(y, t + 0.024) / 220) for t in (0.6, 1.0, 1.5, 2.0)]
check("Nota sostenida 35 c bemol -> nota exacta", max(abs(e) for e in errs) < 3.0, f"errores {[round(e,1) for e in errs]} cents")
# 2) melodía con vibrato
segs = [(0.3,0),(0.6,196*2**(-40/1200)),(0.12,0),(0.6,247*2**(30/1200)),(0.1,0),(0.7,262*2**(-15/1200)),(0.6,294*2**(25/1200)),(0.3,0)]
tr, _ = contour(segs, vib_cents=18, drift_cents=5); x = vowel(tr)
tgt = [196, 246.94, 261.63, 293.66]; spans = [(0.3,0.9),(1.02,1.62),(1.72,2.42),(2.42,3.02)]
ref = note_err(x, tgt, spans, [], 0.0)
nat = note_err(eng(x, base + ["at_on=1", "at_speed=40", "at_humanize=50"]), tgt, spans, [])
hard = note_err(eng(x, base + ["at_on=1", "at_speed=0", "at_humanize=0"]), tgt, spans, [])
sut = note_err(eng(x, base + ["at_on=1", "at_speed=120", "at_humanize=80", "at_amount=70"]), tgt, spans, [])
off = note_err(eng(x, base + ["at_on=0"]), tgt, spans, [])
check("Natural: media a <5 c de la nota", all(abs(m) < 5 for m, s in nat), f"{[round(m,1) for m,s in nat]} (entrada {[round(m,1) for m,s in ref]})")
check("Natural: conserva vibrato (desv > 6 c)", all(s > 6 for m, s in nat), f"desv {[round(s,1) for m,s in nat]}")
check("Duro: media <2 c y tono plano (desv < 3 c)", all(abs(m) < 2 and s < 3 for m, s in hard), f"{[(round(m,1), round(s,1)) for m,s in hard]}")
check("Sutil (70 %): corrige parcialmente", all(abs(a[0]) < abs(r[0]) * 0.6 + 4 and abs(a[0]) > 3 for a, r in zip(sut, ref)), f"{[round(m,1) for m,s in sut]}")
check("Apagado: transparente (<3 c de la entrada)", all(abs(a[0] - r[0]) < 3 for a, r in zip(off, ref)), f"{[round(a[0]-r[0],1) for a,r in zip(off,ref)]}")
print("RESULTADO:", "OK" if not fails else f"{len(fails)} FALLOS"); sys.exit(1 if fails else 0)
