# VOICE FX PRO — Compatibilidad y criterios de aceptación

## Restricción Android que define el producto

Dos aplicaciones ordinarias no pueden capturar simultáneamente el micrófono con garantía. Android puede dejar una app capturando silencio cuando otra app toma prioridad. `AudioRecordingCallback` permite detectar el silenciamiento, pero no revocarlo ni inyectar audio procesado en la entrada privada de otra app.

## Estrategias de salida

### A. Monitorización local
Micrófono → VOICE FX PRO → DSP → auriculares. Sirve para comprobar la cadena de efectos y ajustar la voz, pero no implica que otra app reciba esa señal.

### B. Captura compartida cuando Android la permita
VOICE FX PRO mantiene el servicio foreground y observa cambios de captura. Es oportunista y depende del sistema, la app de karaoke, el fabricante y la fuente de audio seleccionada.

### C. Retorno de hardware
Teléfono → interfaz USB (salida) → entrada/loopback de una interfaz o mezclador → entrada USB del teléfono/app karaoke. Requiere hardware que admita las rutas necesarias y que Android y la app de karaoke seleccionen esa entrada. No todas las interfaces ofrecen loopback ni todos los teléfonos admiten host USB simultáneo con alimentación suficiente.

## Estado adicional del analizador de tono

El analizador informa la nota estimada, frecuencia en Hz y desviación en cents. Se valida con señales sintéticas en pruebas JVM. No corrige la afinación y no debe anunciarse como Auto-Tune.

## Matriz de pruebas

| Prueba | Resultado esperado |
|---|---|
| Solo VOICE FX PRO | Captura y monitoreo estables |
| Reverb/echo/chorus | Cambio audible al mover controles |
| Otra app abre micrófono | Detectar RUNNING o SILENCED; no fingir éxito |
| Karaoke graba voz procesada | Debe comprobarse escuchando el archivo grabado |
| Interfaz USB + retorno | Karaoke recibe la señal procesada si la ruta se soporta |
| Segundo plano / pantalla apagada | Servicio sigue activo donde Android lo permita |

## Criterio de liberación

No marcar como terminado el requisito principal hasta que una app externa concreta grabe la voz procesada, con el karaoke y modelo de teléfono anotados. Para afirmar compatibilidad general hay que repetirlo en una matriz de fabricantes y versiones Android.


## Decisión de producto para teléfonos sin ROOT (0.3.1)

El objetivo comercial de compatibilidad universal no puede implementarse mediante APIs públicas de una app Android ordinaria. No existe una API pública para publicar un dispositivo de entrada virtual del sistema ni para inyectar PCM en la sesión de captura privada de otra app. No se debe intentar resolverlo con `AudioPlaybackCapture`: esa API captura reproducción de otras apps bajo condiciones concretas, no sustituye el micrófono de entrada.

La pestaña APLICACIÓN debe describir el selector como acceso para abrir/importar audio, no como selector de ruteo de micrófono. El indicador de captura propia activa no equivale a compatibilidad externa. El único criterio de éxito en vivo es una grabación hecha desde la app externa que contenga efectivamente los efectos.

Para el usuario sin ROOT, las rutas soportables son importación de WAV o hardware externo con retorno/loopback compatible. La captura compartida simultánea queda como comportamiento oportunista, no como función garantizada.
