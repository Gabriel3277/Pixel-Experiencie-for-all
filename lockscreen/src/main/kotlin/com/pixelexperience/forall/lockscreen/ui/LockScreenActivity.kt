package com.pixelexperience.forall.lockscreen.ui

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixelexperience.forall.lockscreen.data.LockSettingsRepository
import com.pixelexperience.forall.lockscreen.data.ShortcutType
import com.pixelexperience.forall.lockscreen.notifications.LockNotificationListener
import com.pixelexperience.forall.lockscreen.notifications.LockNotificationRepository
import com.pixelexperience.forall.lockscreen.system.TorchController
import com.pixelexperience.forall.lockscreen.ui.theme.LockTheme

/**
 * Aparece por cima do keyguard (showWhenLocked). Não substitui a tela de bloqueio do sistema:
 * para desbloquear usa KeyguardManager.requestDismissKeyguard (PIN/biometria do Android).
 */
class LockScreenActivity : ComponentActivity() {

    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { finishAndRemoveTask() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // barra de navegação realmente transparente (sem scrim automático) e ícones claros da status bar, como no Pixel
        window.isNavigationBarContrastEnforced = false
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* a tela de bloqueio ignora "voltar" */ }
        })
        TorchController.register(this)
        // se o usuário desbloquear por outro meio (digital/rosto no keyguard), fecha junto
        ContextCompat.registerReceiver(
            this, userPresent, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val repo = LockSettingsRepository.get(this)

        setContent {
            LockTheme {
                val settings by repo.settings.collectAsStateWithLifecycle()
                val wallpaper = rememberWallpaper(settings.wallpaperId, settings.wallpaperVersion)
                val notifs by LockNotificationRepository.items.collectAsStateWithLifecycle()
                val torch by TorchController.isOn.collectAsStateWithLifecycle()
                LockScreenContent(
                    settings = settings,
                    wallpaper = wallpaper,
                    notifications = notifs,
                    torchOn = torch,
                    onShortcut = { runShortcut(it) },
                    onOpenNotification = { n ->
                        unlock(onSuccess = {
                            n.contentIntent?.let { pi ->
                                runCatching {
                                    // Android 14+: o remetente precisa permitir explicitamente abrir a activity
                                    val opts = ActivityOptions.makeBasic()
                                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                        .toBundle()
                                    pi.send(this@LockScreenActivity, 0, null, null, null, null, opts)
                                }
                            }
                        })
                    },
                    onDismissNotification = { LockNotificationListener.cancel(it) },
                    onClearAll = { LockNotificationListener.cancelAll() },
                    onRequestUnlock = { cb -> unlock(onFail = { cb(false) }) },
                )
            }
        }
    }

    private var unlockPending = false

    private fun unlock(onSuccess: () -> Unit = {}, onFail: () -> Unit = {}) {
        if (unlockPending) { onFail(); return }
        unlockPending = true
        val km = getSystemService(KeyguardManager::class.java)
        val started = runCatching {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    unlockPending = false
                    onSuccess()
                    finishAndRemoveTask()
                }
                override fun onDismissCancelled() { unlockPending = false; onFail() }
                override fun onDismissError() { unlockPending = false; onFail() }
            })
        }.isSuccess
        if (!started) { unlockPending = false; onFail() }
    }

    private fun launchAfterUnlock(intent: Intent) {
        unlock(onSuccess = { runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
    }

    private fun runShortcut(type: ShortcutType) {
        when (type) {
            ShortcutType.NONE -> Unit
            ShortcutType.FLASHLIGHT -> TorchController.toggle(this)
            ShortcutType.CAMERA -> {
                // câmera segura abre direto sobre o keyguard, sem pedir PIN
                val ok = runCatching {
                    startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess // ActivityNotFoundException cai no fallback abaixo
                if (!ok) launchAfterUnlock(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            }
            ShortcutType.DIALER -> launchAfterUnlock(Intent(Intent.ACTION_DIAL))
            ShortcutType.ALARM -> launchAfterUnlock(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            ShortcutType.ASSISTANT -> launchAfterUnlock(Intent(Intent.ACTION_VOICE_COMMAND))
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(userPresent) }
        TorchController.unregister(this)
        super.onDestroy()
    }
}
