package com.pixelexperience.forall.lockscreen.ui

import android.app.NotificationManager
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.notifications.LockAction
import com.pixelexperience.forall.lockscreen.notifications.LockNotification
import com.pixelexperience.forall.lockscreen.ui.components.GlassCard
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.lockColorScheme
import com.pixelexperience.forall.lockscreen.ui.theme.UiFont
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

private val OuterCorner = 28.dp
private val InnerCorner = 6.dp
private val CardGap = 3.dp

/** Folga lateral da area da lista: os cartoes nao sao cortados ao se mover (a lista fica mais larga que os cartoes). */
private val SideRoom = 12.dp

/** Nao nulo = estilo vidro (valor = opacidade do tint). Nulo = cartoes solidos. */
private val LocalGlassOpacity = staticCompositionLocalOf<Float?> { null }

private class Group(val id: String, val appName: String, val items: List<LockNotification>)

/** Linha da lista: grupo de notificacoes, rotulo de secao ou a pilula de icones das silenciosas. */
private class Entry(
    val key: String,
    val group: Group? = null,
    val header: String? = null,
    val pill: Boolean = false,
    val first: Boolean = false,
    val last: Boolean = false,
)

/** Estado compartilhado do arrasto: o item arrastado move os vizinhos em onda (M3 Expressive). */
private class SwipeChain {
    var index by mutableIntStateOf(-1)
    val x = Animatable(0f)
}

/** Papeis de cor Material You dos cartoes. */
private class Pal(val tint: Color, val accent: Color, sc: ColorScheme) {
    val onCard: Color = sc.onSurface
    val onCardVariant: Color = sc.onSurfaceVariant
    val onAccent: Color = sc.onPrimary
    val chip: Color = sc.surfaceContainerHighest
    val clearBg: Color = sc.primaryContainer
    val onClear: Color = sc.onPrimaryContainer
    private val prio: Color = sc.secondaryContainer
    private val onPrio: Color = sc.onSecondaryContainer
    fun card(priority: Boolean): Color = if (priority) prio else tint
    fun textOn(priority: Boolean): Color = if (priority) onPrio else onCard
    fun variantOn(priority: Boolean): Color = if (priority) lerp(onPrio, prio, 0.25f) else onCardVariant
    fun chipOn(priority: Boolean): Color = if (priority) lerp(prio, onPrio, 0.14f) else chip
}

private fun isPt() = Locale.getDefault().language == "pt"

/** Cartao solido: cor opaca (ou vidro, quando ativado nas configuracoes). */
@Composable
private fun SolidCard(modifier: Modifier, shape: Shape, color: Color, content: @Composable BoxScope.() -> Unit) {
    val glass = LocalGlassOpacity.current
    if (glass != null) {
        GlassCard(modifier = modifier, shape = shape, tint = color, opacity = glass, content = content)
    } else {
        Box(modifier.clip(shape).background(color, shape), content = content)
    }
}

@Composable
private fun rememberCardShape(topCorner: Dp, bottomCorner: Dp): Shape {
    val top by animateDpAsState(topCorner, LockMotion.fastSpatial(), label = "top")
    val bottom by animateDpAsState(bottomCorner, LockMotion.fastSpatial(), label = "bottom")
    return RoundedCornerShape(
        topStart = top.coerceAtLeast(0.dp), topEnd = top.coerceAtLeast(0.dp),
        bottomEnd = bottom.coerceAtLeast(0.dp), bottomStart = bottom.coerceAtLeast(0.dp),
    )
}

/**
 * Central de notificacoes da tela de bloqueio (estilo Android 14 / Pixel + movimento Material 3 Expressive).
 * - Lista segmentada: cartoes colados, so as pontas tem canto grande.
 * - Ao deslizar um item, os vizinhos acompanham em onda e os cantos se transformam; ao soltar, mola.
 * - Grupo: um cartao com cabecalho (icone, app, contador) e uma linha por notificacao.
 * - Silenciosas: pilula de icones; ao abrir, secao com X. Rodape: Gerenciar e Limpar tudo.
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
    glass: Boolean = false,
    scheme: ColorScheme? = null,
    onManage: () -> Unit = {},
) {
    val pal = remember(tint, accent, scheme) {
        Pal(tint, accent, scheme ?: lockColorScheme(accent, tint.luminance() <= 0.5f))
    }
    var showQuiet by rememberSaveable { mutableStateOf(false) }
    val quiet = remember(items) { items.filter { it.importance < NotificationManager.IMPORTANCE_DEFAULT } }
    val quietApps = remember(quiet) { quiet.distinctBy { it.packageName } }
    val chain = remember { SwipeChain() }
    var clearing by remember { mutableStateOf(false) }

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

    // Limpar tudo em cascata: cada cartao desliza para fora em sequencia e so depois o sistema limpa
    LaunchedEffect(clearing) {
        if (clearing) {
            delay(min(entries.size, 8) * 45L + 280L)
            onClearAll()
            clearing = false
        }
    }

    CompositionLocalProvider(LocalGlassOpacity provides (if (glass) opacity else null)) {
        LazyColumn(
            modifier = modifier.widenSides(SideRoom).fadeEdges(),
            verticalArrangement = Arrangement.spacedBy(CardGap),
            contentPadding = PaddingValues(start = SideRoom, end = SideRoom, top = 12.dp, bottom = 28.dp),
            userScrollEnabled = !preview,
        ) {
            itemsIndexed(entries, key = { _, e -> e.key }) { idx, e ->
                val h = e.header
                val g = e.group
                val canClear = g?.items?.any { it.clearable } == true
                val fx = Modifier
                    .animateItem(
                        fadeInSpec = LockMotion.effects<Float>(),
                        placementSpec = LockMotion.spatial<IntOffset>(),
                        fadeOutSpec = LockMotion.effects<Float>(),
                    )
                    .chainWave(chain, idx)
                    .clearSlide(clearing && canClear, min(idx, 8))
                when {
                    e.pill -> QuietPill(quietApps, pal, fx) { showQuiet = true }
                    h != null -> SectionLabel(h, pal, fx) { showQuiet = false }
                    g != null -> GroupBlock(g, pal, idx, e.first, e.last, chain, onOpen, onDismiss, onAction, fx)
                }
            }
            if (items.isNotEmpty()) {
                item(key = "footer") {
                    ListFooter(
                        pal = pal,
                        showClear = items.any { it.clearable },
                        onManage = onManage,
                        onClear = { clearing = true },
                        modifier = Modifier.animateItem(
                            fadeInSpec = LockMotion.effects<Float>(),
                            placementSpec = LockMotion.spatial<IntOffset>(),
                            fadeOutSpec = LockMotion.effects<Float>(),
                        ),
                    )
                }
            }
        }
    }
}

/** Aumenta a area (hitbox/clip) da lista nos 2 lados sem mudar a posicao nem a largura dos cartoes. */
private fun Modifier.widenSides(room: Dp): Modifier = layout { measurable, constraints ->
    val r = room.roundToPx()
    val p = measurable.measure(constraints.offset(horizontal = 2 * r))
    layout(constraints.maxWidth, p.height) { p.place(-r, 0) }
}

/** Esmaece suavemente as bordas superior e inferior da lista (sem corte abrupto). */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.02f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Onda: os itens vizinhos acompanham o arrasto com intensidade que cai com a distancia. */
private fun Modifier.chainWave(chain: SwipeChain, myIndex: Int): Modifier = graphicsLayer {
    val i = chain.index
    if (i >= 0 && i != myIndex) {
        val d = abs(myIndex - i)
        val f = (0.55f - 0.12f * d).coerceAtLeast(0f)
        val lim = SideRoom.toPx()
        translationX = (chain.x.value * f).coerceIn(-lim, lim)
    }
}

/** Limpar tudo: o cartao espera a sua vez (order) e desliza para fora. */
private fun Modifier.clearSlide(clearing: Boolean, order: Int): Modifier = composed {
    val x = remember { Animatable(0f) }
    var w by remember { mutableIntStateOf(1) }
    LaunchedEffect(clearing) {
        if (clearing) {
            delay(order * 45L)
            x.animateTo(w.toFloat(), tween(220))
        } else if (x.value != 0f) {
            x.animateTo(0f, LockMotion.spatial())
        }
    }
    this
        .onSizeChanged { w = it.width.coerceAtLeast(1) }
        .graphicsLayer {
            translationX = x.value
            alpha = 1f - (x.value / w).coerceIn(0f, 1f)
        }
}

/** Deslizar para os lados dispensa; se o sistema nao remover a notificacao, o card volta ao lugar. */
private fun Modifier.swipeAway(enabled: Boolean, chain: SwipeChain?, index: Int, onDismissed: () -> Unit): Modifier = composed {
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
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offset.snapTo(offset.value + d)
                    if (chain != null) {
                        chain.index = index
                        chain.x.snapTo(offset.value)
                    }
                }
            },
            onDragStopped = { v ->
                val moved = abs(offset.value) > 8f
                val far = abs(offset.value) > width * 0.35f
                val fast = abs(v) > 1400f && (!moved || sign(v) == sign(offset.value))
                if (chain != null) scope.launch { chain.x.animateTo(0f, LockMotion.bouncy()) }
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
    index: Int,
    first: Boolean,
    last: Boolean,
    chain: SwipeChain,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onAction: (LockAction) -> Unit,
    modifier: Modifier,
) {
    // vizinhos do item arrastado "soltam" da lista: o canto voltado para ele fica grande
    val topC by remember(first, index, chain) {
        derivedStateOf {
            val d = chain.index >= 0 && abs(chain.x.value) > 8f
            if (first || (d && (index == chain.index || index == chain.index + 1))) OuterCorner else InnerCorner
        }
    }
    val bottomC by remember(last, index, chain) {
        derivedStateOf {
            val d = chain.index >= 0 && abs(chain.x.value) > 8f
            if (last || (d && (index == chain.index || index == chain.index - 1))) OuterCorner else InnerCorner
        }
    }
    val firstN = g.items.first()

    if (g.items.size == 1) {
        NotificationCard(
            n = firstN, topCorner = topC, bottomCorner = bottomC, pal = pal,
            onOpen = onOpen, dismissible = firstN.clearable, onDismiss = { onDismiss(firstN.key) }, onAction = onAction,
            modifier = modifier, chain = chain, index = index,
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
                SizeTransform(clip = false) { _, _ -> LockMotion.spatial() }
        },
        label = "group",
    ) { isExpanded ->
        if (!isExpanded) {
            GroupSummaryCard(
                g = g, topCorner = topC, bottomCorner = bottomC, pal = pal, chain = chain, index = index,
                canDismiss = canDismissGroup, onDismissAll = dismissGroup, onExpand = { expanded = true },
            )
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
                            chain = chain, index = index,
                        )
                    }
                }
            }
        }
    }
}

/** Grupo recolhido (Android 14): cabecalho com icone, app e contador, mais uma linha "Titulo texto" por notificacao. */
@Composable
private fun GroupSummaryCard(
    g: Group,
    topCorner: Dp,
    bottomCorner: Dp,
    pal: Pal,
    chain: SwipeChain,
    index: Int,
    canDismiss: Boolean,
    onDismissAll: () -> Unit,
    onExpand: () -> Unit,
) {
    val shape = rememberCardShape(topCorner, bottomCorner)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val main = pal.onCard
    val sub = pal.onCardVariant
    val bg by animateColorAsState(if (pressed) lerp(pal.tint, main, 0.10f) else pal.tint, LockMotion.effects(), label = "press")
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, LockMotion.fastSpatial(), label = "pressScale")
    val head = g.items.first()

    SolidCard(
        Modifier.fillMaxWidth().swipeAway(canDismiss, chain, index, onDismissAll).graphicsLayer { scaleX = scale; scaleY = scale },
        shape, bg,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(interactionSource = src, indication = null) { onExpand() }
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(head, 24.dp, pal)
                Spacer(Modifier.width(10.dp))
                Text(
                    g.appName, color = sub, fontSize = 13.sp, fontFamily = UiFont,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                CountChevronPill(g.items.size, 0f, pal.chip, main, onExpand)
            }
            g.items.take(3).forEach { n ->
                Text(
                    text = buildAnnotatedString {
                        if (!n.hidden && n.title.isNotBlank()) {
                            withStyle(SpanStyle(fontFamily = UiFont, color = main)) { append(n.title) }
                            append("  ")
                        }
                        append(n.text)
                    },
                    color = sub, fontSize = 14.sp, fontFamily = UiFont,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (g.items.size > 3) {
                Text(
                    "+${g.items.size - 3}", color = sub, fontSize = 12.sp, fontFamily = UiFont,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Cabecalho do grupo expandido: icone, "App - N", botao Limpar e seta de recolher. */
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
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = UiFont, shadow = sh),
        )
        Spacer(Modifier.weight(1f))
        if (canDismiss) {
            SolidCard(Modifier, RoundedCornerShape(50), pal.clearBg) {
                Text(
                    if (isPt()) "Limpar" else "Clear",
                    modifier = Modifier.clickable { onDismissAll() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = pal.onClear, fontSize = 13.sp, fontFamily = UiFont, maxLines = 1,
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
    chain: SwipeChain? = null,
    index: Int = -1,
) {
    val ctx = LocalContext.current
    var textExpanded by rememberSaveable(n.key) { mutableStateOf(false) }
    val priority = n.importance >= NotificationManager.IMPORTANCE_HIGH
    val longContent = !n.hidden && (
        n.bigText.length > n.text.length + 3 || n.text.length > 70 || n.picture != null || n.actions.isNotEmpty()
        )
    val shape = rememberCardShape(topCorner, bottomCorner)
    val rot by animateFloatAsState(if (textExpanded) 180f else 0f, LockMotion.spatial(), label = "chevron")

    val cardColor = pal.card(priority)
    val main = pal.textOn(priority)
    val sub = pal.variantOn(priority)
    val chipColor = pal.chipOn(priority)

    // realce ao tocar: o cartao clareia e encolhe um pouco, com mola
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) lerp(cardColor, main, 0.10f) else cardColor, LockMotion.effects(), label = "press")
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, LockMotion.fastSpatial(), label = "pressScale")

    val pic = n.picture
    val thumb = pic != null && !textExpanded && !n.hidden

    SolidCard(
        modifier = modifier.fillMaxWidth()
            .swipeAway(dismissible, chain, index, onDismiss)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = shape, color = bg,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(interactionSource = src, indication = null) { onOpen(n) }
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)
                .animateContentSize(LockMotion.spatial()),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(n, 24.dp, pal)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (textExpanded) {
                    Text(
                        n.appName, color = sub, fontSize = 12.sp, fontFamily = UiFont,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.title.ifBlank { n.appName }, color = main, fontSize = 15.sp, fontFamily = UiFont,
                        maxLines = if (textExpanded) 2 else 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "  ·  " + relTime(ctx, n.postTime), color = sub, fontSize = 12.sp,
                        fontFamily = UiFont, maxLines = 1, softWrap = false,
                    )
                }
                if (n.hidden) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = sub, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            n.text, color = sub, fontSize = 14.sp,
                            fontFamily = UiFont, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    val body = if (textExpanded && n.bigText.isNotBlank()) n.bigText else n.text
                    if (body.isNotBlank()) {
                        Text(
                            body, color = sub, fontSize = 14.sp, lineHeight = 20.sp,
                            fontFamily = UiFont, maxLines = if (textExpanded) 8 else 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        )
                    }
                    if (textExpanded && pic != null) {
                        val ratio = (pic.width.toFloat() / pic.height.coerceAtLeast(1)).coerceIn(1f, 2.2f)
                        Image(
                            bitmap = pic, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).aspectRatio(ratio).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    if (textExpanded && n.actions.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            n.actions.forEach { a ->
                                Box(
                                    Modifier.weight(1f).heightIn(min = 36.dp).clip(CircleShape)
                                        .background(chipColor)
                                        .clickable { onAction(a) }.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        a.title, color = main, fontSize = 13.sp, fontFamily = UiFont,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (thumb && pic != null) {
                Spacer(Modifier.width(10.dp))
                Image(
                    bitmap = pic, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)),
                )
            }
            if (longContent) {
                Spacer(Modifier.width(8.dp))
                ChevronButton(
                    rotation = rot,
                    description = if (textExpanded) (if (isPt()) "Recolher" else "Collapse") else (if (isPt()) "Expandir" else "Expand"),
                    bg = chipColor, fg = main,
                    onClick = { textExpanded = !textExpanded },
                )
            }
        }
    }
}

/** Rotulo "Silenciosas" com X para voltar a pilula de icones. */
@Composable
private fun SectionLabel(text: String, pal: Pal, modifier: Modifier = Modifier, onCollapse: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, modifier = Modifier.weight(1f),
            style = TextStyle(
                color = Color.White, fontSize = 14.sp, fontFamily = UiFont,
                shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 1f), 12f),
            ),
        )
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(pal.tint).clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close, if (isPt()) "Fechar" else "Close", tint = pal.onCard,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Pilula com os icones dos apps das notificacoes silenciosas (como no Pixel). */
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

/** Rodape do Android 14: Gerenciar (esquerda) e Limpar tudo (direita). */
@Composable
private fun ListFooter(pal: Pal, showClear: Boolean, onManage: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterPill(if (isPt()) "Gerenciar" else "Manage", pal.tint, pal.onCard, onManage)
        if (showClear) FooterPill(if (isPt()) "Limpar tudo" else "Clear all", pal.clearBg, pal.onClear, onClear)
    }
}

@Composable
private fun FooterPill(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, LockMotion.fastSpatial(), label = "footerPress")
    SolidCard(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, RoundedCornerShape(50), bg) {
        Text(
            text,
            modifier = Modifier.clickable(interactionSource = src, indication = null, onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            color = fg, fontSize = 14.sp, fontFamily = UiFont, maxLines = 1,
        )
    }
}

/** Pilula "N + seta" do grupo, como no Android 14. */
@Composable
private fun CountChevronPill(count: Int, rotation: Float, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count", color = fg, fontSize = 14.sp, fontFamily = UiFont, maxLines = 1)
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = fg, modifier = Modifier.size(22.dp).rotate(rotation))
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
    val brand = n.color != 0
    val bg = if (brand) Color(n.color or 0xFF000000.toInt()) else pal.accent
    val glyph = if (!brand) pal.onAccent else if (bg.luminance() > 0.55f) Color(0xFF1C1B1F) else Color.White
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val icon = n.icon
        if (icon != null) {
            Image(icon, null, Modifier.size(size * 0.62f), colorFilter = ColorFilter.tint(glyph))
        } else {
            Text(n.appName.take(1).uppercase(), color = glyph, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
