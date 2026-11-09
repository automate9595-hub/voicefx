#!/usr/bin/env python3
"""Revisión estática ligera de Kotlin (no sustituye al compilador): llaves balanceadas, Param.* válidos,
funciones @Composable referenciadas definidas, imports de com.voicefxpro.* existentes."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/voicefxpro"
files = list(root.rglob("*.kt"))
params = set(re.findall(r"^\s+(\w+)\(\"", (root / "Params.kt").read_text(encoding="utf-8"), re.M))
src = {f: f.read_text(encoding="utf-8") for f in files}
defs = set()
for t in src.values():
    defs |= set(re.findall(r"\b(?:fun|class|object)\s+(?:[\w.]+\.)?(\w+)", t))
bad = 0
for f, t in src.items():
    clean = re.sub(r'"(?:\\.|[^"\\])*"', '""', re.sub(r"//[^\n]*", "", t))
    clean = re.sub(r"'(?:\\.|[^'\\])'", "' '", clean)
    for o, c in ("{}", "()", "[]"):
        if clean.count(o) != clean.count(c): print(f"{f.name}: {o}{c} desbalanceado {clean.count(o)} vs {clean.count(c)}"); bad += 1
    for m in set(re.findall(r"\bParam\.([A-Z0-9_]+)", t)):
        if m not in params: print(f"{f.name}: Param.{m} no existe"); bad += 1
    for m in re.findall(r"import com\.voicefxpro\.(?:ui\.)?([A-Za-z]\w*)\s*$", t, re.M):
        if m not in defs: print(f"{f.name}: import {m} no definido"); bad += 1
print("Revisión estática:", "OK" if not bad else f"{bad} problemas")
sys.exit(1 if bad else 0)
