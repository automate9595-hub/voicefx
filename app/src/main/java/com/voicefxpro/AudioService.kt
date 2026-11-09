package com.voicefxpro

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

/**
 * Servicio en primer plano del motor de audio.
 *  - Ruta principal: motor nativo C++ con Oboe (AAudio) full-duplex y baja latencia.
 *  - Respaldo: AudioRecord/AudioTrack con la cadena Kotlin original, sólo si la biblioteca nativa no carga o Oboe no abre.
 */
class AudioService : Service() {
    @Volatile private var active = false
    private var legacyWorker: Thread? = null
    @Volatile private var legacyRecord: AudioRecord? = null
    @Volatile private var legacyTrack: AudioTrack? = null
    private val channelId = "voice_fx_audio"
    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var focusMuted = false
    private var deviceCallback: AudioDeviceCallback? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var lastMessage = ""

    private val prefs by lazy { getSharedPreferences("voicefx", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ParamStore.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEngine()
            ACTION_RESTART -> if (active) restartEngine()
            ACTION_REFRESH -> Unit
            else -> {
                promoteToForeground("Preparando motor de audio…")
                if (!active) startEngine()
            }
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, 7, notification(text), type)
    }

    // ---------------------------------------------------------------- motor

    private fun startEngine() {
        active = true
        EngineMonitor.message = "Iniciando…"
        requestFocus()
        registerWatchers()
        thread(name = "VoiceFxStart") {
            val inId = prefs.getInt("in_device", 0)
            val outId = prefs.getInt("out_device", 0)
            var started = false
            if (NativeEngine.available && NativeEngine.paramsInSync()) {
                val rc = NativeEngine.nStart(inId, outId)
                if (rc == 0) {
                    started = true
                    EngineMonitor.legacy = false
                    ParamStore.pushAllToNative()
                    applyMonitorToNative()
                    setMessage("Motor nativo Oboe activo · baja latencia")
                } else {
                    setMessage("Oboe no pudo abrir el audio (código $rc). Usando motor de respaldo.")
                }
            }
            if (!started && active) startLegacy()
            handler.post(statusTick)
        }
    }

    private fun restartEngine() {
        if (EngineMonitor.legacy) {
            stopLegacy(); startLegacy(); return
        }
        thread(name = "VoiceFxRestart") {
            val rc = NativeEngine.nStart(prefs.getInt("in_device", 0), prefs.getInt("out_device", 0))
            if (rc == 0) { ParamStore.pushAllToNative(); applyMonitorToNative(); setMessage("Dispositivos cambiados · motor reiniciado") }
            else setMessage("No se pudo reiniciar el audio (código $rc)")
        }
    }

    private fun stopEngine() {
        active = false
        handler.removeCallbacks(statusTick)
        if (NativeEngine.available) { try { NativeEngine.nStopRecording() } catch (_: Throwable) {}; NativeEngine.nStop() }
        stopLegacy()
        unregisterWatchers()
        abandonFocus()
        EngineMonitor.legacy = false
        EngineMonitor.legacyState = 0
        EngineMonitor.message = "Motor detenido"
        EngineMonitor.resetLegacyMeters()
        EngineMonitor.status[NativeEngine.S.STATE] = 0
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (active) { active = false; if (NativeEngine.available) NativeEngine.nStop(); stopLegacy() }
        handler.removeCallbacks(statusTick)
        unregisterWatchers()
        super.onDestroy()
    }

    private fun applyMonitorToNative() {
        if (!NativeEngine.available || EngineMonitor.legacy) return
        val on = prefs.getBoolean("monitor_enabled", true) && !focusMuted
        NativeEngine.nSetMonitor(on, prefs.getInt("monitor_volume", 85).coerceIn(0, 100) / 100f)
    }

    // ---------------------------------------------------------------- vigilancia periódica

    private val statusTick = object : Runnable {
        override fun run() {
            if (!active) return
            EngineMonitor.poll()   // mantiene el estado actualizado aunque la interfaz esté en segundo plano
            applyMonitorToNative()
            val s = EngineMonitor.status
            val msg = when {
                !EngineMonitor.legacy && s[NativeEngine.S.STATE] == 3 -> "Reconectando audio (cambio de dispositivo)…"
                !EngineMonitor.legacy && s[NativeEngine.S.STATE] == 2 -> "Error de audio (código ${s[NativeEngine.S.LAST_ERROR]})"
                EngineMonitor.inputSilent -> "Micrófono silenciado: otra app tiene prioridad de captura"
                EngineMonitor.legacy -> "Motor de respaldo activo"
                else -> "Motor activo · efectos en tiempo real"
            }
            setMessage(msg)
            handler.postDelayed(this, 1000)
        }
    }

    private fun setMessage(msg: String) {
        EngineMonitor.message = msg
        if (msg == lastMessage) return
        lastMessage = msg
        try { getSystemService(NotificationManager::class.java).notify(7, notification(msg)) } catch (_: Throwable) {}
    }

    // ---------------------------------------------------------------- foco de audio, dispositivos y grabaciones ajenas

    private fun requestFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { change ->
                    // Llamada entrante u otra app con foco: silenciar el monitoreo sin detener el motor.
                    focusMuted = change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    applyMonitorToNative()
                }.build()
            focusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null; focusMuted = false
    }

    private fun registerWatchers() {
        val am = audioManager ?: return
        val dc = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { EngineMonitor.devicesRevision.intValue++ }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { EngineMonitor.devicesRevision.intValue++ }
        }
        deviceCallback = dc
        am.registerAudioDeviceCallback(dc, handler)
        if (Build.VERSION.SDK_INT >= 29) {
            val rc = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                    val list = configs ?: emptyList()
                    val ours = EngineMonitor.status[NativeEngine.S.SESSION_ID]
                    var silenced = false
                    var others = 0
                    for (c in list) {
                        val mine = (ours > 0 && c.clientAudioSessionId == ours) || (EngineMonitor.legacy && c.clientAudioSessionId == legacyRecord?.audioSessionId)
                        if (mine) { if (c.isClientSilenced) silenced = true } else others++
                    }
                    EngineMonitor.silencedBySystem = silenced
                    EngineMonitor.otherRecorders = others
                }
            }
            recordingCallback = rc
            am.registerAudioRecordingCallback(rc, handler)
        }
    }

    private fun unregisterWatchers() {
        val am = audioManager ?: return
        deviceCallback?.let { am.unregisterAudioDeviceCallback(it) }; deviceCallback = null
        if (Build.VERSION.SDK_INT >= 29) recordingCallback?.let { am.unregisterAudioRecordingCallback(it) }
        recordingCallback = null
        EngineMonitor.silencedBySystem = false; EngineMonitor.otherRecorders = 0
    }

    // ---------------------------------------------------------------- motor de respaldo (Kotlin)

    private fun startLegacy() {
        EngineMonitor.legacy = true
        EngineMonitor.legacyState = 1
        setMessage("Motor de respaldo activo (sin Oboe)")
        legacyWorker = thread(name = "VoiceFxLegacy") { legacyLoop() }
    }

    private fun stopLegacy() {
        try { legacyRecord?.stop() } catch (_: Exception) {}
        try { legacyTrack?.pause() } catch (_: Exception) {}
        legacyWorker?.join(1000)
        legacyWorker = null
    }

    private fun legacyLoop() {
        val am = getSystemService(AudioManager::class.java)
        val preferred = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val rate = listOf(preferred, 48000, 44100, 16000).distinct().firstOrNull {
            AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 &&
                AudioTrack.getMinBufferSize(it, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        } ?: -1
        if (rate < 0) { EngineMonitor.legacyState = 2; setMessage("El dispositivo no admite la configuración de audio"); return }
        val inMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var input: AudioRecord? = null
        var output: AudioTrack? = null
        try {
            val formatIn = AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            input = try {
                AudioRecord.Builder().setAudioSource(if (Build.VERSION.SDK_INT >= 24) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(formatIn).setBufferSizeInBytes(max(inMin, rate / 50)).build()
            } catch (_: Exception) {
                AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(formatIn).setBufferSizeInBytes(max(inMin, rate / 50)).build()
            }
            output = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(outMin, rate / 25)).setTransferMode(AudioTrack.MODE_STREAM)
                .apply { if (Build.VERSION.SDK_INT >= 26) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }.build()
            val inp: AudioRecord = input ?: throw IllegalStateException("Sin entrada de audio")
            val outp: AudioTrack = output ?: throw IllegalStateException("Sin salida de audio")
            if (inp.state != AudioRecord.STATE_INITIALIZED || outp.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("No se pudo inicializar el audio")
            legacyRecord = inp
            legacyTrack = outp
            inp.startRecording(); outp.play()
            val dsp = VoiceDsp(rate)
            val tracker = PitchTracker(rate)
            val buffer = ShortArray(256)
            val silent = ShortArray(256)
            var peakIn = 0f
            var peakOut = 0f
            while (active && EngineMonitor.legacy) {
                val n = inp.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (n > 0) {
                    dsp.reverb = ParamStore.get(Param.REVERB) / 100f
                    dsp.echo = ParamStore.get(Param.ECHO) / 100f
                    dsp.chorus = ParamStore.get(Param.CHORUS) / 100f
                    dsp.compression = ParamStore.get(Param.COMPRESSION) / 100f
                    dsp.drive = ParamStore.get(Param.DRIVE) / 100f
                    dsp.bass = (ParamStore.get(Param.BASS) - 50f) / 100f
                    dsp.treble = (ParamStore.get(Param.TREBLE) - 50f) / 100f
                    dsp.gate = ParamStore.get(Param.GATE) / 100f
                    dsp.wet = ParamStore.get(Param.MIX) / 100f
                    var pk = 0f
                    for (i in 0 until n) pk = max(pk, abs(buffer[i] / 32768f))
                    val pitch = tracker.accept(buffer, n)
                    dsp.process(buffer, n)
                    var pko = 0f
                    for (i in 0 until n) pko = max(pko, abs(buffer[i] / 32768f))
                    peakIn = max(pk, peakIn * 0.9f); peakOut = max(pko, peakOut * 0.9f)
                    val m = EngineMonitor.meters
                    m[NativeEngine.M.IN_PEAK] = peakIn; m[NativeEngine.M.OUT_PEAK] = peakOut
                    m[NativeEngine.M.F0] = pitch?.hz ?: 0f
                    m[NativeEngine.M.VOICED] = if (pitch != null) 1f else 0f
                    val monitorOn = prefs.getBoolean("monitor_enabled", true) && !focusMuted
                    outp.setVolume(if (monitorOn) prefs.getInt("monitor_volume", 85).coerceIn(0, 100) / 100f else 0f)
                    outp.write(if (monitorOn) buffer else silent, 0, n, AudioTrack.WRITE_BLOCKING)
                } else if (n < 0) {
                    if (active) { EngineMonitor.legacyState = 2; setMessage("Error de captura de audio: $n") }
                    break
                }
            }
        } catch (e: Exception) {
            if (active) { EngineMonitor.legacyState = 2; setMessage(e.message ?: "No se pudo iniciar el motor de audio") }
        } finally {
            try { input?.stop() } catch (_: Exception) {}; input?.release()
            try { output?.stop() } catch (_: Exception) {}; output?.release()
            legacyRecord = null; legacyTrack = null
        }
    }

    // ---------------------------------------------------------------- notificación

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(channelId, "VOICE FX PRO", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(message: String): Notification {
        val stop = PendingIntent.getService(this, 8, Intent(this, AudioService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 9, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VOICE FX PRO")
            .setContentText(message)
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.voicefxpro.START"
        const val ACTION_STOP = "com.voicefxpro.STOP"
        const val ACTION_REFRESH = "com.voicefxpro.REFRESH"
        const val ACTION_RESTART = "com.voicefxpro.RESTART"

        /** Carpeta de grabaciones (sin permisos extra: almacenamiento propio de la app). */
        fun recordingsDir(context: Context): File {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "VoiceFXPro")
            dir.mkdirs()
            return dir
        }

        fun newRecordingFile(context: Context): File {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            return File(recordingsDir(context), "VoiceFXPro_$stamp.wav")
        }
    }
}
