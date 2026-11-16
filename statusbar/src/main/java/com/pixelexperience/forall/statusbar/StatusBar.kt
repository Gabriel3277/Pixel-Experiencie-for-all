package com.pixelexperience.forall.statusbar

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** API pública do módulo :statusbar. */
object StatusBar {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isEnabled(context: Context): Boolean =
        StatusBarPrefs.bool(context, StatusBarPrefs.ENABLED, false)

    /** Inicia o overlay (só se a permissão de sobreposição já foi concedida). */
    fun start(context: Context) {
        if (!canDrawOverlays(context)) return
        context.startForegroundService(Intent(context, StatusBarService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, StatusBarService::class.java))
    }
}

internal object StatusBarPrefs {
    const val ENABLED = "enabled"
    const val SHOW_MUTE = "show_mute"
    const val SHOW_VIBRATE = "show_vibrate"
    const val SHOW_BATTERY_PCT = "show_battery_pct"
    const val DARK_ICONS = "dark_icons"

    private const val FILE = "statusbar_prefs"

    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun bool(context: Context, key: String, def: Boolean): Boolean =
        prefs(context).getBoolean(key, def)

    fun set(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }
}
