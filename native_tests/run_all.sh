#!/usr/bin/env bash
# Compila y ejecuta todas las pruebas del motor DSP nativo en el equipo anfitrión (sin Android).
set -euo pipefail
cd "$(dirname "$0")"
g++ -O2 -std=c++17 -Wall -Wextra t_engine.cpp -o /tmp/t_engine
g++ -O2 -std=c++17 -Wall -Wextra t_psola.cpp -o /tmp/t_psola
g++ -O2 -std=c++17 -pthread t_recorder.cpp -o /tmp/t_recorder
g++ -O2 -std=c++17 -Wall -Wextra t_stress.cpp -o /tmp/t_stress
/tmp/t_recorder
/tmp/t_stress
python3 test_autotune.py
python3 test_effects.py
