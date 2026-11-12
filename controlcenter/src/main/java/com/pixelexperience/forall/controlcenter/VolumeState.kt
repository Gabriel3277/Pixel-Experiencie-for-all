package com.pixelexperience.forall.controlcenter

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

internal enum class VolStream(val type: Int) {
    MEDIA(AudioManager.STREAM_MUSIC),
    CALL(AudioManager.STREAM_VOICE_CALL),
    RING(AudioManager.STREAM_RING),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION),
    ALARM(AudioManager.STREAM_ALARM),
}

/** Estado real dos volumes e do modo de toque. Tudo vem do AudioManager. */
internal class VolumeState(private val context: Context) {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val notifications: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    private val raw = mutableStateMapOf<VolStream, Float>()

    var ringerMode by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)
        private set

    /** Nome da saída de áudio (fone/Bluetooth) ou null quando é o próprio telefone. */
    var outputName by mutableStateOf<String?>(null)
        private set

    /** Sem esta permissão ("Não perturbe") o Android não deixa usar o modo mudo total. */
    val canSilence: Boolean
        get() = notifications?.isNotificationPolicyAccessGranted == true

    /** No vibrar/mudo, Toque e Notificação aparecem zerados, como no painel do sistema. */
    fun level(s: VolStream): Float {
        val quiet = ringerMode != AudioManager.RINGER_MODE_NORMAL
        if (quiet && (s == VolStream.RING || s == VolStream.NOTIFICATION)) return 0f
        return raw[s] ?: 0f
    }

    fun refresh() {
        val a = audio ?: return
        for (s in VolStream.values()) {
            val max = a.getStreamMaxVolume(s.type).coerceAtLeast(1)
            raw[s] = a.getStreamVolume(s.type) / max.toFloat()
        }
        ringerMode = a.ringerMode
        outputName = findOutput(a)
    }

    fun setLevel(s: VolStream, fraction: Float) {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(s.type).coerceAtLeast(1)
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        if ((s == VolStream.RING || s == VolStream.NOTIFICATION) &&
            target > 0 && ringerMode != AudioManager.RINGER_MODE_NORMAL
        ) {
            setRingerMode(AudioManager.RINGER_MODE_NORMAL)
        }
        try {
            if (a.getStreamVolume(s.type) != target) a.setStreamVolume(s.type, target, 0)
        } catch (_: Exception) {
        }
        raw[s] = a.getStreamVolume(s.type) / max.toFloat()
    }

    /** Devolve false quando o Android recusa (mudo total sem acesso ao "Não perturbe"). */
    fun setRingerMode(mode: Int): Boolean {
        val a = audio ?: return false
        return try {
            a.ringerMode = mode
            ringerMode = a.ringerMode
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun findOutput(a: AudioManager): String? {
        val devices = try {
            a.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            return null
        }
        val bt = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (bt != null) {
            val name = bt.productName?.toString()
            return if (name.isNullOrBlank()) "Bluetooth" else name
        }
        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        return if (wired != null) context.getString(R.string.cc_vp_headphones) else null
    }
}
