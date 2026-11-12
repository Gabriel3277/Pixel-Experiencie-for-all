package com.pixelexperience.forall.controlcenter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pixelexperience.forall.controlcenter.ui.VolumeCallbacks
import com.pixelexperience.forall.controlcenter.ui.VolumePanel
import com.pixelexperience.forall.controlcenter.ui.VolumeUi
import com.pixelexperience.forall.themes.PixelTheme
import kotlin.math.roundToInt

/**
 * Painel de volume (estilo Android 17). Aparece ao apertar as teclas de volume ou
 * pelo botão ⋮ da central de controle. Só existe enquanto está na tela.
 */
internal class VolumeWindow(private val service: AccessibilityService) {

    private val windowManager: WindowManager = service.getSystemService(WindowManager::class.java)
    private val audio: AudioManager? = service.getSystemService(AudioManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val owner = OverlayOwner().also { it.resume() }
    private val state = VolumeState(service)
    private val ui = VolumeUi()

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    /** Tecla de volume: muda o volume certo (como o sistema) e mostra o painel. */
    fun onVolumeKey(up: Boolean) {
        try {
            audio?.adjustSuggestedStreamVolume(
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                0,
            )
        } catch (_: Exception) {
        }
        show(expanded = false)
    }

    fun show(expanded: Boolean) {
        ensureView(expanded)
        state.refresh()
        if (expanded) ui.expanded = true
        ui.visible = true
        ui.poke()
    }

    fun onConfigurationChanged() = remove()

    fun stop() {
        remove()
        owner.destroy()
    }

    private fun ensureView(expanded: Boolean) {
        if (view != null) return

        val statusBarPx = readStatusBarHeight()
        val v = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PixelTheme(dynamicColor = true) {
                    val topInset = with(LocalDensity.current) { statusBarPx.toDp() }
                    VolumePanel(
                        state = state,
                        ui = ui,
                        topInset = topInset,
                        cb = VolumeCallbacks(
                            onExpandedLayout = { applyLayout(it) },
                            onGone = { post { if (!ui.visible) remove() } },
                            onNeedPolicy = {
                                launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            },
                            onOpenSound = { launch(Intent(Settings.ACTION_SOUND_SETTINGS)) },
                            onOpenCaptions = { launch(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) },
                        ),
                    )
                }
            }
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "PixelControlCenterVolume"
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        fillLayout(lp, expanded)

        try {
            windowManager.addView(v, lp)
            view = v
            params = lp
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível criar a janela do volume", e)
        }
    }

    private fun applyLayout(expanded: Boolean) {
        val lp = params ?: return
        val v = view ?: return
        fillLayout(lp, expanded)
        try {
            windowManager.updateViewLayout(v, lp)
        } catch (_: Exception) {
        }
    }

    /** Aberto = tela toda (para tocar fora e fechar); fechado = só a faixa da barra. */
    private fun fillLayout(lp: WindowManager.LayoutParams, expanded: Boolean) {
        if (expanded) {
            lp.width = WindowManager.LayoutParams.MATCH_PARENT
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.TOP
            lp.x = 0
            lp.y = 0
        } else {
            lp.width = (200 * density).roundToInt()
            lp.height = (330 * density).roundToInt()
            lp.gravity = Gravity.TOP or Gravity.END
            lp.x = 0
            lp.y = (service.resources.displayMetrics.heightPixels * 0.12f).roundToInt()
        }
    }

    private fun remove() {
        view?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: Exception) {
            }
        }
        view = null
        params = null
        ui.reset()
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir ${intent.action}", e)
        }
        ui.expanded = false
        ui.visible = false
    }

    private fun readStatusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val h = if (id > 0) service.resources.getDimensionPixelSize(id) else 0
        return if (h > 0) h else (24 * density).roundToInt()
    }

    private companion object {
        const val TAG = "PixelControlCenter"
    }
}
