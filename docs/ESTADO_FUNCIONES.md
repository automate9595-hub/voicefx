# VOICE FX PRO 0.3.0 — Estado de funciones

## Qué está verificado y qué no (leer primero)
- **Verificado con mediciones (en este entorno):** todo el motor DSP en C++ (`app/src/main/cpp/dsp`): detección de tono, Auto-Tune PSOLA, cambio de pitch/formantes, armonías, doblador, compresor, de-esser, EQ, puerta, saturación, chorus, flanger, phaser, vibrato, eco, reverb, teléfono, robot, limitador y grabador WAV. Ver `docs/TEST_RESULTS.md` (32 pruebas de efectos + 6 de Auto-Tune + estrés).
- **NO verificado (no había Android SDK, Gradle ni Kotlin en este entorno):** compilación de Kotlin/Compose, de la capa Oboe (`audio/OboeEngine.cpp`, `jni.cpp`) y del APK. No se generó APK. GitHub Actions compila y publica el APK; si aparece un error de compilación, es lo primero a corregir (ver "Riesgos").
- **NO probado en un teléfono:** latencia real, cortes de audio, comportamiento con auriculares/USB/Bluetooth. Las cifras de CPU de las pruebas son de un PC; en el teléfono serán mayores.

## Terminado (DSP verificado)
| Función | Cómo se comprobó |
|---|---|
| Auto-Tune automático (tonalidad + 9 escalas) | Nota 35 cents desafinada → error final 1,0–1,8 cents (modo duro). Melodía con vibrato: modo Natural queda a ≤2,2 cents de la nota y conserva el vibrato (desv. ≈10 cents). |
| Auto-Tune manual (notas permitidas) | Misma lógica con máscara de 12 notas (`AT_MASK`). Probado el selector de notas por escalas; la máscara manual usa el mismo código. |
| Retune Speed, Humanize, Intensidad, Tolerancia, A4 | Duro (0 ms): desvío 0,5–0,9 cents plano. Sutil (70 %): corrige parcialmente (−35 → −9,5 cents). |
| Cambio de pitch ±12 st | Error ≤ 0,9 cents; ver HNR en `exploratorias/test3.py`. |
| Cambio de formantes | Centroide espectral 584 → 783 Hz (+5 st) y → 499 Hz (−5 st) con f0 intacta. |
| Armonías 3ª, 5ª (diatónicas), octava ↑/↓ | Energía medida en 247, 294, 392 y 98 Hz (G3 en Do mayor). |
| Doblador | Dos copias retardadas (17/29 ms) y desafinadas ±3–30 cents. |
| Robot (monotonía + metálico) | Desviación de tono 19,7 → 0,6 cents; bandas laterales del ring-mod presentes. |
| Compresor, De-esser, Puerta | 10,0 → 1,8 dB de rango; −14 dB en 6–8 kHz sin tocar graves; ruido −48 → −87 dBFS. |
| EQ graves/medios/agudos, Drive (x2 sobremuestreo), Telephone | ±8 a ±11 dB medidos; armónicos de saturación; banda 300–3400 Hz. |
| Chorus, Flanger, Phaser, Vibrato | Peine/muescas espectrales; vibrato ≈ ±57 cents. |
| Echo (libre y sincronizado a BPM), Reverb | Repetición a 300 ms y a 250 ms (120 BPM, 1/8); cola de reverb medida. |
| Limitador de salida, mezcla seca/procesada, bypass | Salida ≤ 0,98 en estrés con entradas > 0 dBFS; bypass exacto. |
| Robustez | 15 combinaciones de sample rate/bloque con parámetros aleatorios: 0 muestras NaN/Inf. |

## Terminado (código escrito, sin compilar ni probar en teléfono)
- Interfaz de 6 solapas (ESTUDIO, EFECTOS, AUTOPITCH, APLICACIÓN, MONITOR, PRESETS), tema oscuro violeta/azul/cyan, medidores animados, botón de encendido, diseño de 1 o 2 columnas según ancho.
- Todos los controles están conectados a un parámetro del motor (sin botones decorativos): ver `params.def`.
- Audio en tiempo real con **Oboe/AAudio full-duplex** (baja latencia, exclusivo, entrada UNPROCESSED), ajuste automático del búfer, descarte del retraso acumulado, reapertura automática ante desconexión de dispositivos, selección de dispositivo de entrada/salida (USB, cable, Bluetooth).
- Detección de silenciamiento (ceros digitales > 1,2 s + `AudioRecordingCallback` en Android 10+), foco de audio (llamadas silencian el monitor), errores y recuperación con estado en pantalla.
- Motor de respaldo (AudioRecord/AudioTrack con la cadena Kotlin original) si Oboe no abre. Se conservan `VoiceDsp.kt`, `PitchTracker.kt` y sus pruebas.
- Presets: 15 de fábrica (Natural, Pop, Trap, Reggaetón, Rap, Balada, Robot, Telephone, Ambient, Voz Pro Estudio/Radio/Brillante, Coro/Armonías, Gran Sala) + presets propios (guardar, cargar, borrar).
- Grabador WAV de la señal procesada y **compartir** con la app elegida (FileProvider).
- Migración de los valores guardados de versiones 0.2.x.

## Pendiente / límites conocidos
1. **Compilar y probar en un teléfono** (APK, latencia real, cortes, USB, Bluetooth). Prioridad 1.
2. **Compatibilidad directa con BandLab/Smule/StarMaker como micrófono: NO es posible en Android sin root ni hardware.** Android no ofrece micrófono virtual a apps normales y, desde Android 10, silencia la captura de la app que no está en primer plano. La app lo detecta y lo muestra; las vías reales son grabar+compartir y la interfaz USB con retorno. No se probó ninguna app de terceros.
3. Latencia de la etapa de tono (Auto-Tune, armonías, doblador): +24 ms fijos por diseño (PSOLA necesita mirar hacia adelante). Con todo sin tonos activos, la latencia añadida es 0.
4. Salida mono (sin ensanchamiento estéreo de chorus/doblador).
5. Voces de armonía: calidad de PSOLA monofónico; con voces muy graves (< 100 Hz) la sincronía de granos se degrada.
6. Sólo se probó con voz sintética; falta ajuste fino con voces reales (el comportamiento con respiración, consonantes y ruido de fondo real puede requerir retocar umbrales).
7. Preset "Telephone" y presets de géneros son punto de partida: ajustar a gusto con los sliders y guardarlos.

## Riesgos de compilación (para revisar si falla Actions)
- API de Oboe 1.9 usada en `OboeEngine.cpp` (`openStream(shared_ptr&)`, `onErrorAfterClose`, `LatencyTuner`, `getSessionId`).
- CMake 3.22.1 y Prefab (`buildFeatures.prefab = true`, `ANDROID_STL=c++_shared`).
- Compose: sólo APIs estables de Material 3 (BOM 2024.12.01).
