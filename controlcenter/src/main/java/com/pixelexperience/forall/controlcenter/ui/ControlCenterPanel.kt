package com.pixelexperience.forall.controlcenter.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay
import kotlin.math.min

/** Altura da área do painel (cartão + margens), sem a barra de status, em dp. */
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

/**
 * Painel no estilo Android 17 / Material 3 Expressive: cartão flutuante verde,
 * sliders em pílula com alça, tiles que morfam de círculo para quadrado
 * arredondado ao ligar (com animação elástica).
 */
@Composable
internal fun ControlCenterPanel(
    state: PanelState,
    topInset: Dp,
    actions: PanelActions,
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
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = topInset + 8.dp, bottom = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = c.panel.copy(alpha = 0.86f),
            shape = RoundedCornerShape(36.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Topo: configurações e desligar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

                // Brilho
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                // Volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillSlider(
                        modifier = Modifier.weight(1f),
                        value = state.volume,
                        onValueChange = state::updateVolume,
                        enabled = true,
                        disabledHint = "",
                        onDisabledClick = {},
                        trackColor = c.tileOff,
                        fillColor = c.tileOn,
                        hintColor = c.onTileOff,
                    )
                    // Botão ⋮ sempre verde-claro, como na referência.
                    CircleButton(
                        icon = R.drawable.cc_ic_more,
                        label = stringResource(R.string.cc_more_volume),
                        active = true,
                        activeRadius = 28.dp,
                        colors = c,
                        onClick = actions::openVolume,
                    )
                }

                // Tiles largos: o quadradinho do ícone é que liga e desliga.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WideTile(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.cc_ic_wifi,
                        title = stringResource(R.string.cc_tile_internet),
                        subtitle = state.internetSubtitle,
                        active = state.internetActive,
                        colors = c,
                        onClick = actions::openInternet,
                    )
                    WideTile(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.cc_ic_bluetooth,
                        title = stringResource(R.string.cc_tile_bluetooth),
                        subtitle = state.bluetoothSubtitle,
                        active = state.bluetoothActive,
                        colors = c,
                        onClick = actions::openBluetooth,
                    )
                }

                // Tiles pequenos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallTile(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.cc_ic_flashlight,
                        label = stringResource(R.string.cc_tile_flashlight),
                        active = state.torchOn,
                        enabled = state.hasTorch,
                        colors = c,
                        onClick = state::toggleTorch,
                    )
                    SmallTile(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.cc_ic_airplane,
                        label = stringResource(R.string.cc_tile_airplane),
                        active = state.airplaneOn,
                        colors = c,
                        onClick = actions::openAirplane,
                    )
                    SmallTile(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.cc_ic_rotate,
                        label = stringResource(R.string.cc_tile_rotate),
                        active = state.autoRotate,
                        colors = c,
                        onClick = {
                            if (state.canWriteSettings) state.toggleRotation()
                            else actions.requestWriteSettings()
                        },
                    )
                    SmallTile(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.cc_ic_alarm,
                        label = stringResource(R.string.cc_tile_alarm),
                        active = false,
                        colors = c,
                        onClick = actions::openAlarms,
                    )
                }
            }
        }
    }
}
