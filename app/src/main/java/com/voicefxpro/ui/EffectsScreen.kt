package com.voicefxpro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicefxpro.Param
import com.voicefxpro.ParamStore

private data class EffectTileSpec(
    val title: String,
    val active: Boolean,
    val toggle: () -> Unit,
    val editor: @Composable ColumnScope.() -> Unit
)

@Composable
fun EffectsScreen() {
    val revision = ParamStore.revision.intValue
    var selected by remember { mutableStateOf<String?>(null) }


    val tiles = listOf(
        EffectTileSpec("AUTOTUNE", ParamStore.on(Param.AT_ON),
            { ParamStore.setBool(Param.AT_ON, !ParamStore.on(Param.AT_ON)) }) {
            FxSlider(Param.AT_SPEED); FxSlider(Param.AT_HUMANIZE); FxSlider(Param.AT_AMOUNT); FxSlider(Param.AT_FLEX)
            Dim("La tonalidad y la escala se configuran en la pestaña AUTOPITCH.")
        },
        EffectTileSpec("REVERB", ParamStore.get(Param.REVERB) > 0f,
            { ParamStore.toggleEffect(Param.REVERB, 0f, 18f) }) {
            FxSlider(Param.REVERB); FxSlider(Param.REVERB_SIZE); FxSlider(Param.REVERB_DAMP); FxSlider(Param.REVERB_PRE)
        },
        EffectTileSpec("ECHO", ParamStore.get(Param.ECHO) > 0f,
            { ParamStore.toggleEffect(Param.ECHO, 0f, 35f) }) {
            FxSlider(Param.ECHO); FxSlider(Param.ECHO_TIME); FxSlider(Param.ECHO_FB); FxSlider(Param.BPM)
        },
        EffectTileSpec("PITCH", ParamStore.get(Param.PITCH_SEMI) != 0f,
            { ParamStore.toggleEffect(Param.PITCH_SEMI, 0f, 3f) }) {
            FxSlider(Param.PITCH_SEMI); FxSlider(Param.FORMANT_SEMI)
        },
        EffectTileSpec("ROBOT", ParamStore.get(Param.ROBOT_MONO) > 0f || ParamStore.get(Param.ROBOT_METAL) > 0f,
            { if (ParamStore.get(Param.ROBOT_MONO) > 0f || ParamStore.get(Param.ROBOT_METAL) > 0f) { if (ParamStore.get(Param.ROBOT_MONO) > 0f) ParamStore.toggleEffect(Param.ROBOT_MONO, 0f, 60f); if (ParamStore.get(Param.ROBOT_METAL) > 0f) ParamStore.toggleEffect(Param.ROBOT_METAL, 0f, 40f) } else ParamStore.toggleEffect(Param.ROBOT_MONO, 0f, 60f) }) {
            FxSlider(Param.ROBOT_MONO); FxSlider(Param.ROBOT_METAL)
        },
        EffectTileSpec("ARMONÍAS", listOf(Param.HARM_3, Param.HARM_5, Param.HARM_OCT_UP, Param.HARM_OCT_DN).any { ParamStore.get(it) > 0f },
            { val ps = listOf(Param.HARM_3, Param.HARM_5, Param.HARM_OCT_UP, Param.HARM_OCT_DN); if (ps.any { ParamStore.get(it) > 0f }) ps.filter { ParamStore.get(it) > 0f }.forEach { ParamStore.toggleEffect(it, 0f, 35f) } else ParamStore.toggleEffect(Param.HARM_3, 0f, 35f) }) {
            FxSlider(Param.HARM_3); FxSlider(Param.HARM_5); FxSlider(Param.HARM_OCT_UP); FxSlider(Param.HARM_OCT_DN)
        },
        EffectTileSpec("COMPRESOR", ParamStore.get(Param.COMPRESSION) > 0f,
            { ParamStore.toggleEffect(Param.COMPRESSION, 0f, 25f) }) { FxSlider(Param.COMPRESSION) },
        EffectTileSpec("GATE", ParamStore.get(Param.GATE) > 0f,
            { ParamStore.toggleEffect(Param.GATE, 0f, 40f) }) { FxSlider(Param.GATE) },
        EffectTileSpec("DE-ESSER", ParamStore.get(Param.DEESS) > 0f,
            { ParamStore.toggleEffect(Param.DEESS, 0f, 40f) }) { FxSlider(Param.DEESS); FxSlider(Param.DEESS_FREQ) },
        EffectTileSpec("CHORUS", ParamStore.get(Param.CHORUS) > 0f,
            { ParamStore.toggleEffect(Param.CHORUS, 0f, 35f) }) { FxSlider(Param.CHORUS); FxSlider(Param.CHORUS_RATE) },
        EffectTileSpec("FLANGER", ParamStore.get(Param.FLANGER) > 0f,
            { ParamStore.toggleEffect(Param.FLANGER, 0f, 35f) }) { FxSlider(Param.FLANGER); FxSlider(Param.FLANGER_RATE); FxSlider(Param.FLANGER_FB) },
        EffectTileSpec("PHASER", ParamStore.get(Param.PHASER) > 0f,
            { ParamStore.toggleEffect(Param.PHASER, 0f, 35f) }) { FxSlider(Param.PHASER); FxSlider(Param.PHASER_RATE) },
        EffectTileSpec("VIBRATO", ParamStore.get(Param.VIBRATO) > 0f,
            { ParamStore.toggleEffect(Param.VIBRATO, 0f, 35f) }) { FxSlider(Param.VIBRATO); FxSlider(Param.VIBRATO_RATE) },
        EffectTileSpec("DOUBLER", ParamStore.get(Param.DOUBLER) > 0f,
            { ParamStore.toggleEffect(Param.DOUBLER, 0f, 35f) }) { FxSlider(Param.DOUBLER); FxSlider(Param.DOUBLER_DETUNE) },
        EffectTileSpec("DRIVE", ParamStore.get(Param.DRIVE) > 0f,
            { ParamStore.toggleEffect(Param.DRIVE, 0f, 30f) }) { FxSlider(Param.DRIVE) },
        EffectTileSpec("TELEPHONE", ParamStore.get(Param.PHONE) > 0f,
            { ParamStore.toggleEffect(Param.PHONE, 0f, 100f) }) { FxSlider(Param.PHONE) }
    )

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("EFECTOS", color = VfxColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Dim("Toque: activar. Mantener presionado: editar.")
            }
            ActionButton("RESTABLECER", filled = false, color = VfxColors.Cyan) { ParamStore.resetAll() }
        }

        tiles.chunked(3).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowTiles.forEach { tile ->
                    EffectTile(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        onTap = { tile.toggle(); ParamStore.revision.intValue++ },
                        onLongPress = { selected = tile.title }
                    )
                }
                repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Dim("Los efectos encendidos se procesan en VOICE FX PRO. Esto no sustituye por sí solo el micrófono que StarMaker captura.")
        Spacer(Modifier.height(8.dp))
    }

    val editor = tiles.firstOrNull { it.title == selected }
    if (editor != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Editar ${editor.title}", color = VfxColors.Text, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { editor.editor() }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("LISTO") } },
            containerColor = VfxColors.Panel
        )
    }
    revision
}

@Composable
private fun EffectTile(tile: EffectTileSpec, modifier: Modifier, onTap: () -> Unit, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val color = if (tile.active) VfxColors.Mint else VfxColors.Line
    Surface(
        modifier = modifier.height(104.dp).pointerInput(tile.title, tile.active) {
            detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
        },
        shape = shape,
        color = if (tile.active) VfxColors.Violet.copy(alpha = 0.24f) else VfxColors.Panel,
        border = BorderStroke(1.dp, color.copy(alpha = if (tile.active) 0.95f else 0.65f)),
        tonalElevation = if (tile.active) 3.dp else 0.dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (tile.active) "●" else "○", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(tile.title, color = VfxColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(if (tile.active) "ACTIVO" else "APAGADO", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
