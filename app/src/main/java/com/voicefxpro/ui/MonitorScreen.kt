package com.voicefxpro.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicefxpro.*
import java.io.File

@Composable
fun MonitorScreen(
    monitorOn: Boolean,
    monitorVolume: Float,
    onMonitor: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    inDevice: Int,
    outDevice: Int,
    onDevices: (Int, Int) -> Unit,
    onRecord: () -> Unit,
    lastRecording: File?,
    onShare: () -> Unit
) {
    val ctx: Context = LocalContext.current
    EngineMonitor.tick.intValue
    val devRev = EngineMonitor.devicesRevision.intValue
    val ins = remember(devRev) { AudioDevices.list(ctx, true) }
    val outs = remember(devRev) { AudioDevices.list(ctx, false) }
    val rec = EngineMonitor.status[NativeEngine.S.RECORDING] == 1

    ScreenColumn {
        Text("MONITOR", color = VfxColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Panel("Escuchar mi voz procesada", "Es la salida local del motor. No significa que otra app reciba esta señal como micrófono.") {
            SwitchRow("Monitoreo en tiempo real", "Apagado: el motor sigue procesando (medidores y grabación) pero no suena", monitorOn) { onMonitor(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Volumen de monitoreo", color = VfxColors.Text, fontSize = 14.sp)
                Text("${monitorVolume.toInt()}%", color = VfxColors.Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = monitorVolume, onValueChange = onVolume, valueRange = 0f..100f, enabled = monitorOn, modifier = Modifier.height(36.dp),
                colors = SliderDefaults.colors(thumbColor = VfxColors.Cyan, activeTrackColor = VfxColors.Violet, inactiveTrackColor = VfxColors.Line)
            )
            Dim("Usá auriculares con cable: con parlante el micrófono vuelve a captar la salida y se produce acople.", color = VfxColors.Amber)
        }

        Panel("Señal") {
            LevelMeter("Entrada", EngineMonitor.meter(NativeEngine.M.IN_PEAK), EngineMonitor.meter(NativeEngine.M.IN_RMS))
            Spacer(Modifier.height(4.dp))
            LevelMeter("Salida", EngineMonitor.meter(NativeEngine.M.OUT_PEAK), EngineMonitor.meter(NativeEngine.M.OUT_RMS))
            Spacer(Modifier.height(6.dp))
            InfoChips()
            if (EngineMonitor.running && EngineMonitor.inputSilent) Dim("Captura silenciada: otra app tiene prioridad de micrófono.", color = VfxColors.Amber)
            if (EngineMonitor.running && EngineMonitor.status[NativeEngine.S.XRUNS] > 0) Dim("Se detectaron cortes de audio; el búfer se agranda solo para evitarlos.", color = VfxColors.TextDim)
        }

        TunerPanel()

        Panel("Dispositivo de salida", "Auriculares, USB, altavoz… El cambio reinicia el motor automáticamente.") {
            DeviceChip("Automático (el del sistema)", outDevice == 0) { onDevices(inDevice, 0) }
            outs.forEach { d -> DeviceChip(d.label + if (d.isBluetooth) "  (latencia alta)" else "", outDevice == d.id) { onDevices(inDevice, d.id) } }
        }
        Panel("Dispositivo de entrada") {
            DeviceChip("Automático (micrófono del sistema)", inDevice == 0) { onDevices(0, outDevice) }
            ins.forEach { d -> DeviceChip(d.label + if (d.isBluetooth) "  (latencia alta)" else "", inDevice == d.id) { onDevices(d.id, outDevice) } }
        }

        Panel("Grabador", "Guarda la voz procesada en WAV (16 bits, mono) en la carpeta de la app.") {
            val secs = if (rec) NativeEngine.nRecordingSeconds() else 0.0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(if (rec) "■ DETENER  ${formatTime(secs)}" else "● GRABAR", Modifier.weight(1f), color = if (rec) VfxColors.Red else VfxColors.Violet) { onRecord() }
                ActionButton("COMPARTIR", Modifier.weight(1f), enabled = lastRecording != null && !rec, filled = false, color = VfxColors.Cyan) { onShare() }
            }
            if (lastRecording != null) Dim("Último archivo: ${lastRecording.name}")
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun formatTime(s: Double): String = String.format("%d:%02d", (s / 60).toInt(), (s % 60).toInt())

@Composable
private fun DeviceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    ChoiceChip(label, selected, Modifier.fillMaxWidth(), onClick)
}
