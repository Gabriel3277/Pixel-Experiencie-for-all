package com.pixelexperience.forall.statusbar

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal data class NotifIcon(
    val key: String,
    val pkg: String,
    val bitmap: ImageBitmap,
    val priority: Boolean
)

/** Ponte entre o NotificationListenerService e o overlay (mesmo processo). */
internal object NotificationRepository {
    var icons by mutableStateOf<List<NotifIcon>>(emptyList())
        private set

    fun set(list: List<NotifIcon>) {
        icons = list
    }
}

/** Lê as notificações reais e publica os pequenos ícones para a barra de status. */
internal class StatusBarNotificationListener : NotificationListenerService() {

    private val cache = HashMap<String, ImageBitmap>()

    override fun onListenerConnected() = refresh()

    override fun onListenerDisconnected() {
        cache.clear()
        NotificationRepository.set(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = Ranking()
        val seenPackages = HashSet<String>()
        val result = ArrayList<NotifIcon>()

        for (sbn in active.sortedByDescending { it.postTime }) {
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) continue

            val importance = if (currentRanking.getRanking(sbn.key, ranking)) {
                ranking.importance
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
            if (importance < NotificationManager.IMPORTANCE_LOW) continue
            if (!seenPackages.add(sbn.packageName)) continue

            val bitmap = bitmapFor(sbn) ?: continue
            result += NotifIcon(
                key = sbn.key,
                pkg = sbn.packageName,
                bitmap = bitmap,
                priority = importance >= NotificationManager.IMPORTANCE_DEFAULT
            )
        }

        cache.keys.retainAll(active.map { it.key }.toSet())
        NotificationRepository.set(result)
    }

    private fun bitmapFor(sbn: StatusBarNotification): ImageBitmap? {
        cache[sbn.key]?.let { return it }
        val icon = sbn.notification.smallIcon ?: return null
        val drawable = runCatching { icon.loadDrawable(this) }.getOrNull() ?: return null
        val size = (resources.displayMetrics.density * 16).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        val image = bitmap.asImageBitmap()
        cache[sbn.key] = image
        return image
    }
}
