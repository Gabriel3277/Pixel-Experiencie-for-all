package com.pixelexperience.forall.controlcenter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// Distância (dp) a partir da qual o item "se solta" do grupo e vira pílula.
private val DetachDistance = 56.dp

// Queda do efeito de onda: vizinho a N posições acompanha (1 - 0,24 * N) do arrasto.
private const val FollowFalloff = 0.24f

// Raio grande o bastante para virar pílula (o Compose limita ao máximo possível).
private val PillRadius = 40.dp

/**
 * Lista segmentada estilo Material 3 Expressive com a animação do vídeo de referência:
 *  1. Ao arrastar um item na horizontal, os vizinhos o acompanham em onda (com mola).
 *  2. Passando de [DetachDistance], o item se solta: vira pílula, os vizinhos voltam
 *     ao lugar e os cantos adjacentes arredondam (o grupo se divide em dois).
 *  3. Ao soltar: volta com mola, ou, se passou de ~40% da largura / foi um fling rápido,
 *     sai da tela, a lista recolhe e [onDismiss] é chamado.
 *
 * Use dentro de um Column já rolável (não é uma LazyColumn).
 */
@Composable
fun <T> SegmentedSwipeList(
    items: List<T>,
    itemKey: (T) -> Any,
    onDismiss: (T) -> Unit,
    modifier: Modifier = Modifier,
    canDismiss: (T) -> Boolean = { true },
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    outerRadius: Dp = 22.dp,
    innerRadius: Dp = 5.dp,
    gap: Dp = 2.dp,
    itemContent: @Composable (T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val detachPx = with(LocalDensity.current) { DetachDistance.toPx() }

    val dragX = remember { Animatable(0f) }
    var dragKey by remember { mutableStateOf<Any?>(null) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val removing = remember { mutableStateListOf<Any>() }
    val detached by remember {
        derivedStateOf { dragKey != null && abs(dragX.value) > detachPx }
    }

    LaunchedEffect(detached) {
        if (detached) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Itens que ainda contam para índice/cantos (os que estão saindo ficam de fora).
    val visible = items.filter { itemKey(it) !in removing }
    val dragIndex = visible.indexOfFirst { itemKey(it) == dragKey }
    val lastIndex = visible.lastIndex
    val split = detached && dragIndex >= 0

    fun onStart(k: Any) {
        if (dragKey == null) {
            dragKey = k
            scope.launch { dragX.snapTo(0f) }
        }
    }

    fun onMove(k: Any, delta: Float) {
        if (dragKey == k) scope.launch { dragX.snapTo(dragX.value + delta) }
    }

    fun onStop(item: T, velocity: Float) {
        val k = itemKey(item)
        scope.launch {
            if (dragKey != k) return@launch
            val w = widthPx.coerceAtLeast(1f)
            val x = dragX.value
            val flung = abs(velocity) > 1600f && sign(velocity) == sign(x) && abs(x) > detachPx
            if (abs(x) > w * 0.4f || flung) {
                dragX.animateTo(sign(x) * w * 1.1f, tween(160))
                removing.add(k)
                delay(260)
                onDismiss(item)
                removing.remove(k)
                dragX.snapTo(0f)
                dragKey = null
            } else {
                dragX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
                dragKey = null
            }
        }
    }

    Column(modifier = modifier.onSizeChanged { widthPx = it.width.toFloat() }) {
        items.forEach { item ->
            val k = itemKey(item)
            key(k) {
                val idx = visible.indexOfFirst { itemKey(it) == k }
                val isDragged = idx >= 0 && idx == dragIndex
                val distance = if (idx < 0 || dragIndex < 0) -1 else abs(idx - dragIndex)

                val pill = split && isDragged
                val bigTop = idx <= 0 || (split && idx == dragIndex + 1)
                val bigBottom = idx == lastIndex || (split && idx == dragIndex - 1)

                val top by animateDpAsState(
                    targetValue = when {
                        pill -> PillRadius
                        bigTop -> outerRadius
                        else -> innerRadius
                    },
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                    label = "topCorner",
                )
                val bottom by animateDpAsState(
                    targetValue = when {
                        pill -> PillRadius
                        bigBottom -> outerRadius
                        else -> innerRadius
                    },
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                    label = "bottomCorner",
                )

                AnimatedVisibility(
                    visible = idx >= 0,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                ) {
                    val factor = if (distance < 0) 0f else (1f - FollowFalloff * distance).coerceAtLeast(0f)
                    val followTarget =
                        if (isDragged || split || distance < 0) 0f else dragX.value * factor
                    val follow by animateFloatAsState(
                        targetValue = followTarget,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                        label = "follow",
                    )
                    val dragState = rememberDraggableState { delta -> onMove(k, delta) }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (idx > 0) gap else 0.dp)
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Horizontal,
                                enabled = canDismiss(item),
                                onDragStarted = { onStart(k) },
                                onDragStopped = { velocity -> onStop(item, velocity) },
                            )
                            .graphicsLayer {
                                translationX = if (isDragged) dragX.value else follow
                            },
                        shape = RoundedCornerShape(
                            topStart = top,
                            topEnd = top,
                            bottomEnd = bottom,
                            bottomStart = bottom,
                        ),
                        color = containerColor,
                        contentColor = contentColor,
                    ) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}
