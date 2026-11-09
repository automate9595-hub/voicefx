package com.voicefxpro

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.roundToInt

/** Lightweight monophonic pitch meter (not a pitch shifter or Auto-Tune). */
class PitchTracker(private val inputRate: Int = 48_000) {
    private val downsampleFactor = 4
    private val rate = inputRate / downsampleFactor
    private val window = FloatArray(512)
    private var write = 0
    private var inputCounter = 0
    private var filled = 0
    private var sinceAnalysis = 0
    var latest: Pitch? = null
        private set

    data class Pitch(val hz: Float, val note: String, val cents: Int, val confidence: Float)

    fun accept(samples: ShortArray, count: Int): Pitch? {
        var result: Pitch? = null
        for (i in 0 until count) {
            inputCounter++
            if (inputCounter % downsampleFactor != 0) continue
            window[write] = samples[i] / 32768f
            write = (write + 1) % window.size
            if (filled < window.size) filled++
            sinceAnalysis++
            if (filled == window.size && sinceAnalysis >= 256) {
                sinceAnalysis = 0
                latest = analyze()
                result = latest
            }
        }
        return latest ?: result
    }

    private fun analyze(): Pitch? {
        val ordered = FloatArray(window.size)
        var energy = 0.0
        for (i in window.indices) {
            val v = window[(write + i) % window.size]
            ordered[i] = v
            energy += v * v
        }
        val rms = sqrt((energy / ordered.size).toFloat())
        if (rms < 0.008f) return null

        val minLag = (rate / 1000).coerceAtLeast(2)
        val maxLag = (rate / 70).coerceAtMost(ordered.size / 2)
        val correlations = FloatArray(maxLag + 1)
        var bestLag = -1
        var best = 0f
        for (lag in minLag..maxLag) {
            var cross = 0.0
            var a = 0.0
            var b = 0.0
            val limit = ordered.size - lag
            var i = 0
            while (i < limit) {
                val x = ordered[i].toDouble()
                val y = ordered[i + lag].toDouble()
                cross += x * y
                a += x * x
                b += y * y
                i += 2
            }
            val denom = sqrt(a * b)
            val corr = if (denom > 1e-12) (cross / denom).toFloat() else 0f
            correlations[lag] = corr
            if (corr > best) { best = corr; bestLag = lag }
        }
        // Choose the first strong local peak, avoiding octave-down errors caused by
        // later autocorrelation peaks at integer multiples of the true period.
        var firstPeak = -1
        for (lag in (minLag + 1) until maxLag) {
            val c = correlations[lag]
            if (c >= 0.86f && c >= correlations[lag - 1] && c >= correlations[lag + 1]) {
                firstPeak = lag
                break
            }
        }
        if (firstPeak > 0) bestLag = firstPeak
        best = correlations.getOrElse(bestLag) { 0f }
        if (bestLag <= 0 || best < 0.72f) return null

        // Parabolic interpolation around the strongest autocorrelation peak.
        fun correlation(lag: Int): Double {
            var cross = 0.0; var a = 0.0; var b = 0.0
            val limit = ordered.size - lag
            var i = 0
            while (i < limit) {
                val x = ordered[i].toDouble(); val y = ordered[i + lag].toDouble()
                cross += x * y; a += x * x; b += y * y; i += 2
            }
            val d = sqrt(a * b)
            return if (d > 1e-12) cross / d else 0.0
        }
        val left = if (bestLag > minLag) correlation(bestLag - 1) else best.toDouble()
        val center = best.toDouble()
        val right = if (bestLag < maxLag) correlation(bestLag + 1) else best.toDouble()
        val denom = left - 2.0 * center + right
        val shift = if (abs(denom) > 1e-9) (0.5 * (left - right) / denom).coerceIn(-0.5, 0.5) else 0.0
        val hz = (rate / (bestLag + shift)).toFloat()
        if (!hz.isFinite() || hz !in 65f..1100f) return null
        val midi = (69.0 + 12.0 * (kotlin.math.ln(hz / 440.0) / kotlin.math.ln(2.0))).roundToInt()
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val note = "${names[((midi % 12) + 12) % 12]}${midi / 12 - 1}"
        val exact = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val cents = (1200.0 * (kotlin.math.ln(hz / exact) / kotlin.math.ln(2.0))).toInt()
        return Pitch(hz, note, cents, best)
    }
}
