package com.pixelexperience.forall.controlcenter

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

internal data class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val icon: ImageBitmap?,
    val color: Int,
    val title: String,
    val text: String,
    val whenMs: Long,
    val clearable: Boolean,
    val intent: PendingIntent?,
)

/** Lista de notificações que o painel mostra. Quem alimenta é o PanelNotificationService. */
internal object NotificationStore {

    val items = mutableStateListOf<NotifItem>()

    var connected by mutableStateOf(false)
        private set

    private var listener: NotificationListenerService? = null

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun onConnected(service: NotificationListenerService) {
        listener = service
        connected = true
        items.clear()
        try {
            service.activeNotifications?.forEach { upsert(service, it) }
        } catch (_: Exception) {
        }
    }

    fun onDisconnected() {
        listener = null
        connected = false
        items.clear()
    }

    fun upsert(context: Context, sbn: StatusBarNotification) {
        val item = toItem(context, sbn)
        val index = items.indexOfFirst { it.key == sbn.key }
        if (item == null) {
            if (index >= 0) items.removeAt(index)
            return
        }
        if (index >= 0) items.removeAt(index)
        val pos = items.indexOfFirst { it.whenMs < item.whenMs }.let { if (it < 0) items.size else it }
        items.add(pos, item)
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
    }

    fun remove(key: String) {
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0) items.removeAt(index)
    }

    fun dismiss(key: String) {
        try {
            listener?.cancelNotification(key)
        } catch (_: Exception) {
        }
        remove(key)
    }

    fun clearAll() {
        try {
            listener?.cancelAllNotifications()
        } catch (_: Exception) {
        }
        items.removeAll { it.clearable }
    }

    /** Toca na notificação: dispara a ação dela, como a barra do sistema faria. */
    fun open(context: Context, item: NotifItem) {
        val pi = item.intent ?: return
        try {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    .toBundle()
            } else {
                null
            }
            pi.send(context, 0, null, null, null, null, options)
        } catch (_: Exception) {
        }
    }

    private fun toItem(context: Context, sbn: StatusBarNotification): NotifItem? {
        val n = sbn.notification ?: return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        val pm = context.packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        return NotifItem(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appName,
            icon = loadIcon(context, n),
            color = n.color,
            title = title,
            text = text,
            whenMs = if (n.`when` > 0L) n.`when` else sbn.postTime,
            clearable = sbn.isClearable,
            intent = n.contentIntent,
        )
    }

    private fun loadIcon(context: Context, n: Notification): ImageBitmap? = try {
        val drawable = n.smallIcon?.loadDrawable(context)
        if (drawable == null) {
            null
        } else {
            val size = 48
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bmp))
            bmp.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }

    private const val MAX_ITEMS = 50
}
