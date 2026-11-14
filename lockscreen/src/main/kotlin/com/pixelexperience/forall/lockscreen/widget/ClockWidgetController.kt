package com.pixelexperience.forall.lockscreen.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Hospeda widgets de terceiros via AppWidgetHost/AppWidgetManager (APIs oficiais do Android).
 * Singleton por processo.
 */
class ClockWidgetController private constructor(private val appContext: Context) {

    val host = AppWidgetHost(appContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)

    private var listeners = 0

    @Synchronized
    fun startListening() {
        if (listeners++ == 0) host.startListening()
    }

    @Synchronized
    fun stopListening() {
        if (listeners > 0 && --listeners == 0) host.stopListening()
    }

    fun installedProviders(): List<AppWidgetProviderInfo> = manager.installedProviders

    fun infoFor(id: Int): AppWidgetProviderInfo? =
        if (id == ClockWidgetStore.INVALID) null else manager.getAppWidgetInfo(id)

    fun selectedId(): Int = ClockWidgetStore.widgetId(appContext)

    fun selectedInfo(): AppWidgetProviderInfo? = infoFor(selectedId())

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        if (id != ClockWidgetStore.INVALID) host.deleteAppWidgetId(id)
    }

    /** true se o vínculo foi feito direto; false se o usuário precisa autorizar (ver bindIntent). */
    fun bindIfAllowed(id: Int, provider: ComponentName): Boolean =
        manager.bindAppWidgetIdIfAllowed(id, provider)

    /** Tela do sistema que pede autorização ao usuário para vincular o widget. */
    fun bindIntent(id: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    /** Abre a configuração do widget (se tiver). O resultado chega em onActivityResult. */
    fun startConfigure(activity: Activity, id: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
    }

    /** Confirma a escolha: apaga o widget anterior (substituição) e salva o novo. */
    fun commit(id: Int, provider: ComponentName) {
        val old = ClockWidgetStore.widgetId(appContext)
        if (old != ClockWidgetStore.INVALID && old != id) deleteId(old)
        ClockWidgetStore.save(appContext, id, provider)
    }

    fun remove() {
        deleteId(ClockWidgetStore.widgetId(appContext))
        ClockWidgetStore.clear(appContext)
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = infoFor(id) ?: return null
        return host.createView(context, id, info)
    }

    companion object {
        const val HOST_ID = 0x50584B

        @Volatile
        private var instance: ClockWidgetController? = null

        fun get(context: Context): ClockWidgetController =
            instance ?: synchronized(this) {
                instance ?: ClockWidgetController(context.applicationContext).also { instance = it }
            }
    }
}
