package com.voicefxpro

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.voicefxpro.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("voicefx", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ParamStore.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Lectura de medidores sólo mientras la app está visible (ahorra batería).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { EngineMonitor.poll(); delay(50) }
            }
        }
        setContent { VoiceFxTheme { Root() } }
    }

    // ------------------------------------------------------------------ acciones

    private fun startEngine() {
        EngineMonitor.requested = true
        ContextCompat.startForegroundService(this, Intent(this, AudioService::class.java).setAction(AudioService.ACTION_START))
    }

    private fun stopEngine() {
        EngineMonitor.requested = false
        startService(Intent(this, AudioService::class.java).setAction(AudioService.ACTION_STOP))
    }

    private fun restartIfRunning() {
        if (EngineMonitor.running || EngineMonitor.legacyState == 1)
            startService(Intent(this, AudioService::class.java).setAction(AudioService.ACTION_RESTART))
    }

    private fun applyMonitor(on: Boolean, volume: Float) {
        prefs.edit().putBoolean("monitor_enabled", on).putInt("monitor_volume", volume.toInt()).apply()
        if (NativeEngine.available && !EngineMonitor.legacy) NativeEngine.nSetMonitor(on, volume / 100f)
    }

    private fun openApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) { toast("No se pudo abrir esa app"); return }
        startActivity(intent)
    }

    private fun shareFile(file: File, pkg: String?) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            if (pkg != null) {
                send.setPackage(pkg)
                startActivity(send)
            } else startActivity(Intent.createChooser(send, "Compartir grabación"))
        } catch (_: ActivityNotFoundException) {
            // La app elegida no acepta audio compartido: mostramos el selector general.
            send.setPackage(null)
            startActivity(Intent.createChooser(send, "Compartir grabación"))
            if (pkg != null) toast("Esa app no recibe audio compartido; elegí otra forma de importarlo.")
        }
    }

    private fun installedApps(): List<InstalledVoiceApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { InstalledVoiceApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ interfaz

    @Composable
    private fun Root() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var monitorOn by remember { mutableStateOf(prefs.getBoolean("monitor_enabled", true)) }
        var monitorVol by remember { mutableFloatStateOf(prefs.getInt("monitor_volume", 85).toFloat()) }
        var inDev by remember { mutableIntStateOf(prefs.getInt("in_device", 0)) }
        var outDev by remember { mutableIntStateOf(prefs.getInt("out_device", 0)) }
        var targetPkg by remember { mutableStateOf(prefs.getString("target_package", "") ?: "") }
        var lastRec by remember { mutableStateOf(prefs.getString("last_recording", null)?.let { File(it) }?.takeIf { it.exists() }) }
        val apps = remember { installedApps() }

        val permissions = remember {
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true || hasMic()) startEngine()
            else toast("Sin permiso de micrófono no se puede activar el motor.")
        }
        val onPower: () -> Unit = {
            if (EngineMonitor.running || EngineMonitor.requested || EngineMonitor.legacyState == 1) stopEngine()
            else if (hasMic()) startEngine() else launcher.launch(permissions)
        }
        val onRecord: () -> Unit = {
            if (EngineMonitor.status[NativeEngine.S.RECORDING] == 1) {
                NativeEngine.nStopRecording()
                toast("Grabación guardada")
            } else if (!EngineMonitor.running || EngineMonitor.legacy || !NativeEngine.available) {
                toast("Activá el motor (modo Oboe) para grabar.")
            } else {
                val f = AudioService.newRecordingFile(this@MainActivity)
                val sr = EngineMonitor.status[NativeEngine.S.SAMPLE_RATE].takeIf { it > 0 } ?: 48000
                if (NativeEngine.nStartRecording(f.absolutePath, sr)) {
                    lastRec = f; prefs.edit().putString("last_recording", f.absolutePath).apply()
                } else toast("No se pudo crear el archivo de grabación")
            }
        }
        val setMonitor: (Boolean) -> Unit = { monitorOn = it; applyMonitor(it, monitorVol) }

        Column(Modifier.fillMaxSize().background(VfxColors.Screen).systemBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> StudioScreen(onPower, onRecord, setMonitor, monitorOn)
                    1 -> EffectsScreen()
                    2 -> AutoPitchScreen()
                    3 -> AppScreen(
                        apps, targetPkg, lastRec,
                        onSelect = { targetPkg = it; prefs.edit().putString("target_package", it).apply() },
                        onOpen = { openApp(it) },
                        onShareLast = { pkg -> lastRec?.let { shareFile(it, pkg) } }
                    )
                    4 -> MonitorScreen(
                        monitorOn, monitorVol, setMonitor,
                        onVolume = { monitorVol = it; applyMonitor(monitorOn, it) },
                        inDevice = inDev, outDevice = outDev,
                        onDevices = { i, o ->
                            inDev = i; outDev = o
                            prefs.edit().putInt("in_device", i).putInt("out_device", o).apply()
                            restartIfRunning()
                        },
                        onRecord = onRecord, lastRecording = lastRec, onShare = { lastRec?.let { shareFile(it, null) } }
                    )
                    else -> PresetsScreen()
                }
            }
            NavBar(tab) { tab = it }
        }
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private val tabNames = listOf("ESTUDIO", "EFECTOS", "AUTOPITCH", "APLICACIÓN", "MONITOR", "PRESETS")

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(VfxColors.Panel).padding(top = 4.dp, bottom = 2.dp)) {
        tabNames.forEachIndexed { i, name ->
            val on = i == selected
            val color = if (on) VfxColors.Cyan else VfxColors.TextDim
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).clickable { onSelect(i) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                NavIcon(i, color)
                Text(name, color = color, fontSize = 9.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun NavIcon(index: Int, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height; val sw = 2.2.dp.toPx()
        when (index) {
            0 -> { drawCircle(color, w * 0.38f, center, style = Stroke(sw)); drawCircle(color, w * 0.12f, center) }
            1 -> for (k in 0..2) { val x = w * (0.2f + 0.3f * k); drawLine(color, Offset(x, h * 0.15f), Offset(x, h * 0.85f), sw, StrokeCap.Round); drawCircle(color, w * 0.09f, Offset(x, h * (0.35f + 0.15f * ((k + 1) % 3)))) }
            2 -> { var prev = Offset(w * 0.05f, h * 0.5f); for (s in 1..16) { val x = w * (0.05f + 0.9f * s / 16f); val y = h * (0.5f - 0.32f * Math.sin(s / 16.0 * 2 * Math.PI).toFloat()); drawLine(color, prev, Offset(x, y), sw, StrokeCap.Round); prev = Offset(x, y) } }
            3 -> { drawRoundRect(color, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f), CornerRadius(5.dp.toPx()), style = Stroke(sw)); drawCircle(color, w * 0.08f, Offset(w * 0.5f, h * 0.7f)) }
            4 -> { drawArc(color, 180f, 180f, false, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f), style = Stroke(sw, cap = StrokeCap.Round)); drawRoundRect(color, Offset(w * 0.1f, h * 0.5f), Size(w * 0.18f, h * 0.35f), CornerRadius(3.dp.toPx())); drawRoundRect(color, Offset(w * 0.72f, h * 0.5f), Size(w * 0.18f, h * 0.35f), CornerRadius(3.dp.toPx())) }
            else -> for (r in 0..1) for (c in 0..1) drawRoundRect(color, Offset(w * (0.12f + 0.45f * c), h * (0.12f + 0.45f * r)), Size(w * 0.32f, h * 0.32f), CornerRadius(3.dp.toPx()), style = Stroke(sw))
        }
    }
}
