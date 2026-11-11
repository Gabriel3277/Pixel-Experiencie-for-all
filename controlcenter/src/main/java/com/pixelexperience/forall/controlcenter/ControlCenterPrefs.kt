package com.pixelexperience.forall.controlcenter

import android.content.Context
import android.content.SharedPreferences

/**
 * Liga/desliga o painel. O app grava aqui o estado do módulo (interruptor da Home)
 * e o serviço de acessibilidade reage na hora.
 */
object ControlCenterPrefs {
    const val KEY_ENABLED = "module_enabled"
    private const val FILE = "controlcenter_prefs"

    internal fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
