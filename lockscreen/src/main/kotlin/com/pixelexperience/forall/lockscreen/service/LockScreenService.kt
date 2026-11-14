package com.pixelexperience.forall.lockscreen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pixelexperience.forall.lockscreen.LockScreenModule
import com.pixelexperience.forall.lockscreen.data.LockSettingsRepository
import com.pixelexperience.forall.lockscreen.data.WallpaperRepository
import com.pixelexperience.forall.lockscreen.ui.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Escuta SCREEN_OFF (só pode ser registrado dinamicamente) e abre a LockScreenActivity,
 * que aparece POR CIMA da tela de bloqueio do sistema. O desbloqueio real continua sendo do Android.
 */
class LockScreenService : Service() {

    private var overlayAnchor: View? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) showLock()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pixel Lock Screen", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.pixelexperience.forall.lockscreen.ui.settings.LockSettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Pixel Lock Screen ativa")
            .setContentText("Toque para ajustar")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
        ServiceCompat.startForeground(this, ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        addOverlayAnchor()
        LockScreenModule.ensureNotificationListener(this)
        val s = LockSettingsRepository.get(this).settings.value
        CoroutineScope(Dispatchers.Default).launch {
            WallpaperRepository.load(applicationContext, s.wallpaperId, s.wallpaperVersion)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        overlayAnchor?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        overlayAnchor = null
        super.onDestroy()
    }

    /** Janela de overlay 1x1 invisível: mantém a permissão de abrir activity em segundo plano válida no Android 15+. */
    private fun addOverlayAnchor() {
        if (!Settings.canDrawOverlays(this)) return
        runCatching {
            val v = View(this)
            val lp = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            getSystemService(WindowManager::class.java).addView(v, lp)
            overlayAnchor = v
        }
    }

    private fun showLock() {
        if (!Settings.canDrawOverlays(this)) return
        if (!LockSettingsRepository.get(this).settings.value.enabled) return
        runCatching {
            startActivity(
                Intent(this, LockScreenActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            )
        }
    }

    companion object {
        private const val CHANNEL = "pxe_lockscreen"
        private const val ID = 7301
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LockScreenService::class.java))
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, LockScreenService::class.java)) }
    }
}

class LockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LockScreenModule.sync(context)
    }
}
