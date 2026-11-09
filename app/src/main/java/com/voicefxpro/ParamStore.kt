package com.voicefxpro

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf

data class UserPreset(val name: String, val values: Map<Param, Float>)

/** Valores actuales de todos los parámetros: persistencia, envío al motor y presets del usuario. */
object ParamStore {
    val values = FloatArray(Param.values().size) { Param.values()[it].def }

    /** Se incrementa cuando los valores cambian "desde afuera" (preset, restaurar) para refrescar los controles. */
    val revision = mutableIntStateOf(0)
    val presetName = androidx.compose.runtime.mutableStateOf("")

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("voicefx", Context.MODE_PRIVATE)
        prefs = p
        for (param in Param.values()) {
            values[param.ordinal] = try {
                p.getFloat("p." + param.key, param.def)
            } catch (_: ClassCastException) {
                param.def
            }.coerceIn(param.min, param.max)
        }
        migrateOldKeys(p)
        values[Param.BYPASS.ordinal] = 0f
        presetName.value = p.getString("preset_name", "") ?: ""
    }

    /** Versiones 0.2.x guardaban enteros 0-100 con claves sin prefijo. */
    private fun migrateOldKeys(p: SharedPreferences) {
        if (p.getBoolean("p.migrated", false)) return
        val ed = p.edit()
        val map = mapOf(
            "reverb" to Param.REVERB, "echo" to Param.ECHO, "chorus" to Param.CHORUS, "compression" to Param.COMPRESSION,
            "drive" to Param.DRIVE, "bass" to Param.BASS, "treble" to Param.TREBLE, "gate" to Param.GATE, "wet" to Param.MIX
        )
        for ((old, param) in map) {
            val v = try {
                if (p.contains(old)) p.getInt(old, -1) else -1
            } catch (_: ClassCastException) {
                -1
            }
            if (v in 0..100) {
                values[param.ordinal] = v.toFloat().coerceIn(param.min, param.max)
                ed.putFloat("p." + param.key, values[param.ordinal])
            }
        }
        ed.putBoolean("p.migrated", true).apply()
    }

    fun get(p: Param): Float = values[p.ordinal]
    fun on(p: Param): Boolean = values[p.ordinal] > 0.5f

    /** Cambio puntual (un control de la interfaz). */
    fun set(p: Param, v: Float, persist: Boolean = true) {
        val c = v.coerceIn(p.min, p.max)
        values[p.ordinal] = c
        NativeEngine.setParam(p, c)
        if (persist && p != Param.BYPASS) prefs?.edit()?.putFloat("p." + p.key, c)?.apply()
    }

    fun setBool(p: Param, on: Boolean) = set(p, if (on) 1f else 0f)

    fun pushAllToNative() {
        for (p in Param.values()) NativeEngine.setParam(p, values[p.ordinal])
    }

    /** Aplica un conjunto de valores: todo lo no indicado vuelve al valor por defecto. */
    fun applyValues(map: Map<Param, Float>, name: String) {
        val ed = prefs?.edit()
        for (p in Param.values()) {
            if (p == Param.BYPASS) continue
            val v = (map[p] ?: p.def).coerceIn(p.min, p.max)
            values[p.ordinal] = v
            NativeEngine.setParam(p, v)
            ed?.putFloat("p." + p.key, v)
        }
        presetName.value = name
        ed?.putString("preset_name", name)?.apply()
        revision.intValue++
    }

    fun resetAll() = applyValues(emptyMap(), "")

    fun resetOne(p: Param) {
        set(p, p.def)
        revision.intValue++
    }

    fun snapshot(): Map<Param, Float> =
        Param.values().filter { it != Param.BYPASS }.associateWith { values[it.ordinal] }

    // ---------- Presets del usuario ----------
    fun userPresets(): List<UserPreset> {
        val raw = prefs?.getString("user_presets", "") ?: ""
        val out = ArrayList<UserPreset>()
        for (line in raw.split("\n")) {
            val bar = line.indexOf('|')
            if (bar <= 0) continue
            val map = HashMap<Param, Float>()
            for (kv in line.substring(bar + 1).split(",")) {
                val eq = kv.indexOf('=')
                if (eq <= 0) continue
                val param = Param.values().firstOrNull { it.key == kv.substring(0, eq) } ?: continue
                val f = kv.substring(eq + 1).toFloatOrNull() ?: continue
                map[param] = f
            }
            out.add(UserPreset(line.substring(0, bar), map))
        }
        return out
    }

    private fun writeUserPresets(list: List<UserPreset>) {
        val text = list.joinToString("\n") { up ->
            up.name + "|" + up.values.entries.joinToString(",") { it.key.key + "=" + it.value }
        }
        prefs?.edit()?.putString("user_presets", text)?.apply()
    }

    fun cleanName(raw: String): String = raw.replace(Regex("[|,=\\n\\r]"), " ").trim().take(32)

    fun saveUserPreset(rawName: String): Boolean {
        val name = cleanName(rawName)
        if (name.isEmpty()) return false
        val list = userPresets().filter { it.name != name }.toMutableList()
        list.add(UserPreset(name, snapshot()))
        writeUserPresets(list)
        presetName.value = name
        prefs?.edit()?.putString("preset_name", name)?.apply()
        return true
    }

    fun deleteUserPreset(name: String) {
        writeUserPresets(userPresets().filter { it.name != name })
    }
}
