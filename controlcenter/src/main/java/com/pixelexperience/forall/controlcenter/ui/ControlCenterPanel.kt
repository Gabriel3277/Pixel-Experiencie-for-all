package com.pixelexperience.forall.controlcenter.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Altura da área do painel (cartão + margens), sem a barra de status, em dp. */
internal const val PANEL_CONTENT_DP = 388

/** Cores do painel: tons de verde (da cor dinâmica do sistema), como no Android 17. */
private class PanelColors(
    val panel: Color,
    val tileOff: Color,
    val tileOn: Color,
    val onTileOn: Color,
    val onTileOff: Color,
    val onPanel: Color,
)

@Composable
private fun rememberPanelColors(): PanelColors {
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
 * barras grossas de brilho e volume, tiles largos e redondos.
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
            color = c.panel,
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
                    CircleButton(
                        icon = R.drawable.cc_ic_more,
                        label = stringResource(R.string.cc_more_volume),
                        active = false,
                        colors = c,
                        onClick = actions::openVolume,
                    )
                }

                // Tiles largos
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

@Composable
private fun HeaderButton(
    @DrawableRes icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CircleButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    colors: PanelColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (active) colors.tileOn else colors.tileOff)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (active) colors.onTileOn else colors.onTileOff,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Barra grossa em pílula: preenchimento verde e uma marca vertical no fim. */
@Composable
private fun PillSlider(
    modifier: Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    disabledHint: String,
    onDisabledClick: () -> Unit,
    trackColor: Color,
    fillColor: Color,
    hintColor: Color,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current.density
    val fraction = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(trackColor)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { offset ->
                        onValueChange((offset.x / widthPx).coerceIn(0f, 1f))
                    }
                } else {
                    detectTapGestures { onDisabledClick() }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onValueChange((offset.x / widthPx).coerceIn(0f, 1f))
                        },
                    ) { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(fillColor),
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (widthPx * fraction - 2f * density).roundToInt().coerceAtLeast(0),
                            0,
                        )
                    }
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fillColor),
            )
        } else {
            Text(
                text = disabledHint,
                color = hintColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun WideTile(
    modifier: Modifier,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    active: Boolean,
    colors: PanelColors,
    onClick: () -> Unit,
) {
    val container = if (active) colors.tileOn else colors.tileOff
    val content = if (active) colors.onTileOn else colors.onTileOff
    val badge = if (active) colors.onTileOn.copy(alpha = 0.18f) else colors.panel

    Row(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(badge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = content,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = content.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmallTile(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    colors: PanelColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container = if (active) colors.tileOn else colors.tileOff
    val content = if (active) colors.onTileOn else colors.onTileOff

    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = content.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(26.dp),
        )
    }
}
