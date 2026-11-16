package com.pixelexperience.forall.lockscreen.ui

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
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
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixelexperience.forall.lockscreen.LockScreenModule
import com.pixelexperience.forall.lockscreen.data.LockSettingsRepository
import com.pixelexperience.forall.lockscreen.data.ShortcutType
import com.pixelexperience.forall.lockscreen.notifications.LockNotificationListener
import com.pixelexperience.forall.lockscreen.notifications.LockNotificationRepository
import com.pixelexperience.forall.lockscreen.system.TorchController
import androidx.compose.foundation.isSystemInDarkTheme
import com.pixelexperience.forall.lockscreen.ui.theme.LockTheme
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import com.pixelexperience.forall.lockscreen.data.LockPalette
import com.pixelexperience.forall.lockscreen.ui.aod.AodContent
import com.pixelexperience.forall.lockscreen.ui.aod.AodState

/**
 * Aparece por cima do keyguard (showWhenLocked). Não substitui a tela de bloqueio do sistema:
 * para desbloquear usa KeyguardManager.requestDismissKeyguard (PIN/biometria do Android).
 */
class LockScreenActivity : ComponentActivity() {

    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // desbloqueio pela biometria nativa (digital lateral / rosto): anima a saída em vez de sumir de uma vez
            if (isFinishing) return
            unlockSignal.value = true
            window.decorView.postDelayed({ if (!isFinishing) finishAndRemoveTask() }, 900)
        }
    }

    private val unlockSignal = mutableStateOf(false)
    private val notifAccess = mutableStateOf(true)
    private val aod = mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    // fim da janela do AOD: só solta a tela; o timeout do sistema apaga (o conteúdo continua preto)
    private val endAod = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    private val idle = Runnable {
        val pm = getSystemService(PowerManager::class.java)
        if (!aod.value && pm.isInteractive && LockSettingsRepository.get(this).settings.value.aodEnabled) applyAod(true)
    }
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = armIdle()
    }
    private var modeAt = 0L
    private val doubleTap by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // ignora o 2º toque de quem acabou de sair do AOD
                if (!aod.value && SystemClock.uptimeMillis() - modeAt > 800 &&
                    LockSettingsRepository.get(this@LockScreenActivity).settings.value.aodEnabled
                ) applyAod(true)
                return false
            }
        })
    }

    private fun armIdle() {
        handler.removeCallbacks(idle)
        if (!aod.value) handler.postDelayed(idle, IDLE_MS)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        doubleTap.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) armIdle()
        return super.dispatchTouchEvent(ev)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        applyAod(intent?.getBooleanExtra(EXTRA_AOD, false) == true)
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
        ContextCompat.registerReceiver(
            this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val repo = LockSettingsRepository.get(this)

        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle()
            val wallpaper = rememberWallpaper(settings.wallpaperId, settings.wallpaperVersion)
            LockTheme(seed = wallpaper?.let { it.palette.seed }, dark = isSystemInDarkTheme()) {
                val notifs by LockNotificationRepository.items.collectAsStateWithLifecycle()
                val torch by TorchController.isOn.collectAsStateWithLifecycle()
                Crossfade(targetState = aod.value, animationSpec = tween(280), label = "aod") { isAod ->
                if (isAod) AodContent(
                    settings = settings,
                    notifications = notifs,
                    palette = wallpaper?.palette ?: LockPalette.Default,
                    onWake = { AodState.screenIsAod = false; applyAod(false) },
                ) else LockScreenContent(
                    settings = settings,
                    wallpaper = wallpaper,
                    notifications = notifs,
                    torchOn = torch,
                    notificationAccess = notifAccess.value,
                    onGrantNotificationAccess = { launchAfterUnlock(LockScreenModule.notificationAccessIntent(this@LockScreenActivity)) },
                    onManageNotifications = { launchAfterUnlock(android.content.Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS")) },
                    unlockSignal = unlockSignal.value,
                    onUnlockAnimated = { if (!isFinishing) finishAndRemoveTask() },
                    onShortcut = { runShortcut(it) },
                    onOpenNotification = { n ->
                        unlock(onSuccess = { n.contentIntent?.let { sendPending(it) } })
                    },
                    onAction = { act -> unlock(onSuccess = { act.intent?.let { sendPending(it) } }) },
                    onDismissNotification = { LockNotificationListener.cancel(it) },
                    onClearAll = { LockNotificationListener.cancelAll() },
                    onRequestUnlock = { cb -> unlock(onFail = { cb(false) }) },
                )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyAod(intent.getBooleanExtra(EXTRA_AOD, false))
    }

    /** AOD em LCD: tela acesa em brilho mínimo por AOD_WINDOW_MS; depois libera e o timeout do sistema apaga. */
    private fun applyAod(on: Boolean) {
        handler.removeCallbacks(endAod)
        handler.removeCallbacks(idle)
        modeAt = SystemClock.uptimeMillis()
        aod.value = on
        if (on) {
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = 0.01f }
            handler.postDelayed(endAod, AOD_WINDOW_MS)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            setTurnScreenOn(false)
            armIdle()
        }
    }

    companion object {
        const val EXTRA_AOD = "pxe_aod"
        private const val AOD_WINDOW_MS = 20_000L
        private const val IDLE_MS = 17_000L
    }

    override fun onResume() {
        super.onResume()
        notifAccess.value = LockScreenModule.hasNotificationAccess(this)
        LockScreenModule.ensureNotificationListener(this)
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

    /** Android 14+: o remetente precisa permitir explicitamente iniciar a activity do PendingIntent. */
    private fun sendPending(pi: PendingIntent) {
        runCatching {
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
            pi.send(this, 0, null, null, null, null, opts)
        }
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
        handler.removeCallbacks(endAod)
        handler.removeCallbacks(idle)
        runCatching { unregisterReceiver(screenOn) }
        runCatching { unregisterReceiver(userPresent) }
        TorchController.unregister(this)
        super.onDestroy()
    }
}
