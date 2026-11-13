package com.pixelexperience.forall.controlcenter.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay

/** Distribui os blocos em linhas de 4 colunas, sem deixar espaço vazio no fim de cada linha. */
private fun pack(items: List<TilePlace>): List<List<Pair<TilePlace, Int>>> {
    val rows = mutableListOf<List<Pair<TilePlace, Int>>>()
    var cur = mutableListOf<Pair<TilePlace, Int>>()
    var used = 0

    fun closeRow() {
        if (cur.isEmpty()) return
        if (used < 4) {
            val last = cur.last()
            cur[cur.lastIndex] = last.first to (last.second + 4 - used)
        }
        rows += cur
        cur = mutableListOf()
        used = 0
    }

    for (p in items) {
        val span = p.span.coerceIn(1, 2)
        if (used + span > 4) closeRow()
        cur += p to span
        used += span
    }
    closeRow()
    return rows
}

/**
 * Grade de blocos. No Edit Mode: segure e arraste para reorganizar,
 * toque para alternar entre pequeno e largo, e use o "x" para remover.
 */
@Composable
internal fun TileGrid(
    layout: List<TilePlace>,
    editing: Boolean,
    onLayoutChange: (List<TilePlace>) -> Unit,
    state: PanelState,
    actions: PanelActions,
    colors: PanelColors,
) {
    val ctx = LocalContext.current
    var sys by remember { mutableStateOf(readSystem(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            sys = readSystem(ctx)
            delay(1000)
        }
    }

    if (layout.isEmpty()) {
        Text(
            text = "Nenhum bloco. Adicione abaixo.",
            color = colors.onPanel.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val unit = (maxWidth - gap * 3) / 4
        val rows = pack(layout)

        val bounds = remember { mutableMapOf<String, Rect>() }
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var dragStart by remember { mutableStateOf(Offset.Zero) }

        fun drop() {
            val id = dragId
            if (id != null) {
                val live = layout.map { it.id }.toSet()
                val others = bounds.entries.filter { it.key != id && it.key in live }
                val point = dragStart + dragOffset
                val target = others.firstOrNull { it.value.contains(point) }?.key
                    ?: others.minByOrNull { (it.value.center - point).getDistance() }?.key
                if (target != null) {
                    val from = layout.indexOfFirst { it.id == id }
                    val to = layout.indexOfFirst { it.id == target }
                    if (from >= 0 && to >= 0 && from != to) {
                        val m = layout.toMutableList()
                        val item = m.removeAt(from)
                        m.add(to, item)
                        onLayoutChange(m)
                    }
                }
            }
            dragId = null
            dragOffset = Offset.Zero
        }

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            var order = 0
            rows.forEach { row ->
                val rowHeight = if (row.any { it.second >= 2 }) 76.dp else 64.dp
                val lifted = row.any { it.first.id == dragId }
                Row(
                    modifier = Modifier.zIndex(if (lifted) 1f else 0f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { (place, span) ->
                        val spec = TileCatalog.byId[place.id]
                        if (spec != null) {
                            val index = order++
                            key(place.id) {
                                GridTile(
                                    span = span,
                                    width = unit * span + gap * (span - 1),
                                    height = rowHeight,
                                    editing = editing,
                                    enter = Modifier.entrance(2 + index),
                                    ui = tileUi(place.id, ctx, state, actions, sys),
                                    spec = spec,
                                    colors = colors,
                                    seed = index,
                                    isDragging = dragId == place.id,
                                    dragOffset = dragOffset,
                                    onBounds = { bounds[place.id] = it },
                                    onRemove = { onLayoutChange(layout.filterNot { it.id == place.id }) },
                                    onResize = {
                                        onLayoutChange(
                                            layout.map {
                                                if (it.id == place.id) it.copy(span = if (it.span >= 2) 1 else 2) else it
                                            },
                                        )
                                    },
                                    onDragStart = {
                                        dragId = place.id
                                        dragOffset = Offset.Zero
                                        dragStart = bounds[place.id]?.center ?: Offset.Zero
                                    },
                                    onDrag = { dragOffset += it },
                                    onDragEnd = { drop() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridTile(
    span: Int,
    width: Dp,
    height: Dp,
    editing: Boolean,
    enter: Modifier,
    ui: TileUi,
    spec: TileSpec,
    colors: PanelColors,
    seed: Int,
    isDragging: Boolean,
    dragOffset: Offset,
    onBounds: (Rect) -> Unit,
    onRemove: () -> Unit,
    onResize: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "wiggle")
    val angle by infinite.animateFloat(
        initialValue = -1.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(150 + (seed % 5) * 14, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "angle",
    )

    val resizeNow by rememberUpdatedState(onResize)
    val dragStartNow by rememberUpdatedState(onDragStart)
    val dragNow by rememberUpdatedState(onDrag)
    val dragEndNow by rememberUpdatedState(onDragEnd)

    Box(
        modifier = enter
            .size(width, height)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .graphicsLayer {
                if (editing && !isDragging) rotationZ = angle
                if (isDragging) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                    alpha = 0.92f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (span >= 2) {
            WideTile(
                modifier = Modifier.fillMaxWidth(),
                icon = spec.icon,
                title = spec.label,
                subtitle = ui.subtitle,
                active = ui.active,
                colors = colors,
                onClick = ui.onClick,
            )
        } else {
            SmallTile(
                modifier = Modifier.fillMaxWidth(),
                icon = spec.icon,
                label = spec.label,
                active = ui.active,
                colors = colors,
                enabled = ui.enabled,
                onClick = ui.onClick,
            )
        }

        if (editing) {
            // Camada que captura toque (alternar tamanho) e arrastar (reordenar).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { resizeNow() })
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragStartNow() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragNow(amount)
                            },
                            onDragEnd = { dragEndNow() },
                            onDragCancel = { dragEndNow() },
                        )
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.onPanel)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.cc_ic_close),
                    contentDescription = "Remover",
                    tint = colors.panel,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Blocos que ainda não estão na grade, filtrados por categoria. */
@Composable
internal fun TileCatalogPicker(
    layout: List<TilePlace>,
    colors: PanelColors,
    onAdd: (TileSpec) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier,
) {
    var category by remember { mutableStateOf<TileCategory?>(null) }
    val used = layout.map { it.id }.toSet()
    val available = TileCatalog.all.filter {
        it.id !in used && (category == null || it.category == category)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Adicionar blocos",
                color = colors.onPanel,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            CatalogChip("Restaurar padrão", false, colors, onReset)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CatalogChip("Todos", category == null, colors) { category = null } }
            items(TileCategory.values().toList()) { c ->
                CatalogChip(c.label, category == c, colors) { category = c }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (available.isEmpty()) {
            Text(
                text = "Todos os blocos desta categoria já estão na grade.",
                color = colors.onPanel.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(available, key = { it.id }) { spec ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAdd(spec) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(colors.tileOff),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(spec.icon),
                                contentDescription = spec.label,
                                tint = colors.onTileOff,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            text = spec.label,
                            color = colors.onPanel,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogChip(text: String, selected: Boolean, colors: PanelColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.tileOn else colors.tileOff)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) colors.onTileOn else colors.onTileOff,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
