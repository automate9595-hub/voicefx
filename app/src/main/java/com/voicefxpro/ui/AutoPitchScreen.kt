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
import com.voicefxpro.Theory

@Composable
fun AutoPitchScreen() {
    val rev = ParamStore.revision.intValue
    var on by remember(rev) { mutableStateOf(ParamStore.on(Param.AT_ON)) }
    var manual by remember(rev) { mutableStateOf(ParamStore.get(Param.AT_MODE) > 0.5f) }
    var key by remember(rev) { mutableIntStateOf(ParamStore.get(Param.AT_KEY).toInt()) }
    var scale by remember(rev) { mutableIntStateOf(ParamStore.get(Param.AT_SCALE).toInt()) }
    var mask by remember(rev) { mutableIntStateOf(ParamStore.get(Param.AT_MASK).toInt()) }
    val activeMask = if (manual) mask else Theory.scaleMask(key, scale)

    ScreenColumn {
        Text("AUTOPITCH", color = VfxColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Panel("Corrección de afinación", "Detecta tu nota (YIN) y la lleva a la nota correcta de la escala con PSOLA, conservando el timbre.") {
            SwitchRow("Auto-Tune activado", "Si está apagado sólo se muestra la nota detectada", on) { on = it; ParamStore.setBool(Param.AT_ON, it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("AUTOMÁTICO\ntonalidad + escala", !manual, Modifier.weight(1f)) { manual = false; ParamStore.set(Param.AT_MODE, 0f) }
                ChoiceChip("MANUAL\nelijo las notas", manual, Modifier.weight(1f)) {
                    if (!manual) { mask = Theory.scaleMask(key, scale); ParamStore.set(Param.AT_MASK, mask.toFloat()) }
                    manual = true; ParamStore.set(Param.AT_MODE, 1f)
                }
            }
        }

        TunerPanel()

        Panel("Tonalidad", "Nota raíz. También es el tono que usa el modo Robot monótono y las armonías.") {
            for (row in 0..1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in row * 6 until row * 6 + 6) {
                        ChoiceChip(NOTE_NAMES[i], key == i, Modifier.weight(1f)) {
                            key = i; ParamStore.set(Param.AT_KEY, i.toFloat())
                        }
                    }
                }
            }
        }

        Panel("Escala") {
            val names = Theory.scaleNames
            for (start in names.indices step 3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in start until minOf(start + 3, names.size)) {
                        ChoiceChip(names[i], scale == i && !manual, Modifier.weight(1f)) {
                            scale = i; ParamStore.set(Param.AT_SCALE, i.toFloat())
                            manual = false; ParamStore.set(Param.AT_MODE, 0f)
                        }
                    }
                    for (pad in 0 until (start + 3 - names.size).coerceAtLeast(0)) Spacer(Modifier.weight(1f))
                }
            }
        }

        Panel("Notas permitidas", if (manual) "Modo manual: tocá una nota para permitirla o quitarla." else "Muestra las notas de la escala. Tocar una nota pasa a modo manual con esta base.") {
            for (row in 0..1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in row * 6 until row * 6 + 6) {
                        val allowed = (activeMask shr i) and 1 == 1
                        ChoiceChip(NOTE_NAMES[i], allowed, Modifier.weight(1f)) {
                            val base = if (manual) mask else activeMask
                            var m = base xor (1 shl i)
                            if (m == 0) m = base            // al menos una nota
                            mask = m; manual = true
                            ParamStore.set(Param.AT_MASK, m.toFloat()); ParamStore.set(Param.AT_MODE, 1f)
                        }
                    }
                }
            }
        }

        Panel("Carácter de la corrección") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StyleButton("Natural", Modifier.weight(1f), 40f, 50f, 100f, 6f)
                StyleButton("Sutil", Modifier.weight(1f), 120f, 80f, 70f, 10f)
                StyleButton("Pop", Modifier.weight(1f), 55f, 60f, 90f, 8f)
                StyleButton("Duro", Modifier.weight(1f), 0f, 0f, 100f, 0f)
            }
            FxSlider(Param.AT_SPEED, "Retune Speed", hint = "0 ms = salto inmediato (efecto duro). 100-300 ms = muy natural.")
            FxSlider(Param.AT_HUMANIZE, "Humanize", hint = "Alto = conserva tu vibrato y los matices; bajo = tono plano.")
            FxSlider(Param.AT_AMOUNT, "Intensidad", hint = "Cuánto de la corrección se aplica. 100 % = nota exacta.")
            FxSlider(Param.AT_FLEX, "Tolerancia", hint = "Desvíos pequeños se dejan sin corregir para sonar humano.")
            FxSlider(Param.AT_A4, "Referencia A4")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StyleButton(name: String, modifier: Modifier, speed: Float, hum: Float, amount: Float, flex: Float) {
    ChoiceChip(name, false, modifier) {
        ParamStore.set(Param.AT_ON, 1f)
        ParamStore.set(Param.AT_SPEED, speed); ParamStore.set(Param.AT_HUMANIZE, hum)
        ParamStore.set(Param.AT_AMOUNT, amount); ParamStore.set(Param.AT_FLEX, flex)
        ParamStore.revision.intValue++
    }
}
