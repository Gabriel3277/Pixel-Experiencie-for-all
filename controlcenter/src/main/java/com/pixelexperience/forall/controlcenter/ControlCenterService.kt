package com.pixelexperience.forall.controlcenter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.app.KeyguardManager
import android.content.res.Configuration
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade da Central de controle.
 *
 * Mantém uma janela pequena (do tipo "overlay de acessibilidade") numa faixa do lado
 * direito da tela. Ao deslizar para baixo nela, a janela cresce, o painel do sistema é fechado e o nosso
 * painel aparece. Não lê o conteúdo da tela.
 */
class ControlCenterService : AccessibilityService() {

    private var shade: ShadeWindow? = null
    private var notifShade: ShadeWindow? = null
    private var volume: VolumeWindow? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ControlCenterPrefs.KEY_ENABLED) applyEnabled()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        CrashLog.install(applicationContext)
        ControlCenterPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        applyEnabled()
    }

    private fun applyEnabled() {
        if (ControlCenterPrefs.isEnabled(this)) {
            if (volume == null) {
                volume = VolumeWindow(this)
            }
            if (shade == null) {
                shade = ShadeWindow(this, showVolume = { volume?.show(expanded = true) })
                    .also { it.start() }
            }
            if (notifShade == null) {
                notifShade = ShadeWindow(this, kind = ShadeKind.NOTIFICATIONS)
                    .also { it.start() }
            }
        } else {
            shade?.stop()
            shade = null
            notifShade?.stop()
            notifShade = null
            volume?.stop()
            volume = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        shade?.onWindowStateChanged(pkg)
        notifShade?.onWindowStateChanged(pkg)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        shade?.onConfigurationChanged()
        notifShade?.onConfigurationChanged()
        volume?.onConfigurationChanged()
    }

    /**
     * Teclas de volume: o nosso painel muda o volume e aparece no lugar do painel do
     * sistema. Com a tela bloqueada, ou com a opção desligada, o Android cuida como sempre.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val panel = volume ?: return false
        if (!ControlCenterPrefs.isVolumePanelEnabled(this)) return false
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            panel.onVolumeKey(up = code == KeyEvent.KEYCODE_VOLUME_UP)
        }
        return true
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
        notifShade?.stop()
        notifShade = null
        volume?.stop()
        volume = null
    }
}
