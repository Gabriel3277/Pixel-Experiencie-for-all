package com.pixelexperience.forall.controlcenter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade da Central de controle.
 *
 * Mantém uma janela fina (do tipo "overlay de acessibilidade") na barra de status.
 * Ao deslizar para baixo, a janela cresce, o painel do sistema é fechado e o nosso
 * painel aparece. Não lê o conteúdo da tela.
 */
class ControlCenterService : AccessibilityService() {

    private var shade: ShadeWindow? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ControlCenterPrefs.KEY_ENABLED) applyEnabled()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ControlCenterPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        applyEnabled()
    }

    private fun applyEnabled() {
        if (ControlCenterPrefs.isEnabled(this)) {
            if (shade == null) {
                shade = ShadeWindow(this).also { it.start() }
            }
        } else {
            shade?.stop()
            shade = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        shade?.onWindowStateChanged(event.packageName?.toString())
    }

    override fun onInterrupt() {
        // Sem retorno falado ou tátil.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        ControlCenterPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        shade?.stop()
        shade = null
    }
}
