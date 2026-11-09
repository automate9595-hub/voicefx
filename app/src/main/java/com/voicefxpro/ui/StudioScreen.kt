package com.voicefxpro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicefxpro.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    onPower: () -> Unit,
    onRecord: () -> Unit,
    onMonitor: (Boolean) -> Unit,
    monitorOn: Boolean
) {
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VOICE FX PRO", color = VfxColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                val name = ParamStore.presetName.value
                Dim(if (name.isEmpty()) "Preset: personalizado" else "Preset: $name")
            }
            EngineBadge()
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 640.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { PowerPanel(onPower); MetersPanel() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { TunerPanel(); QuickPanel(onRecord, onMonitor, monitorOn); MasterPanel() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PowerPanel(onPower); MetersPanel(); TunerPanel(); QuickPanel(onRecord, onMonitor, monitorOn); MasterPanel()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EngineBadge() {
    EngineMonitor.tick.intValue
    val (label, color) = when {
        EngineMonitor.restarting -> "RECONECTANDO" to VfxColors.Amber
        EngineMonitor.state == 2 || EngineMonitor.legacyState == 2 -> "ERROR" to VfxColors.Red
        EngineMonitor.running && EngineMonitor.inputSilent -> "SILENCIADO" to VfxColors.Amber
        EngineMonitor.running && EngineMonitor.legacy -> "RESPALDO" to VfxColors.Amber
        EngineMonitor.running -> "EN VIVO" to VfxColors.Mint
        else -> "APAGADO" to VfxColors.TextDim
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(color, pulse = EngineMonitor.running)
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PowerPanel(onPower: () -> Unit) {
    EngineMonitor.tick.intValue
    val running = EngineMonitor.running
    if (running) EngineMonitor.requested = false
    val busy = EngineMonitor.restarting || (EngineMonitor.requested && !running)
    Column(Modifier.fillMaxWidth().panelStyle().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PowerButton(running, busy, EngineMonitor.meter(NativeEngine.M.IN_PEAK), onPower)
        Text(
            when {
                busy -> "INICIANDO MOTOR…"
                running -> "MOTOR ACTIVO"
                EngineMonitor.state == 2 -> "ERROR DE AUDIO"
                else -> "TOCÁ PARA ACTIVAR"
            },
            color = if (running) VfxColors.Cyan else VfxColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp
        )
        Dim(EngineMonitor.message, Modifier.fillMaxWidth(), VfxColors.TextDim)
        if (running && EngineMonitor.inputSilent)
            Text("Android silenció el micrófono: otra app tiene la captura. Mirá la solapa APLICACIÓN.", color = VfxColors.Amber, fontSize = 12.sp, textAlign = TextAlign.Center)
        Dim("Usá auriculares con cable para evitar acople (larsen).", color = VfxColors.Amber)
    }
}

@Composable
private fun MetersPanel() {
    EngineMonitor.tick.intValue
    val m = EngineMonitor
    Panel("Medidores") {
        LevelMeter("Entrada", m.meter(NativeEngine.M.IN_PEAK), m.meter(NativeEngine.M.IN_RMS))
        Spacer(Modifier.height(4.dp))
        LevelMeter("Salida", m.meter(NativeEngine.M.OUT_PEAK), m.meter(NativeEngine.M.OUT_RMS))
        Spacer(Modifier.height(6.dp))
        GainReductionBar("Compresor (reducción)", m.meter(NativeEngine.M.COMP_GR))
        Spacer(Modifier.height(4.dp))
        GainReductionBar("De-esser (reducción)", -m.meter(NativeEngine.M.DEESS_GR))
        Spacer(Modifier.height(6.dp))
        InfoChips()
    }
}

@Composable
fun InfoChips() {
    EngineMonitor.tick.intValue
    val s = EngineMonitor.status
    val lat = EngineMonitor.meter(NativeEngine.M.LATENCY_MS)
    val outUs = s[NativeEngine.S.LATENCY_OUT_US]
    val parts = ArrayList<String>()
    if (!EngineMonitor.legacy) {
        parts.add(if (s[NativeEngine.S.API] == 1) "AAudio" else "OpenSL")
        if (s[NativeEngine.S.SAMPLE_RATE] > 0) parts.add("${s[NativeEngine.S.SAMPLE_RATE] / 1000f} kHz")
        if (outUs > 0) parts.add("salida ${(outUs / 1000f).roundToInt()} ms")
        if (lat > 0f) parts.add("tono +${lat.roundToInt()} ms")
        parts.add("CPU ${EngineMonitor.meter(NativeEngine.M.CPU).roundToInt()}%")
        parts.add("cortes ${s[NativeEngine.S.XRUNS]}")
    } else parts.add("motor de respaldo")
    Text(parts.joinToString("  ·  "), color = VfxColors.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
}

@Composable
fun TunerPanel() {
    EngineMonitor.tick.intValue
    val voiced = EngineMonitor.meter(NativeEngine.M.VOICED) > 0.5f && EngineMonitor.running
    val meas = EngineMonitor.meter(NativeEngine.M.MEAS_MIDI)
    val target = EngineMonitor.meter(NativeEngine.M.TARGET_NOTE)
    val outM = EngineMonitor.meter(NativeEngine.M.OUT_MIDI)
    val cents = EngineMonitor.meter(NativeEngine.M.CENTS)
    val atOn = ParamStore.on(Param.AT_ON)
    Panel("Afinación en vivo") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                Text(if (voiced) noteName(meas.roundToInt()) else "—", color = if (voiced) VfxColors.Cyan else VfxColors.TextDim, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Dim(if (voiced) "${EngineMonitor.meter(NativeEngine.M.F0).roundToInt()} Hz" else "sin tono estable")
            }
            Column(Modifier.weight(1f)) {
                CentsBar(if (voiced) cents else 0f, voiced)
                Spacer(Modifier.height(6.dp))
                if (voiced) {
                    val shift = outM - meas
                    Dim("Desvío ${String.format("%+.0f", cents)} cents respecto a ${noteName(target.roundToInt())}")
                    Dim(if (atOn) "Salida: ${noteName(outM.roundToInt())} (corrección ${String.format("%+.0f", shift * 100)} cents)" else "Auto-Tune apagado")
                } else Dim(if (atOn) "Auto-Tune activo: esperando voz…" else "Cantá o hablá para ver la nota")
            }
        }
    }
}

@Composable
fun CentsBar(cents: Float, active: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val h = size.height; val w = size.width
        drawRoundRect(VfxColors.Line, Offset(0f, h * 0.35f), Size(w, h * 0.30f), CornerRadius(h))
        val tolW = w * 0.10f
        drawRoundRect(VfxColors.Mint.copy(alpha = 0.35f), Offset(w / 2 - tolW / 2, h * 0.25f), Size(tolW, h * 0.5f), CornerRadius(4.dp.toPx()))
        for (i in -5..5) {
            val x = w / 2 + (i / 5f) * (w / 2 - 6.dp.toPx())
            drawLine(VfxColors.TextDim.copy(alpha = if (i == 0) 0.9f else 0.35f), Offset(x, h * 0.12f), Offset(x, h * 0.28f), strokeWidth = 1.5.dp.toPx())
        }
        if (active) {
            val c = cents.coerceIn(-50f, 50f) / 50f
            val x = w / 2 + c * (w / 2 - 6.dp.toPx())
            val col = if (abs(cents) < 8f) VfxColors.Mint else if (abs(cents) < 25f) VfxColors.Cyan else VfxColors.Amber
            drawCircle(col, 8.dp.toPx(), Offset(x, h / 2))
            drawCircle(Color.White.copy(alpha = 0.9f), 3.dp.toPx(), Offset(x, h / 2))
        }
    }
}

@Composable
private fun QuickPanel(onRecord: () -> Unit, onMonitor: (Boolean) -> Unit, monitorOn: Boolean) {
    EngineMonitor.tick.intValue
    val rev = ParamStore.revision.intValue
    var bypass by remember(rev) { mutableStateOf(ParamStore.on(Param.BYPASS)) }
    var at by remember(rev) { mutableStateOf(ParamStore.on(Param.AT_ON)) }
    val rec = EngineMonitor.status[NativeEngine.S.RECORDING] == 1
    Panel("Control rápido") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(if (bypass) "VOZ SECA (A)" else "PROCESADA (B)", bypass, Modifier.weight(1f)) { bypass = !bypass; ParamStore.setBool(Param.BYPASS, bypass) }
            ChoiceChip("AUTO-TUNE", at, Modifier.weight(1f)) { at = !at; ParamStore.setBool(Param.AT_ON, at) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(if (monitorOn) "MONITOR ON" else "MONITOR OFF", monitorOn, Modifier.weight(1f)) { onMonitor(!monitorOn) }
            ChoiceChip(if (rec) "● GRABANDO" else "GRABAR", rec, Modifier.weight(1f)) { onRecord() }
        }
        Dim("Compará tu voz seca (A) contra la procesada (B) sin apagar el motor.")
    }
}

@Composable
private fun MasterPanel() {
    Panel("Nivel general") {
        FxSlider(Param.IN_GAIN, "Ganancia de entrada")
        FxSlider(Param.MIX, "Mezcla procesada (seca ↔ efectos)")
        FxSlider(Param.OUT_GAIN, "Volumen de salida")
    }
}
