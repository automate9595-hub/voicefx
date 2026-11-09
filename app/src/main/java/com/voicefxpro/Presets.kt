package com.voicefxpro

data class Preset(val name: String, val category: String, val description: String, val values: Map<Param, Float>)

private fun v(vararg pairs: Pair<Param, Float>): Map<Param, Float> = mapOf(*pairs)

/**
 * Presets originales de VOICE FX PRO. Cada uno parte de los valores por defecto y sólo indica lo que cambia.
 * Son configuraciones propias de esta app (no copian presets de otras aplicaciones).
 */
object Presets {
    val all: List<Preset> = listOf(
        Preset("Natural", "Base", "Voz limpia con compresión suave y un toque de sala. Sin corrección de tono.",
            v(Param.REVERB to 12f, Param.DEESS to 20f)),
        Preset("Pop", "Géneros", "Brillante y actual: afinación suave, doble discreto y reverb corta.",
            v(Param.AT_ON to 1f, Param.AT_SPEED to 55f, Param.AT_HUMANIZE to 60f, Param.AT_AMOUNT to 90f, Param.AT_FLEX to 8f,
                Param.COMPRESSION to 45f, Param.DEESS to 30f, Param.BASS to 55f, Param.MID to 52f, Param.TREBLE to 62f,
                Param.CHORUS to 12f, Param.DOUBLER to 15f, Param.ECHO to 10f, Param.ECHO_TIME to 200f, Param.ECHO_FB to 22f,
                Param.REVERB to 26f, Param.REVERB_SIZE to 55f)),
        Preset("Trap", "Géneros", "Afinación rápida y marcada, eco sincronizado, graves y doble de voz.",
            v(Param.AT_ON to 1f, Param.AT_SPEED to 12f, Param.AT_HUMANIZE to 15f, Param.AT_AMOUNT to 100f, Param.AT_FLEX to 3f,
                Param.COMPRESSION to 60f, Param.DEESS to 40f, Param.DRIVE to 10f, Param.BASS to 62f, Param.TREBLE to 60f,
                Param.GATE to 15f, Param.DOUBLER to 30f, Param.DOUBLER_DETUNE to 10f,
                Param.ECHO to 28f, Param.ECHO_SYNC to 1f, Param.BPM to 140f, Param.ECHO_DIV to 2f, Param.ECHO_FB to 40f,
                Param.REVERB to 22f, Param.REVERB_SIZE to 60f)),
        Preset("Reggaetón", "Géneros", "Voz pegada y cálida con afinación media, eco a ritmo y doble.",
            v(Param.AT_ON to 1f, Param.AT_SPEED to 28f, Param.AT_HUMANIZE to 30f, Param.AT_AMOUNT to 95f, Param.AT_FLEX to 5f,
                Param.COMPRESSION to 55f, Param.DEESS to 35f, Param.BASS to 60f, Param.TREBLE to 58f,
                Param.DOUBLER to 35f, Param.CHORUS to 10f, Param.HARM_OCT_UP to 8f,
                Param.ECHO to 20f, Param.ECHO_SYNC to 1f, Param.BPM to 95f, Param.ECHO_DIV to 1f, Param.ECHO_FB to 30f,
                Param.REVERB to 20f)),
        Preset("Rap", "Géneros", "Compresión firme, graves llenos, puerta y doble corto. Sin autotune.",
            v(Param.COMPRESSION to 78f, Param.DRIVE to 8f, Param.BASS to 68f, Param.TREBLE to 58f, Param.GATE to 25f,
                Param.DEESS to 35f, Param.DOUBLER to 20f, Param.DOUBLER_DETUNE to 8f,
                Param.ECHO to 12f, Param.ECHO_TIME to 180f, Param.ECHO_FB to 20f, Param.REVERB to 8f)),
        Preset("Balada", "Géneros", "Afinación muy sutil que respeta el vibrato, reverb amplia y eco lento.",
            v(Param.AT_ON to 1f, Param.AT_SPEED to 90f, Param.AT_HUMANIZE to 85f, Param.AT_AMOUNT to 70f, Param.AT_FLEX to 10f,
                Param.COMPRESSION to 38f, Param.DEESS to 25f, Param.BASS to 55f, Param.TREBLE to 54f, Param.CHORUS to 8f,
                Param.ECHO to 14f, Param.ECHO_TIME to 380f, Param.ECHO_FB to 30f,
                Param.REVERB to 38f, Param.REVERB_SIZE to 70f, Param.REVERB_PRE to 25f)),
        Preset("Robot", "Efectos", "Tono bloqueado en la tónica y timbre metálico.",
            v(Param.AT_ON to 1f, Param.AT_SPEED to 0f, Param.ROBOT_MONO to 100f, Param.ROBOT_METAL to 55f, Param.DRIVE to 20f,
                Param.COMPRESSION to 60f, Param.TREBLE to 66f, Param.ECHO to 12f, Param.REVERB to 10f)),
        Preset("Telephone", "Efectos", "Banda de teléfono (300-3400 Hz) con compresión y algo de saturación.",
            v(Param.PHONE to 100f, Param.COMPRESSION to 65f, Param.DRIVE to 12f, Param.BASS to 15f, Param.TREBLE to 95f,
                Param.GATE to 8f, Param.REVERB to 0f)),
        Preset("Ambient", "Efectos", "Espacio enorme: reverb larga, eco y chorus suave.",
            v(Param.REVERB to 85f, Param.REVERB_SIZE to 85f, Param.REVERB_DAMP to 45f, Param.REVERB_PRE to 30f,
                Param.ECHO to 42f, Param.ECHO_TIME to 420f, Param.ECHO_FB to 50f, Param.CHORUS to 25f,
                Param.COMPRESSION to 18f, Param.TREBLE to 58f)),
        Preset("Voz Pro Estudio", "Voz profesional", "Cadena de estudio transparente: puerta, compresor, de-esser y afinación casi imperceptible.",
            v(Param.GATE to 12f, Param.COMPRESSION to 45f, Param.DEESS to 45f, Param.BASS to 54f, Param.MID to 52f, Param.TREBLE to 60f,
                Param.AT_ON to 1f, Param.AT_SPEED to 120f, Param.AT_HUMANIZE to 85f, Param.AT_AMOUNT to 55f, Param.AT_FLEX to 10f,
                Param.REVERB to 10f, Param.REVERB_SIZE to 35f)),
        Preset("Voz Pro Radio", "Voz profesional", "Presencia de locutor: compresión densa, graves cálidos y control de sibilancia.",
            v(Param.COMPRESSION to 70f, Param.DEESS to 40f, Param.BASS to 62f, Param.MID to 58f, Param.TREBLE to 58f,
                Param.GATE to 20f, Param.DRIVE to 6f, Param.REVERB to 4f)),
        Preset("Voz Pro Brillante", "Voz profesional", "Aire y brillo para cantar: agudos abiertos, doble leve y afinación sutil.",
            v(Param.COMPRESSION to 50f, Param.DEESS to 55f, Param.BASS to 48f, Param.MID to 55f, Param.TREBLE to 72f,
                Param.CHORUS to 10f, Param.DOUBLER to 12f, Param.AT_ON to 1f, Param.AT_SPEED to 100f, Param.AT_HUMANIZE to 80f,
                Param.AT_AMOUNT to 60f, Param.AT_FLEX to 9f, Param.REVERB to 20f, Param.REVERB_SIZE to 45f)),
        Preset("Coro / Armonías", "Armonías", "Tercera, quinta y octava abajo siguiendo la escala elegida.",
            v(Param.HARM_3 to 60f, Param.HARM_5 to 45f, Param.HARM_OCT_DN to 20f, Param.DOUBLER to 25f,
                Param.AT_ON to 1f, Param.AT_SPEED to 60f, Param.AT_HUMANIZE to 60f, Param.AT_AMOUNT to 85f,
                Param.COMPRESSION to 40f, Param.REVERB to 35f, Param.REVERB_SIZE to 60f)),
        Preset("Gran Sala", "Armonías", "Voz natural dentro de una sala grande.",
            v(Param.REVERB to 70f, Param.REVERB_SIZE to 90f, Param.REVERB_DAMP to 40f, Param.REVERB_PRE to 35f,
                Param.ECHO to 18f, Param.COMPRESSION to 35f))
    )
}
