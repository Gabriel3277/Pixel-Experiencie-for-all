package com.pixelexperience.forall.controlcenter.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pixelexperience.forall.controlcenter.NotificationStore
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay

/**
 * Painel único (Android 17): central de controle em cima e as notificações
 * logo abaixo, na mesma tela. Fecha arrastando para cima a partir da parte
 * de baixo, ou tocando no botão ^.
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Central de controle (mesmo cartão de antes, altura fixa).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + PANEL_CONTENT_DP.dp),
        ) {
            ControlCenterPanel(state = state, topInset = topInset, actions = actions)
        }

        // Notificações.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier
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
