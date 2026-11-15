package com.pixelexperience.forall.lockscreen.ui

import android.app.NotificationManager
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.notifications.LockAction
import com.pixelexperience.forall.lockscreen.notifications.LockNotification
import com.pixelexperience.forall.lockscreen.ui.components.GlassCard
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.lockFont
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign

private val OuterCorner = 28.dp
private val InnerCorner = 6.dp

private class Group(val id: String, val appName: String, val items: List<LockNotification>)

/** Linha da lista: um grupo de notificações ou o rótulo de uma seção. */
private class Entry(val key: String, val group: Group? = null, val header: String? = null)

private fun isPt() = Locale.getDefault().language == "pt"

/**
 * Central de notificações da tela de bloqueio.
 * - Seções: normais/prioritárias em cima, "Silenciosas" embaixo (pelo importance real do Android).
 * - Grupos por app: pilha recolhida com contador; expandida mostra cada notificação num card próprio.
 * - Cada card: deslizar para dispensar, expandir texto longo, imagem e ações.
 */
@Composable
fun NotificationsList(
    items: List<LockNotification>,
    grouped: Boolean,
    opacity: Float,
    tint: Color,
    accent: Color,
    preview: Boolean,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    onAction: (LockAction) -> Unit = {},
) {
    val entries = remember(items, grouped) {
        fun build(prefix: String, list: List<LockNotification>): List<Entry> =
            if (grouped) list.groupBy { it.packageName }.map { (pkg, l) ->
                Entry("$prefix:$pkg", Group("$prefix:$pkg", l.first().appName, l))
            } else list.map { Entry("$prefix:${it.key}", Group("$prefix:${it.key}", it.appName, listOf(it))) }

        val loud = items.filter { it.importance >= NotificationManager.IMPORTANCE_DEFAULT }
        val quiet = items.filter { it.importance < NotificationManager.IMPORTANCE_DEFAULT }
        buildList {
            addAll(build("a", loud))
            if (quiet.isNotEmpty()) {
                add(Entry("h:quiet", header = if (isPt()) "Silenciosas" else "Silent"))
                addAll(build("s", quiet))
            }
        }
    }
    LazyColumn(
        modifier = modifier.fadeBottom(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        userScrollEnabled = !preview,
    ) {
        items(entries, key = { it.key }) { e ->
            val h = e.header
            val g = e.group
            if (h != null) {
                SectionLabel(h, Modifier.animateItem())
            } else if (g != null) {
                GroupBlock(g, opacity, tint, accent, onOpen, onDismiss, onAction, Modifier.animateItem())
            }
        }
        if (items.any { it.clearable }) {
            item(key = "clear_all") {
                Box(Modifier.fillMaxWidth().animateItem(), contentAlignment = Alignment.CenterEnd) {
                    GlassCard(shape = RoundedCornerShape(50), tint = tint, opacity = opacity) {
                        Text(
                            if (isPt()) "Limpar tudo" else "Clear all",
                            modifier = Modifier.clickable { onClearAll() }.padding(horizontal = 20.dp, vertical = 12.dp),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = lockFont(600),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Esmaece suavemente o fim da lista, para nenhum card aparecer cortado de forma seca na borda. */
private fun Modifier.fadeBottom(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 0.94f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }

/** Deslizar para os lados dispensa; se o sistema não remover a notificação, o card volta ao lugar. */
private fun Modifier.swipeAway(enabled: Boolean, onDismissed: () -> Unit): Modifier = composed {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val latest by rememberUpdatedState(onDismissed)
    this
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.85f
        }
        .draggable(
            orientation = Orientation.Horizontal,
            enabled = enabled,
            state = rememberDraggableState { d ->
                scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(offset.value + d) }
            },
            onDragStopped = { v ->
                val moved = abs(offset.value) > 8f
                val far = abs(offset.value) > width * 0.35f
                val fast = abs(v) > 1400f && (!moved || sign(v) == sign(offset.value))
                if (far || fast) {
                    val dir = if (moved) sign(offset.value) else sign(v)
                    offset.animateTo(dir * width, tween(180))
                    latest()
                    delay(700)
                    offset.animateTo(0f, LockMotion.bouncy())
                } else {
                    offset.animateTo(0f, LockMotion.bouncy())
                }
            },
        )
}

@Composable
private fun GroupBlock(
    g: Group,
    opacity: Float,
    tint: Color,
    accent: Color,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onAction: (LockAction) -> Unit,
    modifier: Modifier,
) {
    val first = g.items.first()
    if (g.items.size == 1) {
        NotificationCard(
            n = first, topCorner = OuterCorner, bottomCorner = OuterCorner,
            opacity = opacity, tint = tint, accent = accent,
            onOpen = onOpen, dismissible = first.clearable, onDismiss = { onDismiss(first.key) }, onAction = onAction,
            modifier = modifier,
        )
        return
    }

    var expanded by rememberSaveable(g.id) { mutableStateOf(false) }
    val canDismissGroup = g.items.any { it.clearable }
    val dismissGroup = { g.items.filter { it.clearable }.forEach { onDismiss(it.key) } }

    AnimatedContent(
        targetState = expanded,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            fadeIn(LockMotion.effects()) togetherWith fadeOut(LockMotion.effects()) using
                SizeTransform(clip = true) { _, _ -> LockMotion.spatial() }
        },
        label = "group",
    ) { isExpanded ->
        if (!isExpanded) {
            // pilha recolhida: card mais recente com cartões "espiando" por baixo
            val peeks = (g.items.size - 1).coerceAtMost(2)
            Box(Modifier.fillMaxWidth().swipeAway(canDismissGroup, dismissGroup)) {
                if (peeks >= 2) {
                    GlassCard(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 26.dp).fillMaxWidth().height(40.dp),
                        RoundedCornerShape(OuterCorner), tint, opacity * 0.6f,
                    ) {}
                }
                GlassCard(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 13.dp).fillMaxWidth().height(48.dp),
                    RoundedCornerShape(OuterCorner), tint, opacity * 0.8f,
                ) {}
                NotificationCard(
                    n = first, topCorner = OuterCorner, bottomCorner = OuterCorner,
                    opacity = opacity, tint = tint, accent = accent,
                    onOpen = onOpen, dismissible = false, onDismiss = {}, onAction = onAction,
                    modifier = Modifier.padding(bottom = (peeks * 8).dp),
                    groupCount = g.items.size, onGroupToggle = { expanded = true },
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GroupHeader(g, accent, canDismissGroup, dismissGroup) { expanded = false }
                g.items.forEachIndexed { i, n ->
                    key(n.key) {
                        NotificationCard(
                            n = n,
                            topCorner = if (i == 0) OuterCorner else InnerCorner,
                            bottomCorner = if (i == g.items.lastIndex) OuterCorner else InnerCorner,
                            opacity = opacity, tint = tint, accent = accent,
                            onOpen = onOpen, dismissible = n.clearable, onDismiss = { onDismiss(n.key) }, onAction = onAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(g: Group, accent: Color, canDismiss: Boolean, onDismissAll: () -> Unit, onCollapse: () -> Unit) {
    val sh = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 8.dp, end = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(g.items.first(), 24.dp, accent)
        Spacer(Modifier.width(8.dp))
        Text(
            g.appName, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = lockFont(600), shadow = sh),
        )
        Spacer(Modifier.width(8.dp))
        CountPill(g.items.size, accent)
        Spacer(Modifier.weight(1f))
        if (canDismiss) {
            Text(
                if (isPt()) "Limpar" else "Clear",
                modifier = Modifier.clip(CircleShape).clickable { onDismissAll() }.padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = lockFont(600), shadow = sh),
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
        }
        ChevronButton(rotation = 180f, description = if (isPt()) "Recolher" else "Collapse", onClick = onCollapse)
    }
}

@Composable
private fun NotificationCard(
    n: LockNotification,
    topCorner: Dp,
    bottomCorner: Dp,
    opacity: Float,
    tint: Color,
    accent: Color,
    onOpen: (LockNotification) -> Unit,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    onAction: (LockAction) -> Unit,
    modifier: Modifier = Modifier,
    groupCount: Int = 0,
    onGroupToggle: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var textExpanded by rememberSaveable(n.key) { mutableStateOf(false) }
    val silent = n.importance < NotificationManager.IMPORTANCE_DEFAULT
    val priority = n.importance >= NotificationManager.IMPORTANCE_HIGH
    val longContent = !n.hidden && (
        n.bigText.length > n.text.length + 3 || n.text.length > 70 || n.picture != null || n.actions.isNotEmpty()
        )
    val showChevron = onGroupToggle != null || longContent
    val expandedText = textExpanded && onGroupToggle == null

    val top by animateDpAsState(topCorner, LockMotion.fastSpatial(), label = "top")
    val bottom by animateDpAsState(bottomCorner, LockMotion.fastSpatial(), label = "bottom")
    val shape = RoundedCornerShape(
        topStart = top.coerceAtLeast(0.dp), topEnd = top.coerceAtLeast(0.dp),
        bottomEnd = bottom.coerceAtLeast(0.dp), bottomStart = bottom.coerceAtLeast(0.dp),
    )
    val rot by animateFloatAsState(if (expandedText) 180f else 0f, LockMotion.spatial(), label = "chevron")

    // prioritária: tom do accent; silenciosa: um pouco mais discreta
    val cardTint = if (priority) lerp(tint, accent, 0.22f) else tint
    val cardOpacity = if (silent) opacity * 0.82f else opacity
    val bodyAlpha = if (silent) 0.66f else 0.80f

    GlassCard(
        modifier = modifier.fillMaxWidth().swipeAway(dismissible, onDismiss),
        shape = shape, tint = cardTint, opacity = cardOpacity,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { onOpen(n) }
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)
                .animateContentSize(LockMotion.spatial()),
        ) {
            Column(Modifier.weight(1f)) {
                // linha do app: ícone · nome · contador · horário
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(n, 20.dp, accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        n.appName, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp,
                        fontFamily = lockFont(600), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (groupCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        CountPill(groupCount, accent)
                    }
                    Text(
                        "  ·  " + relTime(ctx, n.postTime), color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp,
                        fontFamily = lockFont(400), maxLines = 1, softWrap = false,
                    )
                }
                if (n.hidden) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = bodyAlpha), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            n.text, color = Color.White.copy(alpha = bodyAlpha), fontSize = 14.sp,
                            fontFamily = lockFont(400), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    if (n.title.isNotBlank()) {
                        Text(
                            n.title, color = Color.White, fontSize = 15.sp, fontFamily = lockFont(600),
                            maxLines = if (expandedText) 2 else 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    val body = if (expandedText && n.bigText.isNotBlank()) n.bigText else n.text
                    if (body.isNotBlank()) {
                        Text(
                            body, color = Color.White.copy(alpha = bodyAlpha), fontSize = 14.sp, lineHeight = 20.sp,
                            fontFamily = lockFont(400), maxLines = if (expandedText) 8 else 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        )
                    }
                    val pic = n.picture
                    if (expandedText && pic != null) {
                        val ratio = (pic.width.toFloat() / pic.height.coerceAtLeast(1)).coerceIn(1f, 2.2f)
                        Image(
                            bitmap = pic, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).aspectRatio(ratio).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    if (expandedText && n.actions.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            n.actions.forEach { a ->
                                Box(
                                    Modifier.weight(1f).heightIn(min = 36.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable { onAction(a) }.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        a.title, color = Color.White, fontSize = 13.sp, fontFamily = lockFont(600),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                ChevronButton(
                    rotation = rot,
                    description = if (expandedText) (if (isPt()) "Recolher" else "Collapse") else (if (isPt()) "Expandir" else "Expand"),
                    onClick = { if (onGroupToggle != null) onGroupToggle() else textExpanded = !textExpanded },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 0.dp),
        style = TextStyle(
            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontFamily = lockFont(600),
            shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f),
        ),
    )
}

@Composable
private fun CountPill(count: Int, accent: Color) {
    Box(
        Modifier.clip(CircleShape).background(accent.copy(alpha = 0.30f)).padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = Color.White, fontSize = 11.sp, fontFamily = lockFont(700), maxLines = 1)
    }
}

@Composable
private fun ChevronButton(rotation: Float, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown, contentDescription = description, tint = Color.White,
            modifier = Modifier.size(22.dp).rotate(rotation),
        )
    }
}

private fun relTime(ctx: Context, t: Long): String {
    val d = System.currentTimeMillis() - t
    return when {
        d < 60_000L -> if (isPt()) "Agora" else "Now"
        d < 3_600_000L -> "${d / 60_000L} min"
        else -> DateFormat.getTimeFormat(ctx).format(Date(t))
    }
}

@Composable
private fun AppIcon(n: LockNotification, size: Dp, accent: Color) {
    val bg = if (n.color != 0) Color(n.color or 0xFF000000.toInt()) else accent.copy(alpha = 0.6f)
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val icon = n.icon
        if (icon != null) {
            Image(icon, null, Modifier.size(size * 0.62f), colorFilter = ColorFilter.tint(Color.White))
        } else {
            Text(n.appName.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
