#!/usr/bin/env python3
"""Verifica que las constantes Kotlin (medidores/estado) coinciden con los enum de C++ y que Params está al día."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
def cpp_enum(path, name):
    txt = (root / path).read_text(encoding="utf-8")
    body = re.search(r"enum\s+" + name + r"\s*:\s*int\s*\{(.*?)\};", txt, re.S).group(1)
    names = [n.strip().split("=")[0].strip() for n in body.split(",") if n.strip()]
    return names
def kt_object(name):
    txt = (root / "app/src/main/java/com/voicefxpro/NativeEngine.kt").read_text(encoding="utf-8")
    body = re.search(r"object\s+" + name + r"\s*\{(.*?)\n    \}", txt, re.S).group(1)
    return {m.group(1): int(m.group(2)) for m in re.finditer(r"const val (\w+) = (\d+)", body)}
ok = True
for cname, kname, prefix in (("Meter", "M", "M_"), ("Status", "S", "S_")):
    path = "app/src/main/cpp/dsp/VoiceEngine.h" if cname == "Meter" else "app/src/main/cpp/audio/OboeEngine.h"
    names = cpp_enum(path, cname) if cname == "Meter" else None
    if cname == "Status":
        txt = (root / path).read_text(encoding="utf-8")
        body = re.search(r"enum\s+Status\s*:\s*int\s*\{(.*?)\};", txt, re.S).group(1)
        body = re.sub(r"//[^\n]*", "", body)
        names = [n.strip().split("=")[0].strip() for n in body.split(",") if n.strip()]
    kt = kt_object(kname)
    for i, n in enumerate(names):
        if n.endswith("COUNT"):
            if kt.get("COUNT") != i: print(f"{kname}: COUNT Kotlin={kt.get('COUNT')} C++={i}"); ok = False
            continue
        key = n[len(prefix):]
        if kt.get(key) != i: print(f"{kname}.{key}: Kotlin={kt.get(key)} C++={i}"); ok = False
print("Constantes JNI sincronizadas" if ok else "DESINCRONIZADO")
sys.exit(0 if ok else 1)
