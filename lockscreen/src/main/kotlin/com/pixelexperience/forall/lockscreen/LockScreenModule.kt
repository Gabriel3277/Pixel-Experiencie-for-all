package com.pixelexperience.forall.lockscreen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.pixelexperience.forall.lockscreen.data.LockSettingsRepository
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

    /** Liga/desliga o serviço conforme a configuração e a permissão. Chame no boot do app e após mudar `enabled`. */
    fun sync(ctx: Context) {
        val on = LockSettingsRepository.get(ctx).settings.value.enabled && hasOverlayPermission(ctx)
        if (on) LockScreenService.start(ctx) else LockScreenService.stop(ctx)
    }
}
