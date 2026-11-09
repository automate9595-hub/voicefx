package com.voicefxpro

import androidx.compose.runtime.mutableIntStateOf

/** Lectura periódica (desde la interfaz) de medidores y estado del motor; también recibe datos del motor de respaldo. */
object EngineMonitor {
    val meters = FloatArray(NativeEngine.M.COUNT)
    val status = IntArray(NativeEngine.S.COUNT)

    /** Cambia en cada lectura: los composables que lo leen se redibujan. */
    val tick = mutableIntStateOf(0)

    /** true cuando se usa el motor de respaldo (AudioRecord/AudioTrack en Kotlin). */
    @Volatile var legacy: Boolean = false
    @Volatile var legacyState: Int = 0          // 0 detenido, 1 activo, 2 error
    /** La interfaz pidió arrancar y el motor aún no informó actividad. */
    @Volatile var requested: Boolean = false
    @Volatile var message: String = "Motor detenido"
    @Volatile var otherRecorders: Int = 0       // otras apps grabando (Android 10+)
    @Volatile var silencedBySystem: Boolean = false
    val devicesRevision = mutableIntStateOf(0)  // cambia al conectar/desconectar dispositivos de audio

    fun poll() {
        if (NativeEngine.available && !legacy) {
            NativeEngine.nGetMeters(meters)
            NativeEngine.nGetStatus(status)
        } else {
            status[NativeEngine.S.STATE] = legacyState
        }
        tick.intValue++
    }

    val state: Int get() = status[NativeEngine.S.STATE]
    val running: Boolean get() = state == 1
    val restarting: Boolean get() = state == 3
    val inputSilent: Boolean get() = status[NativeEngine.S.INPUT_SILENT] == 1 || silencedBySystem
    fun meter(i: Int): Float = meters[i]
    fun resetLegacyMeters() { for (i in meters.indices) meters[i] = 0f }
}
