package com.voicefxpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta de estudio: fondo carbón azulado, violeta eléctrico, azul eléctrico y cyan. */
object VfxColors {
    val Bg = Color(0xFF06060D)
    val BgTop = Color(0xFF14102B)
    val Panel = Color(0xFF11111E)
    val PanelHi = Color(0xFF191930)
    val Line = Color(0xFF2A2A48)
    val Violet = Color(0xFF9B6BFF)
    val Blue = Color(0xFF3D7BFF)
    val Cyan = Color(0xFF25E1F5)
    val Mint = Color(0xFF4DF0B0)
    val Amber = Color(0xFFFFC857)
    val Red = Color(0xFFFF5C7A)
    val Text = Color(0xFFF1F0FF)
    val TextDim = Color(0xFF9A98BC)
    val Accent = Brush.horizontalGradient(listOf(Violet, Blue, Cyan))
    val Screen = Brush.verticalGradient(listOf(BgTop, Bg, Bg))
}

@Composable
fun VoiceFxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VfxColors.Violet, secondary = VfxColors.Cyan, tertiary = VfxColors.Blue,
            background = VfxColors.Bg, surface = VfxColors.Panel, onSurface = VfxColors.Text, onBackground = VfxColors.Text,
            surfaceVariant = VfxColors.PanelHi, outline = VfxColors.Line
        ),
        content = content
    )
}

/** Tarjeta con borde en degradado violeta→cyan muy tenue. */
fun Modifier.panelStyle(): Modifier {
    val shape = RoundedCornerShape(20.dp)
    return this
        .clip(shape)
        .background(VfxColors.Panel)
        .border(1.dp, Brush.linearGradient(listOf(VfxColors.Violet.copy(alpha = 0.45f), VfxColors.Line, VfxColors.Cyan.copy(alpha = 0.35f))), shape)
}
