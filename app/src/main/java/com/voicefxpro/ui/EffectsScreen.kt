package com.voicefxpro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicefxpro.Param
import com.voicefxpro.ParamStore

@Composable
fun EffectsScreen() {
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("EFECTOS", color = VfxColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Dim("Todos los controles actúan en vivo. Doble toque sobre un nombre = valor original.")
            }
            ActionButton("RESTABLECER", filled = false, color = VfxColors.Cyan) { ParamStore.resetAll() }
        }

        Panel("Tono y formantes", "Cambio de pitch con PSOLA: la voz cambia de nota sin volverse \"ardilla\" porque los formantes se conservan.") {
            FxSlider(Param.PITCH_SEMI, "Cambio de pitch")
            FxSlider(Param.FORMANT_SEMI, "Cambio de formantes", hint = "Positivo = voz más pequeña/brillante, negativo = más grande/oscura.")
        }

        Panel("Robot", "La monotonía bloquea el tono en la tónica elegida en AUTOPITCH. Lo metálico es modulación en anillo.") {
            FxSlider(Param.ROBOT_MONO)
            FxSlider(Param.ROBOT_METAL)
        }

        Panel("Armonías y doblador", "Las armonías siguen la tonalidad y la escala de AUTOPITCH (tercera y quinta diatónicas).") {
            FxSlider(Param.HARM_3)
            FxSlider(Param.HARM_5)
            FxSlider(Param.HARM_OCT_UP)
            FxSlider(Param.HARM_OCT_DN)
            FxSlider(Param.DOUBLER, hint = "Dos copias retardadas y levemente desafinadas.")
            FxSlider(Param.DOUBLER_DETUNE)
        }

        Panel("Dinámica") {
            FxSlider(Param.GATE, hint = "0 = apagada. Más alto = cierra con ruidos más fuertes.")
            FxSlider(Param.COMPRESSION, hint = "Umbral, relación y ganancia automática juntos.")
            FxSlider(Param.DEESS, hint = "Baja las \"s\" y \"ch\" molestas sin apagar la voz.")
            FxSlider(Param.DEESS_FREQ)
        }

        Panel("Ecualizador y color") {
            FxSlider(Param.BASS, "Graves (110 Hz)")
            FxSlider(Param.MID, "Medios (1,4 kHz)")
            FxSlider(Param.TREBLE, "Agudos (7,5 kHz)")
            FxSlider(Param.DRIVE, hint = "Saturación con sobremuestreo para evitar aliasing.")
            FxSlider(Param.PHONE)
        }

        Panel("Modulación") {
            FxSlider(Param.CHORUS)
            FxSlider(Param.CHORUS_RATE)
            FxSlider(Param.FLANGER)
            FxSlider(Param.FLANGER_RATE)
            FxSlider(Param.FLANGER_FB)
            FxSlider(Param.PHASER)
            FxSlider(Param.PHASER_RATE)
            FxSlider(Param.VIBRATO)
            FxSlider(Param.VIBRATO_RATE)
        }

        EchoPanel()

        Panel("Reverb") {
            FxSlider(Param.REVERB)
            FxSlider(Param.REVERB_SIZE)
            FxSlider(Param.REVERB_DAMP)
            FxSlider(Param.REVERB_PRE)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EchoPanel() {
    val rev = ParamStore.revision.intValue
    var sync by remember(rev) { mutableStateOf(ParamStore.on(Param.ECHO_SYNC)) }
    var div by remember(rev) { mutableIntStateOf(ParamStore.get(Param.ECHO_DIV).toInt()) }
    Panel("Echo / Delay") {
        FxSlider(Param.ECHO)
        FxSlider(Param.ECHO_FB)
        SwitchRow("Sincronizar con el tempo", "El tiempo de eco se calcula desde los BPM", sync) { sync = it; ParamStore.setBool(Param.ECHO_SYNC, it) }
        if (sync) {
            FxSlider(Param.BPM)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1/4", "1/8", "1/8 con puntillo", "1/16").forEachIndexed { i, name ->
                    ChoiceChip(name, div == i, Modifier.weight(1f)) { div = i; ParamStore.set(Param.ECHO_DIV, i.toFloat()) }
                }
            }
        } else {
            FxSlider(Param.ECHO_TIME)
        }
    }
}
