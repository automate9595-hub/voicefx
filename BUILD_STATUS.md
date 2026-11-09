# Estado de VOICE FX PRO 0.3.1

- Versión de código: 0.3.1 (versionCode 6).
- Se mantiene el motor nativo Oboe/AAudio y la cadena DSP.
- La pestaña APLICACIÓN ahora aclara que seleccionar/abrir un destino no redirige su micrófono.
- El panel de compatibilidad distingue captura propia activa de recepción real por una app externa.
- Se documenta la limitación de Android estándar sin ROOT: no hay API pública para micrófono virtual universal ni inyección en la captura privada de otras apps.
- Se detallan rutas soportables: compartir/importar WAV o interfaz USB/mezclador con retorno/loopback compatible.
- Validaciones ejecutadas en este entorno: `tools/gen_params.py --check`, `tools/check_native_sync.py`, `tools/lint_kotlin.py` y `native_tests/run_all.sh` (32/32 pruebas DSP aprobadas).
- Compilación Android/Gradle y pruebas físicas en Samsung: pendientes; este entorno no tiene Gradle ni Android SDK.
- No se afirma que la app externa reciba la voz procesada hasta comprobarlo mediante una grabación hecha desde esa app.
