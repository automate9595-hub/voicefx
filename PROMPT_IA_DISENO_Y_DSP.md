# PROMPT PARA OTRA IA — REDISEÑO Y EVOLUCIÓN DE VOICE FX PRO

Actuá como un equipo senior compuesto por diseñador UX/UI especializado en audio profesional, ingeniero Android/Kotlin, ingeniero DSP de audio en tiempo real y especialista en compatibilidad Android.

## Proyecto
Estás trabajando sobre el proyecto Android existente **VOICE FX PRO**. Es una aplicación de efectos vocales en tiempo real. Debés modificar el proyecto existente, no crear una demo desconectada ni reemplazarlo por una maqueta.

## Objetivo principal
Transformar VOICE FX PRO en una aplicación de procesamiento vocal de aspecto profesional, moderna, intuitiva y estable, con efectos audibles en tiempo real y controles claros. La prioridad funcional es procesar la voz capturada por el micrófono y permitir que el usuario escuche la señal procesada y, cuando la plataforma lo permita, utilizarla en otra aplicación (por ejemplo BandLab, Smule, StarMaker o karaoke).

## Reglas técnicas obligatorias
1. Antes de editar, inspeccioná todo el proyecto, su arquitectura, servicios, DSP, permisos, Gradle, pruebas y workflow. Conservá las funciones que ya funcionan.
2. No inventes compatibilidad. Android no ofrece a una app normal un micrófono virtual universal ni permite garantizar que dos aplicaciones capturen simultáneamente el mismo micrófono. Si la app de destino toma el micrófono y Android silencia VOICE FX PRO, detectalo y explicalo claramente.
3. La solapa «Aplicación» debe permitir elegir una aplicación instalada y abrirla. No afirmes que elegirla enruta automáticamente el audio. Implementá únicamente rutas de audio que realmente existan y se puedan verificar.
4. Ofrecé una arquitectura de compatibilidad: captura local cuando esté permitida; detección de silenciamiento; selección de dispositivo de audio; compatibilidad con interfaces USB class-compliant cuando el dispositivo y Android lo soporten; y documentación de conexión a procesador vocal/mezclador externo cuando sea necesaria. No uses APIs privadas ni hacks que rompan Android.
5. El monitoreo en tiempo real debe tener interruptor independiente, volumen, indicador de captura, indicador de silenciamiento y visualización de la nota/frecuencia detectada. Debe poder escucharse por auriculares sin acople; recomendá auriculares cableados.
6. Migrá a Oboe/AAudio con callback de baja latencia si podés hacerlo correctamente. Manejá cambios de dispositivo, sample rate, tamaño de buffer, interrupciones, errores y ciclo de vida del servicio foreground. Evitá bloqueos, asignaciones innecesarias y trabajo pesado dentro del callback.
7. Implementá DSP real, no botones decorativos. Cada control debe afectar el audio. Añadí pruebas unitarias y pruebas de integración donde sea posible.
8. Implementá AutoPitch real: modo automático y manual; selección de nota raíz y escala; retune speed; humanize; rango de corrección; mezcla dry/wet; y conservación de formantes. No declares Auto-Tune terminado hasta que modifique audiblemente la señal.
9. Añadí armonizador y doblador de voz con intervalos configurables (tercera, quinta, octava), pitch shift, formant shift, vibrato, chorus, flanger, phaser, delay sincronizable, reverb, compresor, de-esser, EQ paramétrico, gate/expansor y saturación. Cada efecto debe poder activarse, desactivarse y ajustar parámetros.
10. Creá presets originales inspirados en usos vocales habituales (no copies código, nombres protegidos, assets ni presets propietarios de BandLab): Natural, Pop, Reggaetón, Trap, Rap, Balada, Robot, Telephone, Big Hall, Choir/Harmony y Lo-Fi.
11. Guardá presets y preferencias localmente. Añadí restaurar valores, bypass global, comparar voz seca/procesada y medidor de entrada/salida.
12. La aplicación debe seguir siendo Android nativa, Kotlin y Jetpack Compose, con accesibilidad, textos en español, controles táctiles grandes y soporte para diferentes tamaños de pantalla.
13. No elimines permisos ni cambies el applicationId sin justificarlo. No agregues permisos invasivos innecesarios. Mantené el workflow de GitHub Actions para compilar APK y ejecutar tests.

## Dirección visual
- Estética de estudio vocal profesional, oscura y elegante.
- Fondo carbón/negro azulado; acentos violeta eléctrico y cyan; gradientes discretos, no saturar toda la pantalla.
- Jerarquía visual clara, tarjetas compactas, bordes suaves, buen contraste y tipografía legible.
- Panel principal con gran botón de encendido, estado del motor y medidores de entrada/salida.
- Navegación inferior o pestañas claras: **Estudio**, **Efectos**, **AutoPitch**, **Aplicación**, **Monitor**, **Presets**.
- En «Aplicación»: selector de app instalada, app seleccionada, botón abrir, estado de compatibilidad y explicación de ruta real de audio.
- En «Monitor»: escucha procesada en tiempo real, volumen, salida seleccionada, nota detectada y estado de captura.
- En «Efectos»: controles ordenados por categorías, sliders con valores, interruptores y vista previa auditiva.
- En «AutoPitch»: selector de tonalidad/escala, modo automático/manual, retune speed y humanize.
- Evitá pantallas recargadas, textos diminutos, controles falsos y animaciones que consuman CPU durante el audio.

## Validación obligatoria
- Compilá el APK debug y ejecutá todas las pruebas.
- Probá inicio/detención repetida, permisos denegados, llamadas entrantes, desconexión de auriculares, cambio de salida, segundo plano, pantalla apagada y pérdida de foco.
- Probá al menos dos versiones/fabricantes Android si hay dispositivos disponibles.
- Probá con una aplicación de karaoke real: grabá una muestra y verificá que la grabación contiene la voz procesada. No alcanza con que el sonido se escuche en los auriculares.
- Si Android silencia la captura al abrir el karaoke, reportalo como limitación observada y documentá la solución de hardware necesaria. No marques esa prueba como aprobada.
- Entregá el proyecto completo actualizado, ZIP, APK si la compilación tuvo éxito, lista de archivos modificados, resultados de pruebas y limitaciones pendientes.

## Forma de trabajo
Trabajá sobre el código existente de VOICE FX PRO. Hacé cambios pequeños y coherentes, verificá cada etapa y no borres funcionalidades previas. No te detengas en una propuesta visual: implementá el rediseño y las funciones reales. Si una función no puede implementarse con APIs públicas, explicá la limitación y ofrecé una alternativa técnica verificable.

**Criterio de aceptación final:** VOICE FX PRO debe permitir procesar y monitorear la voz en tiempo real, mostrar con honestidad el estado de captura y demostrar mediante una grabación real cuándo una app externa recibe la señal procesada. La interfaz debe ser profesional, pero la prioridad es que el audio funcione.
