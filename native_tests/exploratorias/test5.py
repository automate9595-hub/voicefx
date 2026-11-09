from voice import *
import subprocess
def eng(x, args):
    x.astype(np.float32).tofile("/tmp/m_in.f32"); subprocess.run(["/tmp/t_engine","/tmp/m_in.f32","/tmp/m_out.f32"]+args,capture_output=True)
    return np.fromfile("/tmp/m_out.f32",dtype=np.float32).astype(float)
segs=[(0.3,0),(0.6,196*2**(-40/1200)),(0.12,0),(0.6,247*2**(30/1200)),(0.1,0),(0.7,262*2**(-15/1200)),(0.6,294*2**(25/1200)),(0.3,0)]
tr,_=contour(segs,vib_cents=18,drift_cents=5); x=vowel(tr)
rng=np.random.default_rng(5); mask=(tr==0); x=x+0.04*rng.standard_normal(len(x))*np.convolve(mask.astype(float),np.hanning(960)/480,'same')*3; x*=0.3/np.abs(x).max()
base=["wet=100","compression=0","reverb=0","at_on=1","at_key=0","at_scale=1"]
tgt=[196,246.94,261.63,293.66]; spans=[(0.3,0.9),(1.02,1.62),(1.72,2.42),(2.42,3.02)]
print("Melodía: errores respecto a la nota de la escala (promedio de cada nota, en cents)")
for name,extra in [("Natural  (speed 40, hum 50)",["at_speed=40","at_humanize=50"]),("Sutil    (speed 120, hum 80, intensidad 70)",["at_speed=120","at_humanize=80","at_amount=70"]),("Duro     (speed 0, hum 0)",["at_speed=0","at_humanize=0"]),("Apagado",["at_on=0"])]:
    y=eng(x,base+extra)
    errs=[]
    for (a,b),f in zip(spans,tgt):
        ts=np.arange(a+0.2,b-0.05,0.02); v=[f0_at(y,t+0.024,0.04) for t in ts]; v=[1200*np.log2(q/f) for q in v if q>0]
        errs.append((np.mean(v),np.std(v)))
    ie=[]
    for (a,b),f in zip(spans,tgt):
        ts=np.arange(a+0.2,b-0.05,0.02); v=[f0_at(x,t,0.04) for t in ts]; v=[1200*np.log2(q/f) for q in v if q>0]; ie.append(np.mean(v))
    print(f"  {name:44s} salida media/desv: "+"  ".join(f"{m:+5.1f}/{s:4.1f}" for m,s in errs)+"   | entrada: "+" ".join(f"{m:+5.1f}" for m in ie))
    # clics: máximo de la 2ª derivada vs entrada
    d=np.abs(np.diff(y,2)); di=np.abs(np.diff(x,2)); print(f"      pico |d²| salida {d.max():.3f} / entrada {di.max():.3f}; nivel pico salida {np.abs(y).max():.2f}")
