package com.voicefxpro

/** Escalas musicales (mismos intervalos que el motor nativo: PitchCorrector.h). */
object Theory {
    val scaleNames = listOf("Cromática", "Mayor", "Menor natural", "Menor armónica", "Pentatónica mayor", "Pentatónica menor", "Blues", "Dórica", "Mixolidia")
    private val intervals = listOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), intArrayOf(0, 2, 4, 5, 7, 9, 11), intArrayOf(0, 2, 3, 5, 7, 8, 10),
        intArrayOf(0, 2, 3, 5, 7, 8, 11), intArrayOf(0, 2, 4, 7, 9), intArrayOf(0, 3, 5, 7, 10), intArrayOf(0, 3, 5, 6, 7, 10),
        intArrayOf(0, 2, 3, 5, 7, 9, 10), intArrayOf(0, 2, 4, 5, 7, 9, 10)
    )

    /** Máscara de 12 bits (bit 0 = Do) con las notas de la escala. */
    fun scaleMask(key: Int, scale: Int): Int {
        var mask = 0
        for (iv in intervals[scale.coerceIn(0, intervals.size - 1)]) mask = mask or (1 shl ((key + iv) % 12))
        return mask
    }
}
