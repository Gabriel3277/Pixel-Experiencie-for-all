package com.pixelexperience.forall.controlcenter.ui

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay

/** Altura do conteúdo do painel (sem a barra de status), em dp. */
internal const val PANEL_CONTENT_DP = 252

/**
 * Painel no estilo Android 17 / Material 3 Expressive:
 * tiles largos em pílula, tiles pequenos, cores dinâmicas e cantos bem arredondados.
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = topInset + 12.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    onClick = actions::openInternet,
                )
                WideTile(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.cc_ic_bluetooth,
                    title = stringResource(R.string.cc_tile_bluetooth),
                    subtitle = state.bluetoothSubtitle,
                    active = state.bluetoothActive,
                    onClick = actions::openBluetooth,
                )
            }

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
                    onClick = state::toggleTorch,
                )
                SmallTile(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.cc_ic_rotate,
                    label = stringResource(R.string.cc_tile_rotate),
                    active = state.autoRotate,
                    onClick = {
                        if (state.canWriteSettings) state.toggleRotation()
                        else actions.requestWriteSettings()
                    },
                )
                SmallTile(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.cc_ic_volume,
                    label = stringResource(R.string.cc_tile_volume),
                    active = false,
                    onClick = actions::openVolume,
                )
            }

            BrightnessBar(state = state, actions = actions)
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
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (active) colors.primary else colors.surfaceContainerHighest
    val content = if (active) colors.onPrimary else colors.onSurface
    val badge = if (active) colors.onPrimary.copy(alpha = 0.18f) else colors.surfaceContainer

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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (active) colors.primary else colors.surfaceContainerHighest
    val content = if (active) colors.onPrimary else colors.onSurface

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

@Composable
private fun BrightnessBar(state: PanelState, actions: PanelActions) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerHighest)
            .clickable(enabled = !state.canWriteSettings) { actions.requestWriteSettings() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.cc_ic_brightness),
            contentDescription = stringResource(R.string.cc_brightness),
            tint = colors.onSurface,
            modifier = Modifier.size(24.dp),
        )
        if (state.canWriteSettings) {
            Slider(
                value = state.brightness,
                onValueChange = state::updateBrightness,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.cc_brightness_permission),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
