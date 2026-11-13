package com.pixelexperience.forall.lockscreen.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lanterna via CameraManager.setTorchMode (API oficial, sem permissão de câmera). */
object TorchController {
    private val _on = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _on.asStateFlow()
    private var registered = false
    private var camId: String? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == camId) _on.value = enabled
        }
    }

    fun register(ctx: Context) {
        if (registered) return
        runCatching {
            val cm = ctx.getSystemService(CameraManager::class.java)
            camId = cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cm.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        }
    }

    fun unregister(ctx: Context) {
        if (!registered) return
        runCatching { ctx.getSystemService(CameraManager::class.java).unregisterTorchCallback(callback) }
        registered = false
    }

    fun toggle(ctx: Context): Boolean {
        val id = camId ?: return false
        return runCatching {
            ctx.getSystemService(CameraManager::class.java).setTorchMode(id, !_on.value)
            true
        }.getOrDefault(false)
    }
}
