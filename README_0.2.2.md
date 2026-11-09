# VOICE FX PRO 0.2.2

## Novedades
- Solapa **Aplicación**: enumera apps instaladas con acceso directo, permite elegir una y abrirla.
- Solapa **Monitor**: permite escuchar la señal procesada en tiempo real, apagar el monitoreo local y regular el volumen.
- Muestra la nota/frecuencia detectada.
- La app elegida queda guardada.

## Límite de Android
El selector solo elige y abre la app de destino. No crea un micrófono virtual ni inyecta audio en otra app. Android puede silenciar la captura de VOICE FX PRO cuando la app de destino toma el micrófono. La salida procesada hacia otra app requiere una ruta externa compatible o integración específica comprobada.

## Estado de compilación
No se pudo compilar el APK en este entorno porque no hay Gradle ni Android SDK instalados. Usar GitHub Actions o Android Studio para compilar y ejecutar pruebas en un teléfono real.
