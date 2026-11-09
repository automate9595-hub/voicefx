package com.voicefxpro

/** Puente JNI hacia el motor nativo (C++/Oboe). Si la biblioteca no carga, `available` es false y se usa el motor de respaldo. */
object NativeEngine {
    @Volatile
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("voicefx")
            true
        } catch (_: Throwable) {
            false
        }
    }

    external fun nParamCount(): Int
    external fun nStart(inId: Int, outId: Int): Int
    external fun nStop()
    external fun nSetParam(idx: Int, v: Float)
    external fun nSetMonitor(on: Boolean, vol: Float)
    external fun nGetMeters(out: FloatArray)
    external fun nGetStatus(out: IntArray)
    external fun nStartRecording(path: String, sampleRate: Int): Boolean
    external fun nStopRecording(): Long
    external fun nRecordingSeconds(): Double

    /** Índices de medidores (deben coincidir con enum Meter en VoiceEngine.h; verificado por tools/check_native_sync.py). */
    object M {
        const val IN_PEAK = 0; const val IN_RMS = 1; const val OUT_PEAK = 2; const val OUT_RMS = 3
        const val COMP_GR = 4; const val F0 = 5; const val MEAS_MIDI = 6; const val TARGET_NOTE = 7
        const val OUT_MIDI = 8; const val VOICED = 9; const val GATE_OPEN = 10; const val CPU = 11
        const val LATENCY_MS = 12; const val CONF = 13; const val CENTS = 14; const val DEESS_GR = 15
        const val COUNT = 16
    }

    /** Índices de estado (deben coincidir con enum Status en OboeEngine.h). */
    object S {
        const val STATE = 0; const val SAMPLE_RATE = 1; const val BURST = 2; const val BUFFER = 3
        const val XRUNS = 4; const val INPUT_SILENT = 5; const val API = 6; const val EXCLUSIVE = 7
        const val RESTARTS = 8; const val IN_DEVICE = 9; const val OUT_DEVICE = 10; const val SESSION_ID = 11
        const val LAST_ERROR = 12; const val LATENCY_OUT_US = 13; const val RECORDING = 14
        const val COUNT = 15
    }

    fun setParam(p: Param, v: Float) {
        if (available) nSetParam(p.ordinal, v)
    }

    fun paramsInSync(): Boolean = available && nParamCount() == Param.values().size
}
