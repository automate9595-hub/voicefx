# Resultados de pruebas — VOICE FX PRO 0.3.0

Ejecutadas en este entorno con `bash native_tests/run_all.sh` (g++ 64 bits + Python/numpy/scipy). Señal de prueba: voz sintética (pulso glotal + formantes de vocal /a/, vibrato, jitter, shimmer, respiración) y ruido/tonos de referencia.

```
t_psola.cpp: In function 'int main(int, char**)':
t_stress.cpp: In function 'int main()':
bytes de audio=480000, tamaño de archivo=480044, descartadas=0
OK
fs= 48000 bloque=  64 parámetros aleatorios: muestras no finitas=0, máx |y|=0.971, CPU media 5.92% (pico de bloque 106.7%)
fs= 48000 bloque= 192 parámetros aleatorios: muestras no finitas=0, máx |y|=0.973, CPU media 6.79% (pico de bloque 48.4%)
fs= 48000 bloque= 240 parámetros aleatorios: muestras no finitas=0, máx |y|=0.974, CPU media 7.88% (pico de bloque 59.7%)
fs= 48000 bloque= 480 parámetros aleatorios: muestras no finitas=0, máx |y|=0.974, CPU media 14.01% (pico de bloque 55.0%)
fs= 48000 bloque=1024 parámetros aleatorios: muestras no finitas=0, máx |y|=0.958, CPU media 3.08% (pico de bloque 8.3%)
fs= 44100 bloque=  64 parámetros aleatorios: muestras no finitas=0, máx |y|=0.971, CPU media 5.92% (pico de bloque 122.2%)
fs= 44100 bloque= 192 parámetros aleatorios: muestras no finitas=0, máx |y|=0.976, CPU media 6.36% (pico de bloque 56.6%)
fs= 44100 bloque= 240 parámetros aleatorios: muestras no finitas=0, máx |y|=0.976, CPU media 7.36% (pico de bloque 82.7%)
fs= 44100 bloque= 480 parámetros aleatorios: muestras no finitas=0, máx |y|=0.977, CPU media 11.49% (pico de bloque 222.3%)
fs= 44100 bloque=1024 parámetros aleatorios: muestras no finitas=0, máx |y|=0.959, CPU media 2.79% (pico de bloque 10.7%)
fs= 16000 bloque=  64 parámetros aleatorios: muestras no finitas=0, máx |y|=0.980, CPU media 1.99% (pico de bloque 18.4%)
fs= 16000 bloque= 192 parámetros aleatorios: muestras no finitas=0, máx |y|=0.980, CPU media 2.13% (pico de bloque 32.7%)
fs= 16000 bloque= 240 parámetros aleatorios: muestras no finitas=0, máx |y|=0.980, CPU media 4.79% (pico de bloque 27.1%)
fs= 16000 bloque= 480 parámetros aleatorios: muestras no finitas=0, máx |y|=0.980, CPU media 4.68% (pico de bloque 21.3%)
fs= 16000 bloque=1024 parámetros aleatorios: muestras no finitas=0, máx |y|=0.968, CPU media 0.86% (pico de bloque 1.7%)
fs= 48000 bloque= 192 parámetros por defecto  : muestras no finitas=0, máx |y|=0.951, CPU media 3.92% (pico de bloque 32.0%)
PEOR CASO (todos los efectos y todas las voces al máximo): CPU media 6.40%, pico de bloque 60.9%
RESULTADO: todas las pruebas de estrés correctas
PASS Nota sostenida 35 c bemol -> nota exacta       errores [1.0, 1.6, 1.1, 1.8] cents
PASS Natural: media a <5 c de la nota               [1.6, 0.3, -1.1, 2.2] (entrada [-34.7, 30.9, -19.4, 20.8])
PASS Natural: conserva vibrato (desv > 6 c)         desv [10.2, 9.4, 10.5, 9.6]
PASS Duro: media <2 c y tono plano (desv < 3 c)     [(-0.4, 0.5), (0.1, 0.7), (0.1, 0.9), (0.0, 0.7)]
PASS Sutil (70 %): corrige parcialmente             [-9.5, 12.4, -9.9, 11.8]
PASS Apagado: transparente (<3 c de la entrada)     [0.1, -0.3, 0.9, -0.1]
RESULTADO: OK
PASS Transparencia (todo en neutro)     correlación 0.979 (filtro anti-rumble 60 Hz), diferencia de nivel -0.00 dB
PASS Bypass (voz seca exacta)           error máx 0.00000
PASS Reverb (cola)                      cola con reverb -38.9 dBFS vs seco -180.0 dBFS
PASS Echo (repetición a 300 ms)         eco a 300 ms, nivel 0.29
PASS Echo sincronizado (120 BPM, 1/8)   nivel en 250 ms: 0.29
PASS Flanger (muescas/peine espectral)  rugosidad espectral 2.42 -> 4.90 dB
PASS Phaser (muescas/peine espectral)   rugosidad espectral 2.42 -> 9.11 dB
PASS Chorus (voces moduladas)           diferencia con la seca 0.75; modulación de tono 415.8 cents
PASS Vibrato (±cents a 5,5 Hz)          oscilación pico-pico 113 cents (esperado ~100-120)
PASS Compresor (rango dinámico)         diferencia fuerte/débil 10.0 dB -> 1.8 dB
PASS De-esser (banda sibilante)         banda 6-8 kHz: -14.0 dB; graves intactos -0.0 dB
PASS Puerta de ruido                    ruido -48.0 -> -86.7 dBFS
PASS EQ graves +                        70-110 Hz: +7.9 dB
PASS EQ graves -                        70-110 Hz: -7.7 dB
PASS EQ medios +                        1000-2000 Hz: +10.8 dB
PASS EQ agudos +                        10000-16000 Hz: +11.1 dB
PASS EQ agudos -                        10000-16000 Hz: -11.0 dB
PASS Drive / distorsión (armónicos)     3er armónico respecto a fundamental: -135 dB -> -15 dB
PASS Telephone (banda limitada)         <150 Hz -38 dB; >5 kHz -24 dB
PASS Robot metálico (ring-mod)          bandas laterales 330/550 Hz presentes: 170 dB
PASS Robot monótono (pitch bloqueado)   desviación de tono 19.7 c (natural) -> 0.6 c (robot); nota 220.1 Hz (A3=220)
PASS Armonía de tercera (G3->B3)        energía 247 Hz: +36.9 dB
PASS Armonía de quinta (G3->D4)         energía 294 Hz: +33.3 dB
PASS Octava arriba                      energía 392 Hz: +101.2 dB
PASS Octava abajo                       energía 98 Hz: +77.1 dB
PASS Doblador de voz                    energía añadida relativa a la voz: 1.65
PASS Formantes +5 st (timbre)           centroide 584 -> 783 Hz; f0 196.2 -> 196.1 Hz
PASS Formantes -5 st (timbre)           centroide 584 -> 499 Hz
PASS Cambio de pitch +7 st              293.8 Hz (esperado 293.7 Hz, error +0.8 c)
PASS Cambio de pitch -7 st              130.9 Hz (esperado 130.8 Hz, error +0.8 c)
PASS Cambio de pitch +12 st             392.2 Hz (esperado 392.0 Hz, error +0.9 c)
PASS Mezcla 0 % = voz seca              correlación con la seca 0.9791

RESUMEN: 32 / 32 pruebas aprobadas
```
