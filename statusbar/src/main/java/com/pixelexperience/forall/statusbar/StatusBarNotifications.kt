package com.pixelexperience.forall.statusbar

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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

    /**
     * Desenha o ícone, recorta no contorno real do desenho e centraliza num quadrado de 16dp.
     * Assim todos ficam com o mesmo tamanho visual e alinhados com o relógio e os indicadores,
     * mesmo quando o app deixa margem desigual dentro do próprio ícone.
     */
    private fun bitmapFor(sbn: StatusBarNotification): ImageBitmap? {
        cache[sbn.key]?.let { return it }
        val icon = sbn.notification.smallIcon ?: return null
        val drawable = runCatching { icon.loadDrawable(this) }.getOrNull() ?: return null
        val density = resources.displayMetrics.density
        val size = (density * 16).toInt().coerceAtLeast(16)
        val big = size * 3

        val src = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, big, big)
        drawable.draw(Canvas(src))

        val px = IntArray(big * big)
        src.getPixels(px, 0, big, 0, 0, big, big)
        var minX = big
        var minY = big
        var maxX = -1
        var maxY = -1
        for (y in 0 until big) {
            for (x in 0 until big) {
                if ((px[y * big + x] ushr 24) > 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) {
            src.recycle()
            return null
        }

        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val scale = size * 0.92f / maxOf(bw, bh)
        val dw = bw * scale
        val dh = bh * scale
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            Rect(minX, minY, maxX + 1, maxY + 1),
            RectF((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        src.recycle()

        val image = out.asImageBitmap()
        cache[sbn.key] = image
        return image
    }
}
