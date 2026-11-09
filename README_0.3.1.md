# VOICE FX PRO 0.3.1

Versión enfocada en aclarar y medir la compatibilidad real con aplicaciones externas en Android estándar, sin ROOT.

## Importante: entrada de micrófono externa

VOICE FX PRO procesa el micrófono dentro de su propio proceso y puede monitorizar la señal procesada. En Android estándar, una aplicación de terceros no dispone de una API pública para crear un micrófono virtual universal ni para sustituir la entrada privada de todas las demás aplicaciones. La selección de una aplicación en la pestaña APLICACIÓN sirve para abrirla o compartir una grabación; no enruta su micrófono.

## Rutas que sí se contemplan

1. **Grabación e importación:** procesar la voz, guardar WAV y compartir/importar el archivo en otra app. No es tiempo real.
2. **Hardware externo:** interfaz USB o mezclador con salida y retorno/loopback compatible, si Android y la aplicación de destino permiten seleccionar esa entrada.
3. **Captura compartida oportunista:** Android puede permitir que el motor siga capturando, o puede silenciarlo cuando otra app obtiene prioridad. El estado de silencio se muestra cuando el sistema lo informa; no puede evitarse desde una app normal.

## Qué verifica la pantalla APLICACIÓN

- Si el motor propio está activo.
- Si Android informa que nuestra sesión fue silenciada.
- Si existen otras sesiones de captura observables.
- Si se detecta una interfaz USB.
- Si hay una app seleccionada para abrir/importar.

Ninguno de esos indicadores, por sí solo, demuestra que una app externa esté recibiendo el audio procesado. La validación real requiere grabar desde la app externa y escuchar el archivo resultante.

## Compilar y probar

Se mantiene el flujo de GitHub Actions definido en `.github/workflows/android.yml`. La versión es `0.3.1` (versionCode 6).
