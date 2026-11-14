package com.pixelexperience.forall.lockscreen.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences

/**
 * Guarda qual widget de relógio está escolhido (id do AppWidgetHost + provider).
 * Usa SharedPreferences para leitura síncrona na lockscreen.
 */
object ClockWidgetStore {
    private const val PREFS = "lockscreen_clock_widget"
    private const val KEY_ID = "app_widget_id"
    private const val KEY_PROVIDER = "provider"

    const val INVALID = AppWidgetManager.INVALID_APPWIDGET_ID

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun widgetId(context: Context): Int = prefs(context).getInt(KEY_ID, INVALID)

    fun provider(context: Context): ComponentName? =
        prefs(context).getString(KEY_PROVIDER, null)?.let { ComponentName.unflattenFromString(it) }

    fun save(context: Context, id: Int, provider: ComponentName) {
        prefs(context).edit()
            .putInt(KEY_ID, id)
            .putString(KEY_PROVIDER, provider.flattenToString())
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** Observa mudanças. Retorna a função que cancela o registro. */
    fun register(context: Context, onChange: () -> Unit): () -> Unit {
        val p = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        p.registerOnSharedPreferenceChangeListener(listener)
        return { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
