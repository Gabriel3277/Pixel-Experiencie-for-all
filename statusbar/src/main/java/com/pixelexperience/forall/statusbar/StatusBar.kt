package com.pixelexperience.forall.statusbar

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** API pública do módulo :statusbar. */
object StatusBar {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isEnabled(context: Context): Boolean =
        StatusBarPrefs.bool(context, StatusBarPrefs.ENABLED, false)

    /** True se o usuário liberou "Acesso às notificações" para o app. */
    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

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

    /** Antigo interruptor "ícones escuros"; só lido para migrar para ICON_MODE. */
    const val DARK_ICONS = "dark_icons"

    /** 0 = automático (pelo papel de parede), 1 = claros, 2 = escuros. */
    const val ICON_MODE = "icon_mode"

    /** 0 = todas, 1 = só prioridade, 2 = nenhuma. */
    const val NOTIF_MODE = "notif_mode"

    private const val FILE = "statusbar_prefs"

    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun bool(context: Context, key: String, def: Boolean): Boolean =
        prefs(context).getBoolean(key, def)

    fun set(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun int(context: Context, key: String, def: Int): Int =
        prefs(context).getInt(key, def)

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    fun iconMode(context: Context): Int {
        val p = prefs(context)
        return when {
            p.contains(ICON_MODE) -> p.getInt(ICON_MODE, 0)
            p.getBoolean(DARK_ICONS, false) -> 2
            else -> 0
        }
    }
}
