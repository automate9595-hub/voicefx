# VOICE FX PRO 0.3.0

Procesador vocal Android: motor DSP en C++ (Oboe/AAudio) + interfaz Jetpack Compose.

## Estructura
- `app/src/main/cpp/dsp/` — motor DSP (PSOLA, YIN, Auto-Tune, efectos). Sin dependencias de Android: se prueba en cualquier PC.
- `app/src/main/cpp/audio/` — Oboe full-duplex, grabador WAV. `jni.cpp` — puente con Kotlin.
- `app/src/main/cpp/params.def` — **única fuente** de parámetros. Tras editarla: `python3 tools/gen_params.py`.
- `app/src/main/java/com/voicefxpro/` — servicio, almacén de parámetros, presets; `ui/` — pantallas.
- `native_tests/` — pruebas objetivas del DSP (`bash native_tests/run_all.sh`; requiere g++, numpy, scipy).
- `docs/` — `ESTADO_FUNCIONES.md` (terminado/pendiente/riesgos) y `TEST_RESULTS.md`.

## Compilar
GitHub Actions (`.github/workflows/android.yml`): verifica sincronía, ejecuta las pruebas nativas, las pruebas JVM y genera el APK (artefacto `VOICE-FX-PRO-debug-apk`).
Local: `gradle :app:assembleDebug` (JDK 17, Android SDK, NDK, CMake 3.22.1).

## Uso rápido
1. ESTUDIO → botón de encendido (concede el permiso de micrófono). Usá auriculares con cable.
2. PRESETS → elegí uno. AUTOPITCH → tonalidad/escala y carácter (Natural/Sutil/Pop/Duro).
3. Doble toque sobre el nombre de un slider = valor original.
