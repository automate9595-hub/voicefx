package com.voicefxpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchTrackerTest {
    @Test fun detectsReferenceTones() {
        for (frequency in listOf(110.0, 196.0, 220.0, 440.0, 880.0)) {
            val tracker = PitchTracker(48_000)
            val samples = ShortArray(256)
            var phase = 0.0
            var detected: PitchTracker.Pitch? = null
            repeat(100) {
                for (i in samples.indices) {
                    samples[i] = (sin(phase) * 12_000).toInt().toShort()
                    phase += 2.0 * PI * frequency / 48_000.0
                }
                detected = tracker.accept(samples, samples.size) ?: detected
            }
            assertNotNull("No se detectó $frequency Hz", detected)
            val error = kotlin.math.abs(detected!!.hz - frequency) / frequency
            assertEquals("Error excesivo para $frequency Hz", true, error < 0.03)
        }
    }

    @Test fun silenceDoesNotProducePitch() {
        val tracker = PitchTracker(48_000)
        val silence = ShortArray(256)
        var detected: PitchTracker.Pitch? = null
        repeat(100) { detected = tracker.accept(silence, silence.size) }
        assertNull(detected)
    }
}
