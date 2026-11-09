package com.voicefxpro

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

data class AudioDev(val id: Int, val label: String, val type: Int, val isUsb: Boolean, val isBluetooth: Boolean)

object AudioDevices {
    fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Micrófono del teléfono"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz del teléfono"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricular de llamada"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con micrófono (cable)"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Auriculares (cable)"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "Interfaz USB"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "Auriculares USB"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "Accesorio USB"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (llamadas)"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (música)"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Línea analógica"
        AudioDeviceInfo.TYPE_AUX_LINE -> "Línea auxiliar"
        else -> "Otro dispositivo"
    }

    fun list(context: Context, input: Boolean): List<AudioDev> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val flag = if (input) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS
        return am.getDevices(flag).map { d ->
            val name = d.productName?.toString().orEmpty()
            val base = typeName(d.type)
            val label = if (name.isNotBlank() && !base.startsWith(name)) "$base · $name" else base
            val usb = d.type == AudioDeviceInfo.TYPE_USB_DEVICE || d.type == AudioDeviceInfo.TYPE_USB_HEADSET || d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            val bt = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            AudioDev(d.id, label, d.type, usb, bt)
        }
    }

    fun usbConnected(context: Context): Boolean =
        list(context, true).any { it.isUsb } || list(context, false).any { it.isUsb }
}
