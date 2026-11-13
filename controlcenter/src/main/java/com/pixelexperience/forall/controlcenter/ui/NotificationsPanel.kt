package com.pixelexperience.forall.controlcenter.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.controlcenter.NotifItem
import com.pixelexperience.forall.controlcenter.NotificationStore
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign

/**
 * Painel de notificações (o "N" do lado esquerdo): cartão separado da central de controle.
 * Cada notificação desliza para os lados para sumir; tocar abre o app.
 */
@Composable
internal fun NotificationsPanel(
    topInset: Dp,
    actions: PanelActions,
) {
    val c = rememberPanelColors()
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(NotificationStore.hasAccess(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            hasAccess = NotificationStore.hasAccess(context)
            delay(1000)
        }
    }
    val date = remember {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
            .replaceFirstChar { it.uppercase() }
    }
    val canClear = NotificationStore.items.any { it.clearable }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = topInset + 8.dp, bottom = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = c.panel,
            shape = RoundedCornerShape(36.dp),
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = date,
                        color = c.onPanel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasAccess && canClear) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.tileOn)
                                .clickable { NotificationStore.clearAll() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cc_np_clear),
                                color = c.onTileOn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !hasAccess -> AccessCard(c, actions)
                        NotificationStore.items.isEmpty() -> Text(
                            text = stringResource(R.string.cc_np_empty),
                            color = c.onPanel.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(NotificationStore.items, key = { it.key }) { item ->
                                NotifCard(
                                    item = item,
                                    c = c,
                                    onOpen = {
                                        NotificationStore.open(context, item)
                                        actions.collapse()
                                    },
                                    onDismiss = { NotificationStore.dismiss(item.key) },
                                )
                            }
                        }
                    }
                }

                // Alça: arrastar daqui para cima fecha o painel (a lista rola livremente).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.onPanel.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AccessCard(c: PanelColors, actions: PanelActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.tileOff)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.cc_np_access_title),
            color = c.onTileOff,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.cc_np_access_body),
            color = c.onTileOff.copy(alpha = 0.75f),
            fontSize = 13.sp,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(c.tileOn)
                .clickable { actions.requestNotificationAccess() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.cc_np_access_button),
                color = c.onTileOn,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun NotifCard(
    item: NotifItem,
    c: PanelColors,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val fade = 1f - (abs(offset.value) / width).coerceIn(0f, 1f)
    val time = remember(item.whenMs) { DateFormat.getTimeFormat(context).format(Date(item.whenMs)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offset.value
                alpha = fade
            }
            .clip(RoundedCornerShape(24.dp))
            .background(c.tileOff)
            .clickable(onClick = onOpen)
            .then(
                if (item.clearable) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (abs(offset.value) > width * 0.35f) {
                                        offset.animateTo(offset.value.sign * width, tween(150))
                                        onDismiss()
                                    } else {
                                        offset.animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offset.animateTo(0f) }
                            },
                        ) { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + dx) }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val accent = if (item.color != 0) Color(item.color).copy(alpha = 1f) else c.tileOn
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            if (item.icon != null) {
                Icon(
                    bitmap = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = item.appName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${item.appName} · $time",
                color = c.onTileOff.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    color = c.onTileOff,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.text.isNotBlank()) {
                Text(
                    text = item.text,
                    color = c.onTileOff.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
