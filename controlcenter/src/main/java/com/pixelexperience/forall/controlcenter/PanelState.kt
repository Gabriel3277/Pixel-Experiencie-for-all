package com.pixelexperience.forall.controlcenter

import android.bluetooth.BluetoothManager
import android.media.AudioManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Estado real dos controles. Tudo aqui lê o estado verdadeiro do Android;
 * nada é simulado.
 */
internal class PanelState(private val context: Context) {

    var internetActive by mutableStateOf(false)
        private set
    var internetSubtitle by mutableStateOf(context.getString(R.string.cc_net_none))
        private set
    var bluetoothActive by mutableStateOf(false)
        private set
    var bluetoothSubtitle by mutableStateOf(context.getString(R.string.cc_off))
        private set
    var torchOn by mutableStateOf(false)
        private set
    var autoRotate by mutableStateOf(false)
        private set
    var brightness by mutableFloatStateOf(0.5f)
        private set
    var canWriteSettings by mutableStateOf(false)
        private set
    var autoBrightness by mutableStateOf(false)
        private set
    var volume by mutableFloatStateOf(0.5f)
        private set
    var airplaneOn by mutableStateOf(false)
        private set

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val torchId: String? = findTorchId()
    val hasTorch: Boolean get() = torchId != null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) torchOn = enabled
        }
    }

    fun start() {
        refresh()
        readBrightness()
        readVolume()
        try {
            cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }

    /** Atualiza o que muda sozinho (rede, Bluetooth, rotação, permissão). */
    fun refresh() {
        refreshInternet()
        refreshBluetooth()
        autoRotate = Settings.System.getInt(
            context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0,
        ) == 1
        canWriteSettings = Settings.System.canWrite(context)
        autoBrightness = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        airplaneOn = Settings.Global.getInt(
            context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0,
        ) == 1
    }

    private fun refreshInternet() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = try {
            cm?.getNetworkCapabilities(cm.activeNetwork)
        } catch (_: Exception) {
            null
        }
        when {
            caps == null -> {
                internetActive = false
                internetSubtitle = context.getString(R.string.cc_net_none)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                internetActive = true
                internetSubtitle = context.getString(R.string.cc_net_wifi)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                internetActive = true
                internetSubtitle = context.getString(R.string.cc_net_mobile)
            }
            else -> {
                internetActive = true
                internetSubtitle = context.getString(R.string.cc_net_other)
            }
        }
    }

    private fun refreshBluetooth() {
        val on = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
        bluetoothActive = on
        bluetoothSubtitle = context.getString(if (on) R.string.cc_on else R.string.cc_off)
    }

    private fun readBrightness() {
        val raw = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128,
        )
        brightness = (raw / 255f).coerceIn(0f, 1f)
    }

    fun updateBrightness(value: Float) {
        if (!canWriteSettings) return
        brightness = value.coerceIn(0f, 1f)
        val level = (brightness * 255f).roundToInt().coerceIn(1, 255)
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
        } catch (_: Exception) {
        }
    }

    fun toggleAutoBrightness() {
        if (!canWriteSettings) return
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (autoBrightness) {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                } else {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                },
            )
        } catch (_: Exception) {
        }
        refresh()
    }

    private fun readVolume() {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = a.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    /** Volume de mídia (não precisa de permissão especial). */
    fun updateVolume(value: Float) {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = value.coerceIn(0f, 1f)
        try {
            a.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
        } catch (_: Exception) {
        }
    }

    fun toggleRotation() {
        if (!canWriteSettings) return
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (autoRotate) 0 else 1,
            )
        } catch (_: Exception) {
        }
        refresh()
    }

    fun toggleTorch() {
        val id = torchId ?: return
        try {
            cameraManager?.setTorchMode(id, !torchOn)
        } catch (_: Exception) {
        }
    }

    private fun findTorchId(): String? {
        val cm = cameraManager ?: return null
        return try {
            val withFlash = cm.cameraIdList.filter { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            withFlash.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: withFlash.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
