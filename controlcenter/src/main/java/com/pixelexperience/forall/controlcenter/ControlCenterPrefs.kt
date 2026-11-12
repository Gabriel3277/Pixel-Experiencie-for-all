package com.pixelexperience.forall.controlcenter

import android.content.Context
import android.content.SharedPreferences

/**
 * Liga/desliga o painel. O app grava aqui o estado do módulo (interruptor da Home)
 * e o serviço de acessibilidade reage na hora.
 */
object ControlCenterPrefs {
    const val KEY_ENABLED = "module_enabled"
    const val KEY_VOLUME_PANEL = "volume_panel_enabled"
    private const val FILE = "controlcenter_prefs"

    internal fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Painel de volume próprio (intercepta as teclas de volume). */
    fun isVolumePanelEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOLUME_PANEL, true)

    fun setVolumePanelEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOLUME_PANEL, enabled).apply()
    }
}
