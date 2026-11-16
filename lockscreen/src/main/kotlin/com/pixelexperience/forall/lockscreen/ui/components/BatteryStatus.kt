package com.pixelexperience.forall.lockscreen.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pixelexperience.forall.lockscreen.ui.theme.UiFont
import java.util.Locale

private const val LOW_PCT = 15

private enum class BatteryKind { NORMAL, CHARGING, LOW }

private class BatteryInfo(val pct: Int, val plugged: Boolean, val full: Boolean)

private fun readBattery(i: Intent?): BatteryInfo? {
    if (i == null) return null
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    if (level < 0) return null
    return BatteryInfo(
        pct = level * 100 / scale,
        plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
        full = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL,
    )
}

@Composable
private fun rememberBatteryInfo(): BatteryInfo? {
    val ctx = LocalContext.current
    // leitura inicial síncrona (intent "sticky"), depois só reage a mudanças reais da bateria
    var info by remember { mutableStateOf<BatteryInfo?>(readBattery(ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                readBattery(i)?.let { info = it }
            }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return info
}

/**
 * Centro da barra inferior: texto simples da bateria; ao carregar, pílula tonal com raio (anima ao conectar);
 * com bateria fraca (<= 15% e sem carregador), pílula de aviso uma vez só, sem animação contínua.
 * O sistema não informa carga rápida nem sem fio ao app, então o estado é só carregando/carregada.
 */
@Composable
fun BatteryStatus(showPercent: Boolean, scheme: ColorScheme, modifier: Modifier = Modifier) {
    val info = rememberBatteryInfo() ?: return
    val pt = Locale.getDefault().language == "pt"
    val kind = when {
        info.plugged || info.full -> BatteryKind.CHARGING
        info.pct <= LOW_PCT -> BatteryKind.LOW
        else -> BatteryKind.NORMAL
    }

    // raio "salta" ao conectar o carregador (não na primeira composição)
    val bolt = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(info.plugged) {
        if (info.plugged && seen) {
            bolt.snapTo(0.4f)
            bolt.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
        seen = true
    }
    // aviso de bateria fraca: um único pulso ao aparecer
    val warn = remember { Animatable(1f) }
    LaunchedEffect(kind) {
        if (kind == BatteryKind.LOW) {
            warn.snapTo(0.8f)
            warn.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
    }

    AnimatedContent(
        targetState = kind, modifier = modifier,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "battery",
    ) { k ->
        when (k) {
            BatteryKind.NORMAL -> if (showPercent) {
                Text(
                    "${info.pct}% " + if (pt) "de bateria" else "battery",
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = UiFont, fontSize = 15.sp, letterSpacing = 0.1.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 1f), 12f),
                    ),
                )
            }
            BatteryKind.CHARGING -> {
                val state = if (info.full) (if (pt) "Carregada" else "Fully charged") else (if (pt) "Carregando" else "Charging")
                Pill(
                    scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Rounded.Bolt, { bolt.value },
                    if (showPercent) "$state · ${info.pct}%" else state,
                )
            }
            BatteryKind.LOW -> {
                val state = if (pt) "Bateria fraca" else "Low battery"
                Pill(
                    scheme.errorContainer, scheme.onErrorContainer, Icons.Rounded.BatteryAlert, { warn.value },
                    if (showPercent) "$state · ${info.pct}%" else state,
                )
            }
        }
    }
}

@Composable
private fun Pill(container: Color, content: Color, icon: ImageVector, iconScale: () -> Float, text: String) {
    Row(
        Modifier.background(container, CircleShape).padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null, tint = content,
            modifier = Modifier.size(18.dp).graphicsLayer { scaleX = iconScale(); scaleY = iconScale() },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = content),
        )
    }
}
