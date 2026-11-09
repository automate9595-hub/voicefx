# VOICE FX PRO 0.3.1

Procesador vocal Android con motor DSP nativo C++/Oboe y UI Jetpack Compose.

## Estado de compatibilidad externa

En Android estándar sin ROOT, una app normal no puede crear un micrófono virtual universal ni reemplazar el micrófono privado de otras apps. La pestaña **APLICACIÓN** permite abrir una app o compartir/importar un WAV; no enruta su micrófono. La captura simultánea es oportunista y Android puede silenciarla cuando otra app toma prioridad.

Rutas disponibles: grabar y compartir/importar el WAV procesado, o usar una interfaz USB/mezclador con retorno o loopback compatible. La compatibilidad en vivo solo se considera comprobada cuando una grabación hecha desde la app externa contiene realmente los efectos.

## Proyecto

- `app/src/main/cpp/dsp/`: procesamiento vocal, pitch correction y efectos.
- `app/src/main/cpp/audio/`: motor Oboe/AAudio y grabador WAV.
- `app/src/main/java/com/voicefxpro/`: servicio, parámetros, dispositivos y pantallas.
- `native_tests/`: pruebas DSP C++/Python.
- `.github/workflows/android.yml`: validación, pruebas y construcción del APK debug.
- `COMPATIBILITY_AND_ACCEPTANCE.md`: límites técnicos y criterios de aceptación.
- `README_0.3.1.md`: notas de esta versión.

## Compilar

GitHub Actions construye `app-debug.apk` al subir el proyecto o ejecutar manualmente el workflow. Requiere JDK 17, Gradle 8.9, Android SDK/NDK y CMake 3.22.1 para compilar localmente.
