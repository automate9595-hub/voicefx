package com.voicefxpro.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicefxpro.Param
import com.voicefxpro.ParamStore
import kotlin.math.log10
import kotlin.math.roundToInt

/** Contenedor de pantalla: desplazable, centrado y con ancho máximo (teléfonos y tablets). */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun Panel(title: String? = null, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().panelStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(title, color = VfxColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = VfxColors.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        content()
    }
}

@Composable
fun Dim(text: String, modifier: Modifier = Modifier, color: Color = VfxColors.TextDim) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier)
}

fun formatValue(p: Param, v: Float): String {
    val body = when {
        p.unit == "Hz" && p.max <= 10f -> String.format("%.2f", v)
        p.unit == "Hz" -> v.roundToInt().toString()
        p.unit == "st" -> String.format("%+.1f", v)
        p.max <= 3f -> String.format("%.2f", v)
        else -> v.roundToInt().toString()
    }
    return if (p.unit.isEmpty()) body else "$body ${p.unit}"
}

/** Slider de parámetro: valor en vivo, doble toque sobre el nombre = restablecer. Todo cambio llega al motor. */
@Composable
fun FxSlider(p: Param, label: String = p.label, steps: Int = 0, hint: String? = null) {
    val rev = ParamStore.revision.intValue
    var value by remember(p, rev) { mutableFloatStateOf(ParamStore.get(p)) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, color = VfxColors.Text, fontSize = 14.sp,
                modifier = Modifier.weight(1f).pointerInput(p) { detectTapGestures(onDoubleTap = { ParamStore.resetOne(p) }) }
            )
            Text(formatValue(p, value), color = VfxColors.Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = { value = it; ParamStore.set(p, it) },
            valueRange = p.min..p.max,
            steps = steps,
            modifier = Modifier.height(36.dp).semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = VfxColors.Cyan, activeTrackColor = VfxColors.Violet, inactiveTrackColor = VfxColors.Line,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
            )
        )
        if (hint != null) Dim(hint)
    }
}

@Composable
fun FxSwitch(p: Param, title: String, subtitle: String? = null) {
    val rev = ParamStore.revision.intValue
    var on by remember(p, rev) { mutableStateOf(ParamStore.on(p)) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VfxColors.Text, fontSize = 14.sp)
            if (subtitle != null) Dim(subtitle)
        }
        Switch(
            checked = on, onCheckedChange = { on = it; ParamStore.setBool(p, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = VfxColors.Violet, checkedThumbColor = Color.White, uncheckedTrackColor = VfxColors.Line, uncheckedThumbColor = VfxColors.TextDim, uncheckedBorderColor = VfxColors.Line)
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VfxColors.Text, fontSize = 14.sp)
            if (subtitle != null) Dim(subtitle)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = VfxColors.Violet, checkedThumbColor = Color.White, uncheckedTrackColor = VfxColors.Line, uncheckedThumbColor = VfxColors.TextDim, uncheckedBorderColor = VfxColors.Line)
        )
    }
}

/** Botón de opción (chip grande) para selecciones exclusivas. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) Brush.horizontalGradient(listOf(VfxColors.Violet.copy(alpha = 0.85f), VfxColors.Blue.copy(alpha = 0.85f))) else Brush.horizontalGradient(listOf(VfxColors.PanelHi, VfxColors.PanelHi)))
            .border(1.dp, if (selected) VfxColors.Cyan.copy(alpha = 0.7f) else VfxColors.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else VfxColors.TextDim, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun ActionButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = true, color: Color = VfxColors.Violet, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (!enabled) Brush.horizontalGradient(listOf(VfxColors.PanelHi, VfxColors.PanelHi))
    else if (filled) Brush.horizontalGradient(listOf(color, VfxColors.Blue)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    Box(
        modifier.heightIn(min = 48.dp).clip(shape).background(bg)
            .border(1.dp, if (enabled) color.copy(alpha = if (filled) 0.0f else 0.8f) else VfxColors.Line, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else VfxColors.TextDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatusDot(color: Color, pulse: Boolean = false) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dotA")
    Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = if (pulse) a else 1f)))
}

/** Botón de encendido del motor: aro en degradado, brillo animado sólo mientras está activo. */
@Composable
fun PowerButton(running: Boolean, busy: Boolean, level: Float, onClick: () -> Unit, diameter: Dp = 168.dp) {
    val t = rememberInfiniteTransition(label = "power")
    val glow by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "spin")
    Box(
        Modifier.size(diameter).clip(CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = if (running) "Detener motor de audio" else "Activar motor de audio" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center; val r = this.size.minDimension / 2f
            val lvl = level.coerceIn(0f, 1f)
            if (running) {
                drawCircle(Brush.radialGradient(listOf(VfxColors.Violet.copy(alpha = 0.40f * glow + 0.25f * lvl), Color.Transparent), c, r), r)
            }
            drawCircle(Brush.radialGradient(listOf(VfxColors.PanelHi, VfxColors.Bg), c, r * 0.8f), r * 0.80f)
            val ring = r * 0.80f
            val sweep = Brush.sweepGradient(listOf(VfxColors.Violet, VfxColors.Blue, VfxColors.Cyan, VfxColors.Violet), c)
            drawCircle(if (running) sweep else Brush.linearGradient(listOf(VfxColors.Line, VfxColors.Line)), ring, style = Stroke(width = r * 0.055f))
            if (busy) drawArc(VfxColors.Cyan, spin, 90f, false, topLeft = Offset(c.x - ring, c.y - ring), size = Size(ring * 2, ring * 2), style = Stroke(width = r * 0.075f, cap = StrokeCap.Round))
            // símbolo de encendido
            val col = if (running) VfxColors.Cyan else VfxColors.TextDim
            val sr = r * 0.30f
            drawArc(col, -60f, 300f, false, topLeft = Offset(c.x - sr, c.y - sr + r * 0.02f), size = Size(sr * 2, sr * 2), style = Stroke(width = r * 0.065f, cap = StrokeCap.Round))
            drawLine(col, Offset(c.x, c.y - sr - r * 0.08f), Offset(c.x, c.y - r * 0.02f), strokeWidth = r * 0.065f, cap = StrokeCap.Round)
        }
    }
}

private fun dbFraction(peak: Float, floorDb: Float = -60f): Float {
    if (peak <= 1e-6f) return 0f
    val db = 20f * log10(peak)
    return ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
}

/** Medidor de nivel de 28 segmentos (pico) con marca de RMS. Dibuja directamente sobre Canvas. */
@Composable
fun LevelMeter(label: String, peak: Float, rms: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = VfxColors.TextDim, fontSize = 12.sp)
            val db = if (peak > 1e-5f) (20f * log10(peak)).roundToInt() else -99
            Text(if (db <= -99) "-∞ dB" else "$db dB", color = if (peak > 0.93f) VfxColors.Red else VfxColors.Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(16.dp).semantics { contentDescription = "Medidor $label" }) {
            val n = 28; val gap = 3.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
            val lit = (dbFraction(peak) * n)
            val rmsAt = (dbFraction(rms) * n).toInt()
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val base = when {
                    f > 0.90f -> VfxColors.Red
                    f > 0.75f -> VfxColors.Amber
                    else -> lerpColor(VfxColors.Violet, VfxColors.Cyan, f / 0.75f)
                }
                val on = i < lit.toInt() || (i == lit.toInt() && lit - lit.toInt() > 0.5f)
                val col = if (on) base else base.copy(alpha = 0.12f)
                drawRoundRect(col, Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(2.dp.toPx()))
                if (i == rmsAt && rms > 1e-4f) drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(i * (w + gap), size.height - 3.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, 1f)
}

/** Barra de reducción de ganancia (compresor / de-esser): crece hacia la izquierda desde 0 dB. */
@Composable
fun GainReductionBar(label: String, reductionDb: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = VfxColors.TextDim, fontSize = 12.sp)
            Text(String.format("%.1f dB", -reductionDb.coerceAtLeast(0f)), color = VfxColors.Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(VfxColors.Line, Offset.Zero, size, CornerRadius(4.dp.toPx()))
            val f = (reductionDb.coerceIn(0f, 18f) / 18f)
            drawRoundRect(Brush.horizontalGradient(listOf(VfxColors.Violet, VfxColors.Blue)), Offset(size.width * (1 - f), 0f), Size(size.width * f, size.height), CornerRadius(4.dp.toPx()))
        }
    }
}

val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
fun noteName(midi: Int): String = NOTE_NAMES[((midi % 12) + 12) % 12] + (midi / 12 - 1)
