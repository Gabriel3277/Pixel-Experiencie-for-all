package com.pixelexperience.forall.statusbar

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkInk = Color(0xFF1B1B1F)
private const val MAX_NOTIF_ICONS = 3

/** Quicksand (assets/quicksand.ttf, peso 600); se o arquivo faltar, usa a fonte do sistema. */
private fun loadClockFont(context: Context): FontFamily =
    runCatching {
        val typeface = Typeface.Builder(context.assets, "quicksand.ttf")
            .setFontVariationSettings("'wght' 600")
            .build()
        if (typeface != null) FontFamily(typeface) else FontFamily.SansSerif
    }.getOrDefault(FontFamily.SansSerif)

@Composable
internal fun StatusBarContent(s: StatusBarState) {
    val fg by animateColorAsState(
        targetValue = if (s.darkIcons) DarkInk else Color.White,
        animationSpec = tween(250),
        label = "fg"
    )
    val context = LocalContext.current
    val clockFont = remember { loadClockFont(context) }
    val shadow = if (s.darkIcons) null else Shadow(Color(0x66000000), Offset(0f, 1f), 3f)

    val notifs = when (s.notifMode) {
        2 -> emptyList<NotifIcon>()
        1 -> NotificationRepository.icons.filter { it.priority }
        else -> NotificationRepository.icons
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Esquerda: relógio + ícones de notificação
        Text(
            text = s.time,
            color = fg,
            style = TextStyle(
                fontFamily = clockFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                fontFeatureSettings = "tnum",
                shadow = shadow
            )
        )
        Row(
            modifier = Modifier
                .padding(start = 10.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            notifs.take(MAX_NOTIF_ICONS).forEach { icon ->
                Image(
                    bitmap = icon.bitmap,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(fg),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(15.dp)
                )
            }
            if (notifs.size > MAX_NOTIF_ICONS) {
                Canvas(Modifier.size(4.dp)) { drawCircle(fg) }
            }
        }

        Spacer(Modifier.weight(1f))

        // Direita: indicadores do sistema
        Row(
            modifier = Modifier.animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slot(s.vpn) { KeyIcon(fg) }
            Slot(s.dnd) { DndIcon(fg) }
            Slot(s.ringerMode == AudioManager.RINGER_MODE_SILENT && s.showMute) { BellOffIcon(fg) }
            Slot(s.ringerMode == AudioManager.RINGER_MODE_VIBRATE && s.showVibrate) { VibrateIcon(fg) }
            Slot(true) { SignalIcon(s.signalLevel, fg) }
            Slot(s.wifi) { WifiIcon(s.wifiLevel, fg) }
            Slot(true) { BatteryPill(s) }
        }
    }
}

/** Entrada e saída discretas: some e aparece com fade e deslocamento horizontal. */
@Composable
private fun Slot(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
        exit = fadeOut(tween(140)) + shrinkHorizontally(tween(140))
    ) {
        Box(Modifier.padding(start = 6.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun SignalIcon(level: Int, color: Color) {
    val alphas = List(4) { i ->
        animateFloatAsState(if (i < level) 1f else 0.3f, tween(200), label = "sig$i").value
    }
    Canvas(Modifier.size(width = 15.dp, height = 12.dp)) {
        val bars = 4
        val gap = size.width * 0.1f
        val bw = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = size.height * (0.4f + 0.6f * i / (bars - 1))
            drawRoundRect(
                color = color.copy(alpha = alphas[i]),
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2)
            )
        }
    }
}

/** Wi-Fi estilo Pixel: leque de 4 faixas separadas por vãos finos; o sinal acende de dentro para fora. */
@Composable
private fun WifiIcon(level: Int, color: Color) {
    val active = level.coerceIn(1, 4)
    val alphas = List(4) { i ->
        animateFloatAsState(if (i < active) 1f else 0.3f, tween(250), label = "wifi$i").value
    }
    Canvas(Modifier.size(width = 18.dp, height = 14.dp)) {
        val half = 50f
        val cx = size.width / 2
        val cy = size.height - 1.dp.toPx()
        val outer = minOf(size.width / 1.6f, cy - 0.7.dp.toPx())
        val gap = 1.3.dp.toPx()
        val fracs = floatArrayOf(0.30f, 0.55f, 0.78f, 1f)
        val round = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
        for (i in 0 until 4) {
            val rOut = outer * fracs[i]
            val rIn = if (i == 0) 0f else outer * fracs[i - 1] + gap
            val band = bandPath(cx, cy, rIn, rOut, -90f - half, half * 2)
            drawPath(band, color.copy(alpha = alphas[i]))
            drawPath(band, color.copy(alpha = alphas[i]), style = round)
        }
    }
}

private fun bandPath(cx: Float, cy: Float, rIn: Float, rOut: Float, start: Float, sweep: Float): Path {
    fun box(r: Float) = Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2))
    return Path().apply {
        if (rIn <= 0f) {
            moveTo(cx, cy)
            arcTo(box(rOut), start, sweep, false)
        } else {
            arcTo(box(rOut), start, sweep, true)
            arcTo(box(rIn), start + sweep, -sweep, false)
        }
        close()
    }
}

@Composable
private fun BellOffIcon(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.13f
        val bell = Path().apply {
            moveTo(w * 0.18f, h * 0.74f)
            lineTo(w * 0.18f, h * 0.44f)
            cubicTo(w * 0.18f, h * 0.12f, w * 0.82f, h * 0.12f, w * 0.82f, h * 0.44f)
            lineTo(w * 0.82f, h * 0.74f)
            close()
        }
        drawPath(bell, color, style = Stroke(width = sw, join = StrokeJoin.Round))
        drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.9f))
        drawLine(color, Offset(w * 0.04f, h * 0.06f), Offset(w * 0.96f, h * 0.96f), sw, StrokeCap.Round)
    }
}

@Composable
private fun VibrateIcon(color: Color) {
    Canvas(Modifier.size(width = 15.dp, height = 12.dp)) {
        val w = size.width
        val h = size.height
        val sw = h * 0.12f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.3f, h * 0.06f),
            size = Size(w * 0.4f, h * 0.88f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(width = sw)
        )
        drawLine(color, Offset(w * 0.12f, h * 0.3f), Offset(w * 0.12f, h * 0.7f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.88f, h * 0.3f), Offset(w * 0.88f, h * 0.7f), sw, StrokeCap.Round)
    }
}

@Composable
private fun DndIcon(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val w = size.width
        val sw = w * 0.13f
        drawCircle(color, radius = w / 2 - sw / 2, style = Stroke(width = sw))
        drawLine(color, Offset(w * 0.27f, w * 0.5f), Offset(w * 0.73f, w * 0.5f), sw, StrokeCap.Round)
    }
}

@Composable
private fun KeyIcon(color: Color) {
    Canvas(Modifier.size(width = 15.dp, height = 10.dp)) {
        val w = size.width
        val h = size.height
        val sw = h * 0.2f
        val r = h * 0.3f
        drawCircle(color, radius = r, center = Offset(r + sw / 2, h / 2), style = Stroke(width = sw))
        drawLine(color, Offset(2 * r + sw, h / 2), Offset(w - sw / 2, h / 2), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.7f, h / 2), Offset(w * 0.7f, h * 0.88f), sw, StrokeCap.Round)
        drawLine(color, Offset(w - sw / 2, h / 2), Offset(w - sw / 2, h * 0.88f), sw, StrokeCap.Round)
    }
}

/** Bateria estilo Pixel: corpo arredondado com terminal, número dentro, raio ao carregar. */
@Composable
private fun BatteryPill(s: StatusBarState) {
    val dark = s.darkIcons
    val low = s.batteryPct <= 15 && !s.charging
    val accent: Color? = when {
        s.charging -> Color(0xFF8BD99B)
        s.powerSaver -> Color(0xFFFFD54F)
        low -> Color(0xFFFF8A80)
        else -> null
    }
    val baseTarget = if (dark) DarkInk else Color.White
    val base by animateColorAsState(baseTarget, tween(250), label = "battBase")
    val fill by animateColorAsState(accent ?: baseTarget, tween(300), label = "battFill")
    val textOnFill = if (accent == null && dark) Color.White else DarkInk
    val pct by animateFloatAsState(s.batteryPct.coerceIn(0, 100) / 100f, tween(400), label = "battPct")
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val label = s.batteryPct.toString()
    val showText = s.showBatteryPct
    val charging = s.charging

    Canvas(Modifier.size(width = 32.dp, height = 15.dp)) {
        val h = size.height
        val nubW = 2.2.dp.toPx()
        val nubH = 6.dp.toPx()
        val bodyW = size.width - nubW - 0.8.dp.toPx()
        val track = base.copy(alpha = 0.32f)

        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, bodyW, h, CornerRadius(h * 0.36f))) }
        drawPath(body, track)
        drawRoundRect(
            color = if (pct > 0.97f) fill else track,
            topLeft = Offset(size.width - nubW, (h - nubH) / 2),
            size = Size(nubW, nubH),
            cornerRadius = CornerRadius(nubW / 2)
        )

        val fw = bodyW * pct
        clipPath(body) { drawRect(fill, size = Size(fw, h)) }
        clipRect(0f, 0f, fw, h) {
            drawBatteryGlyphs(paint, label, showText, charging, textOnFill, bodyW)
        }
        clipRect(fw, 0f, bodyW, h) {
            drawBatteryGlyphs(paint, label, showText, charging, base, bodyW)
        }
    }
}

private fun DrawScope.drawBatteryGlyphs(
    paint: Paint,
    label: String,
    showText: Boolean,
    charging: Boolean,
    color: Color,
    w: Float
) {
    val h = size.height
    val textCenter = if (charging && showText) w * 0.62f else w / 2

    if (charging) {
        val bw = h * 0.42f
        val bh = h * 0.68f
        val ox = if (showText) w * 0.1f else (w - bw) / 2
        val oy = (h - bh) / 2
        val bolt = Path().apply {
            moveTo(ox + bw * 0.62f, oy)
            lineTo(ox + bw * 0.05f, oy + bh * 0.56f)
            lineTo(ox + bw * 0.45f, oy + bh * 0.56f)
            lineTo(ox + bw * 0.36f, oy + bh)
            lineTo(ox + bw * 0.95f, oy + bh * 0.42f)
            lineTo(ox + bw * 0.55f, oy + bh * 0.42f)
            close()
        }
        drawPath(bolt, color)
    }

    if (showText) {
        paint.textSize = 11.sp.toPx()
        paint.color = color.toArgb()
        val y = h / 2 - (paint.ascent() + paint.descent()) / 2
        drawIntoCanvas { it.nativeCanvas.drawText(label, textCenter, y, paint) }
    }
}
