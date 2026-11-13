package com.pixelexperience.forall.lockscreen.notifications

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow

data class LockNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?,
    val color: Int,
    val title: String,
    val text: String,
    val bigText: String,
    val postTime: Long,
    val clearable: Boolean,
    val ongoing: Boolean,
    val visibility: Int,
    val contentIntent: PendingIntent?,
)

object LockNotificationRepository {
    val items = MutableStateFlow<List<LockNotification>>(emptyList())
    val connected = MutableStateFlow(false)
}

/** Acesso oficial às notificações (NotificationListenerService). O usuário concede em Ajustes > Acesso a notificações. */
class LockNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        LockNotificationRepository.connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        LockNotificationRepository.connected.value = false
        LockNotificationRepository.items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .mapNotNull { convert(it) }
            .sortedByDescending { it.postTime }
        LockNotificationRepository.items.value = list
    }

    private fun convert(sbn: StatusBarNotification): LockNotification? {
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        if (n.category == Notification.CATEGORY_TRANSPORT) return null
        if (sbn.packageName == packageName && sbn.isOngoing) return null
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: lines ?: ""
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrElse { ex.getString("android.substName") ?: sbn.packageName.substringAfterLast('.') }
        val icon = runCatching { n.smallIcon?.loadDrawable(this)?.toBitmap(72, 72)?.asImageBitmap() }.getOrNull()
        return LockNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            icon = icon,
            color = n.color,
            title = title,
            text = text,
            bigText = big,
            postTime = if (n.`when` > 0) n.`when` else sbn.postTime,
            clearable = sbn.isClearable,
            ongoing = sbn.isOngoing,
            visibility = n.visibility,
            contentIntent = n.contentIntent,
        )
    }

    companion object {
        @Volatile private var instance: LockNotificationListener? = null
        fun cancel(key: String) { runCatching { instance?.cancelNotification(key) } }
        fun cancelAll() { runCatching { instance?.cancelAllNotifications() } }
    }
}
