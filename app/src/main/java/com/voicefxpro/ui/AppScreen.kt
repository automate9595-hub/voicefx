package com.voicefxpro.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicefxpro.AudioDevices
import com.voicefxpro.EngineMonitor
import com.voicefxpro.NativeEngine
import java.io.File

data class InstalledVoiceApp(val label: String, val packageName: String)

private val knownMusicApps = listOf("bandlab", "smule", "starmaker")

@Composable
fun AppScreen(
    apps: List<InstalledVoiceApp>,
    selectedPackage: String,
    lastRecording: File?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onShareLast: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = apps.firstOrNull { it.packageName == selectedPackage }
    val music = apps.filter { a -> knownMusicApps.any { a.packageName.lowercase().contains(it) || a.label.lowercase().contains(it) } }
    val filtered = if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("APLICACIÓN", color = VfxColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            item {
                Panel("Aplicación para abrir o importar audio", selected?.let { "Seleccionada: ${it.label}" } ?: "Elegí una app para abrirla o intentar importar una grabación procesada.") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("ABRIR APP", Modifier.weight(1f), enabled = selected != null) { onOpen(selectedPackage) }
                        ActionButton(
                            "COMPARTIR GRABACIÓN", Modifier.weight(1f), enabled = selected != null && lastRecording != null, filled = false, color = VfxColors.Cyan
                        ) { onShareLast(selectedPackage) }
                    }
                    if (lastRecording == null) Dim("Todavía no hay grabaciones: usá GRABAR en ESTUDIO o MONITOR.")
                    else Dim("Última grabación: ${lastRecording.name}")
                    Dim("Importante: elegir o abrir una app NO cambia su micrófono. Android no ofrece a esta app un micrófono virtual universal sin privilegios de sistema.")
                }
            }
            item { CompatibilityPanel(selected) }
            item { RoutesPanel() }
            if (music.isNotEmpty()) {
                item {
                    Panel("Apps de música detectadas") {
                        music.forEach { app -> AppRow(app, app.packageName == selectedPackage) { onSelect(app.packageName) } }
                    }
                }
            } else {
                item { Panel("Apps de música") { Dim("No se detectó BandLab, Smule ni StarMaker instaladas. Podés elegir cualquier otra de la lista de abajo.") } }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Buscar entre ${apps.size} aplicaciones") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VfxColors.Text, unfocusedTextColor = VfxColors.Text, cursorColor = VfxColors.Cyan,
                        focusedBorderColor = VfxColors.Violet, unfocusedBorderColor = VfxColors.Line,
                        focusedLabelColor = VfxColors.Cyan, unfocusedLabelColor = VfxColors.TextDim
                    )
                )
            }
            items(filtered, key = { it.packageName }) { app -> AppRow(app, app.packageName == selectedPackage) { onSelect(app.packageName) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AppRow(app: InstalledVoiceApp, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
            .background(if (selected) VfxColors.Violet.copy(alpha = 0.22f) else VfxColors.PanelHi)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(app.label, color = VfxColors.Text, fontSize = 14.sp, maxLines = 1)
            Text(app.packageName, color = VfxColors.TextDim, fontSize = 10.sp, maxLines = 1)
        }
        if (selected) Text("ELEGIDA", color = VfxColors.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusLine(ok: Boolean?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(when (ok) { true -> VfxColors.Mint; false -> VfxColors.Amber; null -> VfxColors.TextDim })
        Text(text, color = VfxColors.Text, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/** Estado real, medido en este teléfono: no simula ninguna conexión. */
@Composable
private fun CompatibilityPanel(selected: InstalledVoiceApp?) {
    val ctx: Context = LocalContext.current
    EngineMonitor.tick.intValue
    val devRev = EngineMonitor.devicesRevision.intValue
    val usb = remember(devRev) { AudioDevices.usbConnected(ctx) }
    val running = EngineMonitor.running
    Panel("Estado de compatibilidad (medido ahora)") {
        StatusLine(running, if (running) "Procesador de VOICE FX PRO activo" else "Procesador apagado")
        StatusLine(selected != null, selected?.let { "App de destino: ${it.label}" } ?: "App de destino: sin elegir")
        StatusLine(
            if (!running) null else !EngineMonitor.inputSilent,
            if (!running) "Captura: procesador apagado" else if (EngineMonitor.inputSilent) "Captura SILENCIADA por Android (otra app tiene prioridad)" else "Captura propia: activa; esto no confirma que otra app reciba esta señal"
        )
        StatusLine(
            if (EngineMonitor.otherRecorders > 0) false else null,
            if (android.os.Build.VERSION.SDK_INT >= 29) "Otras sesiones de micrófono detectadas: ${EngineMonitor.otherRecorders}" else "Detección de otras sesiones: requiere Android 10 o superior"
        )
        StatusLine(if (usb) true else null, if (usb) "Interfaz de audio USB conectada" else "Sin interfaz USB conectada")
        if (!NativeEngine.available) StatusLine(false, "Motor nativo no disponible: se usa el motor de respaldo")
        StatusLine(null, "Micrófono virtual del sistema: Android estándar sin ROOT no permite crearlo desde esta app")
    }
}

@Composable
private fun RoutesPanel() {
    Panel(
        "Rutas reales hacia otras aplicaciones",
        "La selección de una app solo permite abrirla o compartirle un archivo. No redirige su micrófono. Android estándar no ofrece una API pública para que una app común reemplace la entrada de micrófono de todas las demás."
    ) {
        Text("1 · Grabar aquí e importar (audio procesado, no en vivo)", color = VfxColors.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Dim("Grabá con VOICE FX PRO y usá COMPARTIR GRABACIÓN. Si la app admite archivos de audio, podrás importarlo como pista. Esto entrega un archivo terminado; no reemplaza el micrófono en vivo.")
        Spacer(Modifier.height(4.dp))
        Text("2 · Interfaz USB / mezclador con loopback (tiempo real, con hardware)", color = VfxColors.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Dim("Conectá una interfaz USB o mezclador que admita retorno/loopback. En MONITOR elegí la salida compatible y configurá el retorno físico o interno como entrada de la app de karaoke. La compatibilidad depende de la interfaz, el teléfono y la app; no es una sustitución de micrófono por software.")
        Spacer(Modifier.height(4.dp))
        Text("3 · Qué NO se puede hacer", color = VfxColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Dim("Si la app de destino toma prioridad, Android puede silenciar nuestra captura. La app detecta ese estado cuando el sistema lo informa, pero no puede anularlo ni inyectar audio en la entrada privada de otra aplicación. Por eso no se promete micrófono virtual universal en teléfonos sin ROOT.")
    }
}
