package com.pixelexperience.forall.controlcenter.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.controlcenter.NotifItem
import com.pixelexperience.forall.controlcenter.NotificationStore
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay

/**
 * Painel único (Android 17): central de controle em cima e as notificações
 * logo abaixo, empilhadas por app. Fecha arrastando para cima a partir da
 * parte de baixo, ou tocando no botão ^.
 */
@Composable
internal fun UnifiedPanel(
    state: PanelState,
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
    val canClear = NotificationStore.items.any { it.clearable }

    // Pilhas abertas (por app) e grupos (um por app, na ordem da notificação mais recente).
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val groups = NotificationStore.items.groupBy { it.pkg }.entries.toList()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + PANEL_CONTENT_DP.dp),
        ) {
            ControlCenterPanel(state = state, topInset = topInset, actions = actions)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .entrance(5)
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Notificações",
                    color = c.onPanel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (hasAccess && canClear) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.tileOff)
                            .clickable { NotificationStore.clearAll() }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.cc_np_clear),
                            color = c.onTileOff,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasAccess -> AccessCard(c, actions)
                    groups.isEmpty() -> Text(
                        text = stringResource(R.string.cc_np_empty),
                        color = c.onPanel.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(groups, key = { _, g -> g.key }) { index, g ->
                            val enter = if (index < 4) Modifier.entrance(6 + index) else Modifier
                            val list = g.value
                            NotifGroup(
                                modifier = enter,
                                list = list,
                                expanded = expanded[g.key] == true,
                                onToggle = { expanded[g.key] = expanded[g.key] != true },
                                c = c,
                                onOpen = { item ->
                                    NotificationStore.open(context, item)
                                    actions.collapse()
                                },
                                onDismissItem = { item -> NotificationStore.dismiss(item.key) },
                                onDismissGroup = {
                                    list.filter { it.clearable }
                                        .forEach { NotificationStore.dismiss(it.key) }
                                },
                            )
                        }
                    }
                }
            }

            // Rodapé: Gerenciar e fechar (^). Arrastar daqui para cima também fecha.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.tileOff)
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (_: Exception) {
                            }
                            actions.collapse()
                        }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Gerenciar",
                        color = c.onTileOff,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(c.tileOff)
                        .clickable { actions.collapse() },
                    contentAlignment = Alignment.Center,
                ) {
                    ChevronUp(color = c.onTileOff)
                }
            }
        }
    }
}

/**
 * Notificações de um mesmo app. Uma só: cartão normal. Várias: pilha com
 * cartões atrás; deslizar o cartão da frente dispensa o grupo todo; tocar em
 * "Mostrar tudo" abre a pilha.
 */
@Composable
private fun NotifGroup(
    modifier: Modifier,
    list: List<NotifItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    c: PanelColors,
    onOpen: (NotifItem) -> Unit,
    onDismissItem: (NotifItem) -> Unit,
    onDismissGroup: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (list.size == 1) {
            val item = list[0]
            NotifCard(item, c, { onOpen(item) }, { onDismissItem(item) })
        } else if (!expanded) {
            val behind = minOf(list.size - 1, 2)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = (behind * 8).dp),
            ) {
                for (i in behind downTo 1) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(y = (i * 8).dp)
                            .padding(horizontal = (i * 10).dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.tileOff.copy(alpha = 1f - i * 0.25f)),
                    )
                }
                NotifCard(list[0], c, { onOpen(list[0]) }, onDismissGroup)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GroupPill("Mostrar tudo (${list.size})", c, onToggle)
            }
        } else {
            list.forEach { item ->
                key(item.key) {
                    NotifCard(item, c, { onOpen(item) }, { onDismissItem(item) })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                GroupPill("Mostrar menos", c, onToggle)
                GroupPill("Limpar grupo", c, onDismissGroup)
            }
        }
    }
}

@Composable
private fun GroupPill(text: String, c: PanelColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.tileOff)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = c.onTileOff,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ChevronUp(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2f.dp.toPx()
        drawLine(
            color = color,
            start = Offset(w * 0.2f, h * 0.65f),
            end = Offset(w * 0.5f, h * 0.35f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.35f),
            end = Offset(w * 0.8f, h * 0.65f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
