package com.voicefxpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicefxpro.ParamStore
import com.voicefxpro.Preset
import com.voicefxpro.Presets

@Composable
fun PresetsScreen() {
    var userList by remember { mutableStateOf(ParamStore.userPresets()) }
    var newName by remember { mutableStateOf("") }
    val current = ParamStore.presetName.value
    ParamStore.revision.intValue

    ScreenColumn {
        Text("PRESETS", color = VfxColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Dim(if (current.isEmpty()) "Configuración actual: personalizada" else "Configuración actual: $current")

        Panel("Guardar mi configuración", "Ajustá los efectos y guardá el resultado con un nombre. Guardar con un nombre existente lo reemplaza.") {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(32) }, singleLine = true, label = { Text("Nombre del preset") }, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VfxColors.Text, unfocusedTextColor = VfxColors.Text, cursorColor = VfxColors.Cyan,
                    focusedBorderColor = VfxColors.Violet, unfocusedBorderColor = VfxColors.Line,
                    focusedLabelColor = VfxColors.Cyan, unfocusedLabelColor = VfxColors.TextDim
                )
            )
            ActionButton("GUARDAR PRESET", Modifier.fillMaxWidth(), enabled = ParamStore.cleanName(newName).isNotEmpty()) {
                if (ParamStore.saveUserPreset(newName)) { userList = ParamStore.userPresets(); newName = "" }
            }
        }

        if (userList.isNotEmpty()) {
            Panel("Mis presets") {
                userList.forEach { up ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(up.name, current == up.name, Modifier.weight(1f)) { ParamStore.applyValues(up.values, up.name) }
                        ActionButton("BORRAR", filled = false, color = VfxColors.Red) { ParamStore.deleteUserPreset(up.name); userList = ParamStore.userPresets() }
                    }
                }
            }
        }

        val categories = Presets.all.map { it.category }.distinct()
        categories.forEach { cat ->
            Panel(cat) {
                Presets.all.filter { it.category == cat }.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { p -> PresetCard(p, current == p.name, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Panel("Valores originales") {
            Dim("Vuelve todos los controles a su valor de fábrica.")
            ActionButton("RESTABLECER TODO", Modifier.fillMaxWidth(), filled = false, color = VfxColors.Cyan) { ParamStore.resetAll() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PresetCard(p: Preset, selected: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 96.dp).clip(shape)
            .background(if (selected) VfxColors.Violet.copy(alpha = 0.25f) else VfxColors.PanelHi)
            .border(1.dp, if (selected) VfxColors.Cyan else VfxColors.Line, shape)
            .clickable { ParamStore.applyValues(p.values, p.name) }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(p.name, color = if (selected) VfxColors.Cyan else VfxColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(p.description, color = VfxColors.TextDim, fontSize = 11.sp, lineHeight = 14.sp)
    }
}
