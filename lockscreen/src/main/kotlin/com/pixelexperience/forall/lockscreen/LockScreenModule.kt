package com.pixelexperience.forall.lockscreen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.pixelexperience.forall.lockscreen.data.LockSettingsRepository
import com.pixelexperience.forall.lockscreen.notifications.LockNotificationListener
import com.pixelexperience.forall.lockscreen.notifications.LockNotificationRepository
import com.pixelexperience.forall.lockscreen.service.LockScreenService
import com.pixelexperience.forall.lockscreen.ui.settings.LockSettingsActivity

/** Ponto de entrada público do módulo lockscreen (para o app/core do Pixel Experience for All). */
object LockScreenModule {

    /** Abre a tela de configurações do módulo. Alternativa: embutir `LockSettingsScreen` na navegação do Core. */
    fun openSettings(ctx: Context) {
        ctx.startActivity(Intent(ctx, LockSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun hasOverlayPermission(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun hasNotificationAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun overlaySettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun notificationAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Android 11+: abre direto a página deste app (no Android 13+ o sistema avisa se for preciso liberar "configurações restritas"). */
    fun notificationAccessIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(ctx, LockNotificationListener::class.java).flattenToString(),
            )
        } else notificationAccessIntent()

    /** Com o acesso concedido mas o listener desconectado (app atualizado, processo morto), pede ao sistema para reconectar. */
    fun ensureNotificationListener(ctx: Context) {
        if (!hasNotificationAccess(ctx) || LockNotificationRepository.connected.value) return
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(ctx, LockNotificationListener::class.java))
        }
    }

    /** Liga/desliga o serviço conforme a configuração e a permissão. Chame no boot do app e após mudar `enabled`. */
    fun sync(ctx: Context) {
        val on = LockSettingsRepository.get(ctx).settings.value.enabled && hasOverlayPermission(ctx)
        if (on) LockScreenService.start(ctx) else LockScreenService.stop(ctx)
        ensureNotificationListener(ctx)
    }
}
