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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
private val CardGap = 4.dp

private class Group(val id: String, val appName: String, val items: List<LockNotification>)

/** Linha da lista: grupo de notificações, rótulo de seção ou a pílula de ícones das silenciosas. */
private class Entry(
    val key: String,
    val group: Group? = null,
    val header: String? = null,
    val pill: Boolean = false,
    val first: Boolean = false,
    val last: Boolean = false,
)

/** Cores sólidas e opacas dos cartões, derivadas do tom (surfaceContainerHigh) e do accent da lockscreen. */
private class Pal(val tint: Color, val accent: Color) {
    val onCard: Color = if (tint.luminance() > 0.5f) Color(0xFF1C1B1F) else Color(0xFFE6E1E5)
    val stack1: Color = lerp(tint, Color.Black, 0.10f)
    val stack2: Color = lerp(tint, Color.Black, 0.20f)
    fun card(priority: Boolean): Color = tint
}

private fun isPt() = Locale.getDefault().language == "pt"

/** Cartão sólido: cor opaca, sem blur, sem transparência, sem degradê. */
@Composable
private fun SolidCard(modifier: Modifier, shape: Shape, color: Color, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(shape).background(color, shape), content = content)
}

/**
 * Central de notificações da tela de bloqueio (estilo Pixel / Android 17).
 * - Cartões sólidos e colados (4dp): só o topo do primeiro e o fim do último têm o canto grande.
 * - Grupos por app: pilha recolhida com contador; expandida mostra cada notificação num cartão próprio.
 * - Silenciosas: pílula com ícones dos apps; ao tocar abre a seção.
 * - Cada cartão: deslizar para dispensar, expandir texto longo, imagem e ações.
 * (opacity é mantido só por compatibilidade com a chamada atual e não é mais usado.)
 */
@Composable
fun NotificationsList(
    items: List<LockNotification>,
    grouped: Boolean,
    @Suppress("UNUSED_PARAMETER") opacity: Float,
    tint: Color,
    accent: Color,
    preview: Boolean,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    onAction: (LockAction) -> Unit = {},
) {
    val pal = remember(tint, accent) { Pal(tint, accent) }
    var showQuiet by rememberSaveable { mutableStateOf(false) }
    val quiet = remember(items) { items.filter { it.importance < NotificationManager.IMPORTANCE_DEFAULT } }
    val quietApps = remember(quiet) { quiet.distinctBy { it.packageName } }

    val entries = remember(items, grouped, showQuiet) {
        fun build(prefix: String, list: List<LockNotification>): List<Entry> {
            val groups: List<Group> =
                if (grouped) list.groupBy { it.packageName }.map { (pkg, l) -> Group("$prefix:$pkg", l.first().appName, l) }
                else list.map { Group("$prefix:${it.key}", it.appName, listOf(it)) }
            return groups.mapIndexed { i, g -> Entry(g.id, g, first = i == 0, last = i == groups.lastIndex) }
        }

        val loud = items.filter { it.importance >= NotificationManager.IMPORTANCE_DEFAULT }
        buildList {
            addAll(build("a", loud))
            if (quiet.isNotEmpty()) {
                if (showQuiet) {
                    add(Entry("h:quiet", header = if (isPt()) "Silenciosas" else "Silent"))
                    addAll(build("s", quiet))
                } else {
                    add(Entry("pill:quiet", pill = true))
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CardGap),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        userScrollEnabled = !preview,
    ) {
        items(entries, key = { it.key }) { e ->
            val h = e.header
            val g = e.group
            when {
                e.pill -> QuietPill(quietApps, pal, Modifier.animateItem()) { showQuiet = true }
                h != null -> SectionLabel(h, pal, Modifier.animateItem()) { showQuiet = false }
                g != null -> GroupBlock(g, pal, e.first, e.last, onOpen, onDismiss, onAction, Modifier.animateItem())
            }
        }
        if (items.any { it.clearable }) {
            item(key = "clear_all") {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).animateItem(), contentAlignment = Alignment.Center) {
                    SolidCard(Modifier, RoundedCornerShape(50), pal.tint) {
                        Text(
                            if (isPt()) "Limpar tudo" else "Clear all",
                            modifier = Modifier.clickable { onClearAll() }.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = pal.onCard,
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
    pal: Pal,
    first: Boolean,
    last: Boolean,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onAction: (LockAction) -> Unit,
    modifier: Modifier,
) {
    val firstN = g.items.first()
    val topC = if (first) OuterCorner else InnerCorner
    val bottomC = if (last) OuterCorner else InnerCorner

    if (g.items.size == 1) {
        NotificationCard(
            n = firstN, topCorner = topC, bottomCorner = bottomC, pal = pal,
            onOpen = onOpen, dismissible = firstN.clearable, onDismiss = { onDismiss(firstN.key) }, onAction = onAction,
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
            // pilha recolhida: cartão mais recente com cartões menores, mais escuros, "espiando" por baixo
            val peeks = (g.items.size - 1).coerceAtMost(2)
            Box(Modifier.fillMaxWidth().swipeAway(canDismissGroup, dismissGroup)) {
                if (peeks >= 2) {
                    SolidCard(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 26.dp).fillMaxWidth().height(40.dp),
                        RoundedCornerShape(OuterCorner), pal.stack2,
                    ) {}
                }
                SolidCard(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 13.dp).fillMaxWidth().height(48.dp),
                    RoundedCornerShape(OuterCorner), pal.stack1,
                ) {}
                NotificationCard(
                    n = firstN, topCorner = topC, bottomCorner = OuterCorner, pal = pal,
                    onOpen = onOpen, dismissible = false, onDismiss = {}, onAction = onAction,
                    modifier = Modifier.padding(bottom = (peeks * 8).dp),
                    groupCount = g.items.size, onGroupToggle = { expanded = true },
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CardGap)) {
                GroupHeader(g, pal, canDismissGroup, dismissGroup) { expanded = false }
                g.items.forEachIndexed { i, n ->
                    key(n.key) {
                        NotificationCard(
                            n = n,
                            topCorner = if (i == 0 && first) OuterCorner else InnerCorner,
                            bottomCorner = if (i == g.items.lastIndex && last) OuterCorner else InnerCorner,
                            pal = pal,
                            onOpen = onOpen, dismissible = n.clearable, onDismiss = { onDismiss(n.key) }, onAction = onAction,
                        )
                    }
                }
            }
        }
    }
}

/** Cabeçalho do grupo expandido: ícone, "App · N", botão Limpar e seta de recolher. */
@Composable
private fun GroupHeader(g: Group, pal: Pal, canDismiss: Boolean, onDismissAll: () -> Unit, onCollapse: () -> Unit) {
    val sh = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 1f), 12f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 8.dp, end = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(g.items.first(), 24.dp, pal)
        Spacer(Modifier.width(8.dp))
        Text(
            "${g.appName} · ${g.items.size}", modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = lockFont(600), shadow = sh),
        )
        Spacer(Modifier.weight(1f))
        if (canDismiss) {
            SolidCard(Modifier, RoundedCornerShape(50), pal.tint) {
                Text(
                    if (isPt()) "Limpar" else "Clear",
                    modifier = Modifier.clickable { onDismissAll() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = pal.onCard, fontSize = 13.sp, fontFamily = lockFont(600), maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        ChevronButton(
            rotation = 180f, description = if (isPt()) "Recolher" else "Collapse",
            bg = pal.tint, fg = pal.onCard, onClick = onCollapse,
        )
    }
}

@Composable
private fun NotificationCard(
    n: LockNotification,
    topCorner: Dp,
    bottomCorner: Dp,
    pal: Pal,
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

    // cores sólidas: prioritária puxa o accent; os textos secundários são misturas opacas (sem alpha)
    val cardColor = pal.card(priority)
    val main = pal.onCard
    val appNameColor = lerp(main, cardColor, 0.14f)
    val timeColor = lerp(main, cardColor, 0.42f)
    val bodyColor = lerp(main, cardColor, if (silent) 0.40f else 0.22f)
    val chipColor = lerp(cardColor, main, 0.14f)

    SolidCard(
        modifier = modifier.fillMaxWidth().swipeAway(dismissible, onDismiss)
            .shadow(2.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f)),
        shape = shape, color = cardColor,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { onOpen(n) }
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)
                .animateContentSize(LockMotion.spatial()),
        ) {
            Column(Modifier.weight(1f)) {
                // linha do app: ícone redondo · nome · contador · tempo
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(n, 20.dp, pal)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        n.appName, color = appNameColor, fontSize = 12.sp,
                        fontFamily = lockFont(600), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (groupCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        CountPill(groupCount, chipColor, main)
                    }
                    Text(
                        "  ·  " + relTime(ctx, n.postTime), color = timeColor, fontSize = 12.sp,
                        fontFamily = lockFont(400), maxLines = 1, softWrap = false,
                    )
                }
                if (n.hidden) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = bodyColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            n.text, color = bodyColor, fontSize = 14.sp,
                            fontFamily = lockFont(400), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    if (n.title.isNotBlank()) {
                        Text(
                            n.title, color = main, fontSize = 15.sp, fontFamily = lockFont(600),
                            maxLines = if (expandedText) 2 else 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    val body = if (expandedText && n.bigText.isNotBlank()) n.bigText else n.text
                    if (body.isNotBlank()) {
                        Text(
                            body, color = bodyColor, fontSize = 14.sp, lineHeight = 20.sp,
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
                                        .background(chipColor)
                                        .clickable { onAction(a) }.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        a.title, color = main, fontSize = 13.sp, fontFamily = lockFont(600),
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
                    bg = chipColor, fg = main,
                    onClick = { if (onGroupToggle != null) onGroupToggle() else textExpanded = !textExpanded },
                )
            }
        }
    }
}

/** Rótulo "Silenciosas" com seta para recolher de volta para a pílula de ícones. */
@Composable
private fun SectionLabel(text: String, pal: Pal, modifier: Modifier = Modifier, onCollapse: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, modifier = Modifier.weight(1f),
            style = TextStyle(
                color = Color.White, fontSize = 13.sp, fontFamily = lockFont(600),
                shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 1f), 12f),
            ),
        )
        ChevronButton(
            rotation = 180f, description = if (isPt()) "Recolher" else "Collapse",
            bg = pal.tint, fg = pal.onCard, onClick = onCollapse,
        )
    }
}

/** Pílula sólida com os ícones dos apps das notificações silenciosas (como no Pixel). */
@Composable
private fun QuietPill(apps: List<LockNotification>, pal: Pal, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(top = 8.dp)) {
        SolidCard(Modifier.align(Alignment.CenterStart), RoundedCornerShape(50), pal.tint) {
            Row(
                Modifier.clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                apps.take(3).forEach { n ->
                    val icon = n.icon
                    if (icon != null) {
                        Image(icon, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(pal.onCard))
                    } else {
                        Text(n.appName.take(1).uppercase(), color = pal.onCard, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (apps.size > 3) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(pal.onCard))
                }
            }
        }
    }
}

@Composable
private fun CountPill(count: Int, bg: Color, fg: Color) {
    Box(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = fg, fontSize = 11.sp, fontFamily = lockFont(700), maxLines = 1)
    }
}

@Composable
private fun ChevronButton(rotation: Float, description: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown, contentDescription = description, tint = fg,
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
private fun AppIcon(n: LockNotification, size: Dp, pal: Pal) {
    val bg = if (n.color != 0) Color(n.color or 0xFF000000.toInt()) else pal.accent
    val glyph = if (bg.luminance() > 0.55f) Color(0xFF1C1B1F) else Color.White
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val icon = n.icon
        if (icon != null) {
            Image(icon, null, Modifier.size(size * 0.62f), colorFilter = ColorFilter.tint(glyph))
        } else {
            Text(n.appName.take(1).uppercase(), color = glyph, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
