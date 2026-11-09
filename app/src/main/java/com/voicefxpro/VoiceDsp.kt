package com.voicefxpro

import kotlin.math.*

/** Lightweight mono real-time effects chain. No third-party BandLab code/assets are used. */
class VoiceDsp(private val sampleRate: Int) {
    var reverb = 0.18f
    var echo = 0f
    var chorus = 0f
    var compression = 0.25f
    var drive = 0f
    var bass = 0f
    var treble = 0f
    var gate = 0f
    var wet = 0.7f
    private val delay = FloatArray(sampleRate * 2)
    private var delayPos = 0
    private val comb1 = FloatArray((sampleRate * 0.029).toInt().coerceAtLeast(64))
    private val comb2 = FloatArray((sampleRate * 0.037).toInt().coerceAtLeast(64))
    private var c1 = 0
    private var c2 = 0
    private var low = 0f
    private var env = 0.02f
    private var phase = 0.0

    fun process(pcm: ShortArray, count: Int) {
        val echoSamples = (sampleRate * 0.24).toInt().coerceIn(1, delay.size - 1)
        val bassAlpha = (2.0 * PI * 150.0 / sampleRate).toFloat()
        for (i in 0 until count) {
            val dry = pcm[i] / 32768f
            env = 0.995f * env + 0.005f * abs(dry)
            var x = if (gate > 0f && env < gate * 0.004f) dry * 0.12f else dry
            low += bassAlpha * (x - low)
            val highPass = x - low
            x += low * bass * 1.8f + highPass * treble * 1.6f
            val threshold = 0.42f - compression * 0.32f
            val absX = abs(x)
            if (absX > threshold && compression > 0f) x = sign(x) * (threshold + (absX - threshold) * (1f - compression * 0.72f))
            if (drive > 0f) x = (x * (1f + drive * 9f)).let { tanh(it) / tanh(1f + drive * 9f) }

            val echoRead = (delayPos - echoSamples + delay.size) % delay.size
            val echoTap = delay[echoRead]
            val mod = if (chorus > 0f) (sin(phase) * chorus * sampleRate * 0.004f).toInt() else 0
            val chorusRead = (delayPos - (sampleRate * 0.018).toInt() + mod + delay.size) % delay.size
            val chorusTap = delay[chorusRead]
            val r1 = comb1[c1]; val r2 = comb2[c2]
            val verb = (r1 + r2) * 0.5f
            val fx = x + echoTap * echo * 0.72f + verb * reverb * 0.62f + chorusTap * chorus * 0.25f
            delay[delayPos] = (x + echoTap * echo * 0.48f + verb * reverb * 0.18f).coerceIn(-1.2f, 1.2f)
            comb1[c1] = (x + r1 * 0.68f).coerceIn(-1.2f, 1.2f)
            comb2[c2] = (x + r2 * 0.63f).coerceIn(-1.2f, 1.2f)
            delayPos = (delayPos + 1) % delay.size
            c1 = (c1 + 1) % comb1.size; c2 = (c2 + 1) % comb2.size
            phase += 2.0 * PI * 0.25 / sampleRate
            if (phase > 2.0 * PI) phase -= 2.0 * PI
            val out = (dry * (1f - wet) + fx * wet).coerceIn(-1f, 1f)
            pcm[i] = (out * 32767f).roundToInt().toShort()
        }
    }
}
