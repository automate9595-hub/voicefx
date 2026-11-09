package com.voicefxpro

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceDspTest {
    @Test fun processedSamplesStayInPcmRange() {
        val dsp = VoiceDsp(48_000).apply {
            reverb = 0.7f; echo = 0.6f; chorus = 0.5f
            compression = 0.8f; drive = 0.7f; bass = 0.3f; treble = 0.2f
            gate = 0.1f; wet = 0.9f
        }
        val samples = ShortArray(256)
        var phase = 0.0
        repeat(20) {
            for (i in samples.indices) {
                samples[i] = (sin(phase) * 30_000).toInt().toShort()
                phase += 2.0 * PI * 220.0 / 48_000.0
            }
            dsp.process(samples, samples.size)
            assertTrue(samples.all { it.toInt() in Short.MIN_VALUE..Short.MAX_VALUE })
        }
    }

    @Test fun silenceRemainsFinite() {
        val dsp = VoiceDsp(48_000)
        val silence = ShortArray(512)
        repeat(10) { dsp.process(silence, silence.size) }
        assertTrue(silence.all { it.toInt() == 0 })
    }
}
