package com.pixelexperience.forall.controlcenter.ui

import androidx.compose.animation.animateContentSize
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

/** Mantido por compatibilidade: o cartão agora tem altura própria (varia com os blocos). */
internal const val PANEL_CONTENT_DP = 388

/** Cores do painel: tons de verde (da cor dinâmica do sistema), como no Android 17. */
internal class PanelColors(
    val panel: Color,
    val tileOff: Color,
    val tileOn: Color,
    val onTileOn: Color,
    val onTileOff: Color,
    val onPanel: Color,
)

@Composable
internal fun rememberPanelColors(): PanelColors {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return remember(cs, dark) {
        if (dark) {
            PanelColors(
                panel = lerp(Color(0xFF0F1511), cs.primary, 0.18f),
                tileOff = lerp(Color(0xFF0A0F0B), cs.primary, 0.08f),
                tileOn = cs.primary,
                onTileOn = cs.onPrimary,
                onTileOff = cs.onSurface,
                onPanel = cs.onSurface,
            )
        } else {
            PanelColors(
                panel = lerp(Color.White, cs.primary, 0.14f),
                tileOff = lerp(Color.White, cs.primary, 0.26f),
                tileOn = cs.primary,
                onTileOn = cs.onPrimary,
                onTileOff = cs.onSurface,
                onPanel = cs.onSurface,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = topInset + 8.dp, bottom = 8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                ),
            color = c.panel.copy(alpha = 0.86f),
            shape = RoundedCornerShape(36.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Topo
                Row(
                    modifier = Modifier
                        .entrance(0)
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editing) {
                        Text(
                            text = "Editar blocos",
                            color = c.onPanel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        HeaderButton(
                            icon = R.drawable.cc_ic_check,
                            label = "Concluir",
                            tint = c.onPanel,
                            onClick = { onEditingChange(false) },
                        )
                    } else {
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

                // Brilho (o slider de volume foi removido)
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

                // Blocos (sem espaços vazios; o volume liberou uma linha)
                TileGrid(
                    layout = layout,
                    editing = editing,
                    onLayoutChange = onLayoutChange,
                    state = state,
                    actions = actions,
                    colors = c,
                )
            }
        }
    }
}
