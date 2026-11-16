package com.pixelexperience.forall.controlcenter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf

/** Mantido por compatibilidade: o cartão agora tem altura própria (varia com os blocos). */
internal const val PANEL_CONTENT_DP = 388

/**
 * Cores do painel: só papéis do tema Material You (cores dinâmicas do papel de parede).
 * Sem cores fixas: o rosado/alaranjado vem do primary e dos containers do sistema.
 */
internal class PanelColors(
    val panel: Color,
    val tileOff: Color,
    val tileOn: Color,
    val onTileOn: Color,
    val onTileOff: Color,
    val onPanel: Color,
    val card: Color,
)

@Composable
internal fun rememberPanelColors(): PanelColors {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return remember(cs, dark) {
        if (dark) {
            PanelColors(
                panel = lerp(cs.surfaceContainer, cs.primaryContainer, 0.25f),
                tileOff = lerp(cs.surfaceContainerHighest, cs.primaryContainer, 0.25f),
                tileOn = cs.primary,
                onTileOn = cs.onPrimary,
                onTileOff = cs.onSurface,
                onPanel = cs.onSurface,
                card = lerp(cs.surfaceContainerHigh, cs.secondaryContainer, 0.35f),
            )
        } else {
            PanelColors(
                panel = lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.30f),
                tileOff = lerp(cs.surfaceContainerHighest, cs.primaryContainer, 0.45f),
                tileOn = cs.primary,
                onTileOn = cs.onPrimary,
                onTileOff = cs.onSurface,
                onPanel = cs.onSurface,
                card = lerp(cs.surfaceContainerLowest, cs.secondaryContainer, 0.35f),
            )
        }
    }
}

/** Versão independente (guarda o próprio layout e o Edit Mode). */
@Composable
internal fun ControlCenterPanel(
    state: PanelState,
    topInset: Dp,
    actions: PanelActions,
) {
    val ctx = LocalContext.current
    var layout by remember { mutableStateOf(TileLayoutStore.load(ctx)) }
    var editing by remember { mutableStateOf(false) }
    ControlCard(
        state = state,
        topInset = topInset,
        actions = actions,
        layout = layout,
        editing = editing,
        onLayoutChange = {
            layout = it
            TileLayoutStore.save(ctx, it)
        },
        onEditingChange = { editing = it },
    )
}

/**
 * Cartão da central de controle (Android 17 / Material 3 Expressive): topo, brilho
 * e a grade de blocos editável. A altura acompanha a quantidade de blocos.
 */
@Composable
internal fun ControlCard(
    state: PanelState,
    topInset: Dp,
    actions: PanelActions,
    layout: List<TilePlace>,
    editing: Boolean,
    onLayoutChange: (List<TilePlace>) -> Unit,
    onEditingChange: (Boolean) -> Unit,
) {
    DisposableEffect(state) {
        state.start()
        onDispose { state.stop() }
    }
    LaunchedEffect(state) {
        while (true) {
            delay(1000)
            state.refresh()
        }
    }

    val c = rememberPanelColors()
    val expanded = state.qsExpanded

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = topInset + 8.dp, bottom = 8.dp)
            .onGloballyPositioned { state.qsBottomPx = it.boundsInRoot().bottom },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                ),
            color = c.panel,
            shape = RoundedCornerShape(Pd.CardRadius),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(Pd.SectionGap),
            ) {
                // Cabeçalho: relógio e data no modo expandido; título no Edit Mode.
                AnimatedVisibility(
                    visible = expanded || editing,
                    modifier = Modifier.entrance(0),
                    enter = fadeIn(tween(200)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(tween(120)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                ) {
                    AnimatedContent(
                        targetState = editing,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                        label = "header",
                    ) { editingNow ->
                        if (editingNow) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Editar blocos",
                                    color = c.onPanel,
                                    style = Pd.Title,
                                    modifier = Modifier.weight(1f),
                                )
                                HeaderButton(
                                    icon = R.drawable.cc_ic_check,
                                    label = "Concluir",
                                    tint = c.onPanel,
                                    onClick = { onEditingChange(false) },
                                )
                            }
                        } else {
                            ClockHeader(c)
                        }
                    }
                }

                // Brilho (nos dois modos)
                Row(
                    modifier = Modifier
                        .entrance(1)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillSlider(
                        modifier = Modifier.weight(1f),
                        value = state.brightness,
                        onValueChange = state::updateBrightness,
                        enabled = state.canWriteSettings,
                        disabledHint = stringResource(R.string.cc_brightness_permission),
                        onDisabledClick = actions::requestWriteSettings,
                        trackColor = c.tileOff,
                        fillColor = c.tileOn,
                        hintColor = c.onTileOff,
                        icon = R.drawable.cc_ic_brightness,
                        iconTint = c.onTileOn,
                        showPercent = true,
                    )
                    CircleButton(
                        icon = R.drawable.cc_ic_brightness_auto,
                        label = stringResource(R.string.cc_auto_brightness),
                        active = state.autoBrightness,
                        activeRadius = 18.dp,
                        colors = c,
                        onClick = {
                            if (state.canWriteSettings) state.toggleAutoBrightness()
                            else actions.requestWriteSettings()
                        },
                    )
                }

                // Blocos: 2 linhas no modo combinado, grade completa no expandido.
                TileGrid(
                    layout = layout,
                    editing = editing,
                    onLayoutChange = onLayoutChange,
                    state = state,
                    actions = actions,
                    colors = c,
                    compact = !expanded && !editing,
                )

                // Rodapé do modo expandido: editar, configurações e energia.
                AnimatedVisibility(
                    visible = expanded && !editing,
                    enter = fadeIn(tween(200)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(tween(120)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderButton(
                            icon = R.drawable.cc_ic_pencil,
                            label = "Editar blocos",
                            tint = c.onPanel,
                            onClick = { onEditingChange(true) },
                        )
                        HeaderButton(
                            icon = R.drawable.cc_ic_settings,
                            label = stringResource(R.string.cc_settings),
                            tint = c.onPanel,
                            onClick = actions::openSettings,
                        )
                        Spacer(Modifier.weight(1f))
                        HeaderButton(
                            icon = R.drawable.cc_ic_power,
                            label = stringResource(R.string.cc_power),
                            tint = c.onPanel,
                            onClick = actions::showPowerMenu,
                        )
                    }
                }

                // Alça: alterna entre o modo combinado e o expandido.
                if (!editing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clickable { state.qsExpanded = !state.qsExpanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(c.onPanel.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}

/** Relógio grande, data e bateria (cabeçalho do modo expandido). */
@Composable
private fun ClockHeader(c: PanelColors) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(1000)
        }
    }
    val time = remember(now / 60_000L) { DateFormat.getTimeFormat(ctx).format(Date(now)) }
    val date = remember(now / 3_600_000L) {
        SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = time, color = c.onPanel, style = Pd.Time)
            Text(text = date, color = c.onPanel.copy(alpha = 0.7f), style = Pd.Label)
        }
        if (battery >= 0) {
            Text(
                text = if (charging) "Carregando · $battery%" else "$battery%",
                color = c.onPanel,
                style = Pd.Label,
            )
        }
    }
}
