package com.pixelexperience.forall.lockscreen.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/** Instantâneo da sessão de mídia ativa (dados reais do MediaController). */
@Immutable
data class MediaInfo(
    val key: String,
    val title: String,
    val artist: String,
    val appLabel: String?,
    val art: ImageBitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val updateTime: Long,
    val speed: Float,
    val durationMs: Long,
    val canPrev: Boolean,
    val canNext: Boolean,
    val canSeek: Boolean,
    val canFavorite: Boolean,
    val favorite: Boolean,
)

/**
 * Observa as sessões de mídia nativas (MediaSessionManager) usando o acesso a notificações que o app já tem.
 * Sem serviço próprio: registra os ouvintes só enquanto a lockscreen está composta (start/stop).
 * info == null  =>  não há sessão válida (ou o acesso a notificações não está concedido).
 */
@Stable
class LockMediaState internal constructor(private val ctx: Context) {

    var info by mutableStateOf<MediaInfo?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private var lastArtSrc: Bitmap? = null
    private var lastArt: ImageBitmap? = null
    private var lastPkg: String? = null
    private var lastLabel: String? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = reselect()
    }

    fun start() {
        val comp = listenerComponent(ctx) ?: return
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        manager = msm
        component = comp
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp, handler)
            pick(msm.getActiveSessions(comp))
        } catch (_: SecurityException) {
            info = null
        }
    }

    fun stop() {
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        controller?.let { runCatching { it.unregisterCallback(callback) } }
        controller = null
        manager = null
        component = null
        info = null
    }

    // ---- controles reais (MediaController.TransportControls) ----

    fun togglePlay() {
        val c = controller ?: return
        if (info?.playing == true) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun prev() {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms.coerceAtLeast(0L))
    }

    /** Só age quando a sessão expõe avaliação de coração (RATING_HEART + ACTION_SET_RATING). */
    fun toggleFavorite() {
        val c = controller ?: return
        val i = info ?: return
        if (!i.canFavorite) return
        c.transportControls.setRating(Rating.newHeartRating(!i.favorite))
    }

    // ---- seleção e leitura da sessão ----

    private fun titleOf(md: MediaMetadata?): String? =
        md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)

    private fun valid(c: MediaController): Boolean {
        val st = c.playbackState?.state ?: return false
        val ok = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_BUFFERING
        return ok && !titleOf(c.metadata).isNullOrBlank()
    }

    private fun reselect() {
        val m = manager ?: return
        val comp = component ?: return
        try {
            pick(m.getActiveSessions(comp))
        } catch (_: SecurityException) {
            info = null
        }
    }

    private fun pick(list: List<MediaController>?) {
        val l = list.orEmpty()
        val best = l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING && valid(it) }
            ?: l.firstOrNull { valid(it) }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.let { runCatching { it.unregisterCallback(callback) } }
            controller = best
            best?.registerCallback(callback, handler)
        }
        refresh()
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            info = null
            return
        }
        if (!valid(c)) {
            reselect()
            if (controller === c) info = null
            return
        }
        val md = c.metadata ?: return
        val st = c.playbackState ?: return
        val actions = st.actions
        val title = titleOf(md).orEmpty()
        val artist = (md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)).orEmpty()
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val rating = md.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
        val heart = rating != null && rating.ratingStyle == Rating.RATING_HEART &&
            (actions and PlaybackState.ACTION_SET_RATING) != 0L
        val playing = st.state == PlaybackState.STATE_PLAYING || st.state == PlaybackState.STATE_BUFFERING
        info = MediaInfo(
            key = "${c.packageName}|$title|$artist",
            title = title,
            artist = artist,
            appLabel = appLabelOf(c.packageName),
            art = artOf(md),
            playing = playing,
            positionMs = st.position.coerceAtLeast(0L),
            updateTime = st.lastPositionUpdateTime,
            speed = if (st.playbackSpeed > 0f) st.playbackSpeed else 1f,
            durationMs = duration,
            canPrev = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
            canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
            canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L && duration > 0L,
            canFavorite = heart,
            favorite = heart && rating?.hasHeart() == true,
        )
    }

    /** Capa real da sessão, reduzida (máx. 384 px) para poupar memória; reaproveita se for a mesma imagem. */
    private fun artOf(md: MediaMetadata): ImageBitmap? {
        val src = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        if (src === lastArtSrc) return lastArt
        val big = maxOf(src.width, src.height)
        val scaled = if (big > 384) {
            val s = 384f / big
            Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
        } else {
            src
        }
        lastArtSrc = src
        lastArt = scaled.asImageBitmap()
        return lastArt
    }

    private fun appLabelOf(pkg: String): String? {
        if (pkg == lastPkg) return lastLabel
        lastPkg = pkg
        lastLabel = runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
        return lastLabel
    }
}

/**
 * Componente do próprio app que está habilitado como ouvinte de notificações (lido de
 * enabled_notification_listeners, a mesma fonte do NotificationManagerCompat). Sem ele o Android
 * não libera as sessões de mídia, então o player simplesmente não aparece.
 */
private fun listenerComponent(ctx: Context): ComponentName? {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return null
    return flat.split(':')
        .mapNotNull { ComponentName.unflattenFromString(it) }
        .firstOrNull { it.packageName == ctx.packageName }
}

@Composable
fun rememberLockMedia(enabled: Boolean): LockMediaState {
    val ctx = LocalContext.current.applicationContext
    val state = remember { LockMediaState(ctx) }
    DisposableEffect(enabled) {
        if (enabled) state.start()
        onDispose { state.stop() }
    }
    return state
}
