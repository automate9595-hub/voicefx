#!/usr/bin/env python3
"""Genera Params.h (C++) y Params.kt (Kotlin) desde app/src/main/cpp/params.def.
Uso:  python3 tools/gen_params.py          -> escribe los archivos
      python3 tools/gen_params.py --check  -> falla si están desactualizados (CI)"""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
src = (root / "app/src/main/cpp/params.def").read_text(encoding="utf-8")
rx = re.compile(r'^P\((\w+),\s*"([^"]*)",\s*"([^"]*)",\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+),\s*"([^"]*)",\s*"([^"]*)"\)', re.M)
rows = rx.findall(src)
assert rows, "params.def vacío"
def f(v):
    return v if "." in v else v + ".0"
h = ["// GENERADO por tools/gen_params.py — no editar a mano.", "#pragma once", "namespace vfx {", "enum Param : int {"]
h += [f"    {r[0]}," for r in rows] + ["    PARAM_COUNT", "};",
      "struct ParamInfo { const char* key; float def; float min; float max; };",
      "static const ParamInfo kParamInfo[PARAM_COUNT] = {"]
h += [f'    {{"{r[1]}", {f(r[3])}f, {f(r[4])}f, {f(r[5])}f}},' for r in rows] + ["};", "}  // namespace vfx", ""]
k = ["// GENERADO por tools/gen_params.py — no editar a mano. Fuente: app/src/main/cpp/params.def",
     "package com.voicefxpro", "",
     "enum class Param(val key: String, val label: String, val def: Float, val min: Float, val max: Float, val unit: String, val group: String) {"]
k += [f'    {r[0]}("{r[1]}", "{r[2]}", {f(r[3])}f, {f(r[4])}f, {f(r[5])}f, "{r[6]}", "{r[7]}"),' for r in rows]
k[-1] = k[-1][:-1] + ";"
k += ["}", ""]
outs = {root / "app/src/main/cpp/dsp/Params.h": "\n".join(h),
        root / "app/src/main/java/com/voicefxpro/Params.kt": "\n".join(k)}
bad = False
for p, text in outs.items():
    if "--check" in sys.argv:
        if not p.exists() or p.read_text(encoding="utf-8") != text:
            print("DESACTUALIZADO:", p); bad = True
    else:
        p.write_text(text, encoding="utf-8"); print("escrito", p)
sys.exit(1 if bad else 0)
