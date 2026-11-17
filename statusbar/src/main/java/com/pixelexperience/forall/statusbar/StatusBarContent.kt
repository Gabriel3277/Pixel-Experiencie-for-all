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
import androidx.compose.ui.text.style.LineHeightStyle
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
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
                lineHeightStyle = LineHeightStyle(
                    LineHeightStyle.Alignment.Center,
                    LineHeightStyle.Trim.Both
                ),
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
                Canvas(Modifier.size(4.5.dp)) { drawCircle(fg) }
            }
        }

        Spacer(Modifier.weight(1f))

        // Direita: indicadores do sistema
        Row(
            modifier = Modifier.animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slot(s.vpn) { KeyIcon(fg) }
            Slot(s.bluetooth) { BluetoothIcon(s.bluetoothConnected, fg) }
            Slot(s.dnd) { DndIcon(fg) }
            Slot(s.ringerMode == AudioManager.RINGER_MODE_SILENT && s.showMute) { SpeakerOffIcon(fg) }
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

/** Sinal do Pixel: 5 barras de pontas redondas; as acesas vão da esquerda para a direita. */
@Composable
private fun SignalIcon(level: Int, color: Color) {
    val lit = (level.coerceIn(0, 4) * 5 + 3) / 4
    val alphas = List(5) { i ->
        animateFloatAsState(if (i < lit) 1f else 0.3f, tween(200), label = "sig$i").value
    }
    Canvas(Modifier.size(width = 22.2.dp, height = 14.dp)) {
        val bw = 2.8.dp.toPx()
        val pitch = 4.85.dp.toPx()
        val fracs = floatArrayOf(0.40f, 0.58f, 0.75f, 0.88f, 1f)
        for (i in 0 until 5) {
            val h = size.height * fracs[i]
            drawRoundRect(
                color = color.copy(alpha = alphas[i]),
                topLeft = Offset(i * pitch, size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2)
            )
        }
    }
}

/** Wi-Fi do Pixel: ponto + 2 arcos grossos de pontas redondas; o sinal acende de dentro para fora. */
@Composable
private fun WifiIcon(level: Int, color: Color) {
    val innerA by animateFloatAsState(if (level >= 2) 1f else 0.3f, tween(250), label = "wifiIn")
    val outerA by animateFloatAsState(if (level >= 3) 1f else 0.3f, tween(250), label = "wifiOut")
    Canvas(Modifier.size(width = 19.dp, height = 13.5.dp)) {
        val sw = 2.6.dp.toPx()
        val dotR = 1.7.dp.toPx()
        val cx = size.width / 2
        val cy = size.height - dotR - 0.1.dp.toPx()
        drawCircle(color, radius = dotR, center = Offset(cx, cy))
        val arcs = listOf(5.6.dp.toPx() to innerA, 10.2.dp.toPx() to outerA)
        for ((r, a) in arcs) {
            drawArc(
                color = color.copy(alpha = a),
                startAngle = -142.5f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}

/** Alto-falante cortado (silencioso), como no Pixel. */
@Composable
private fun SpeakerOffIcon(color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = 13.dp)) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.06f, h * 0.38f)
            lineTo(w * 0.26f, h * 0.38f)
            lineTo(w * 0.48f, h * 0.18f)
            lineTo(w * 0.48f, h * 0.82f)
            lineTo(w * 0.26f, h * 0.62f)
            lineTo(w * 0.06f, h * 0.62f)
            close()
        }
        drawPath(body, color)
        drawPath(body, color, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.46f - w * 0.3f, h * 0.5f - w * 0.3f),
            size = Size(w * 0.6f, w * 0.6f),
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.08f, h * 0.08f), Offset(w * 0.94f, h * 0.94f), w * 0.12f, StrokeCap.Round)
    }
}

/** Runa do Bluetooth; quando há dispositivo conectado, ganha um ponto de cada lado (como no Pixel). */
@Composable
private fun BluetoothIcon(connected: Boolean, color: Color) {
    Canvas(Modifier.size(width = 13.dp, height = 14.dp)) {
        val w = size.width
        val h = size.height
        val rw = 7.dp.toPx()
        val ox = (w - rw) / 2
        val rune = Path().apply {
            moveTo(ox + rw * 0.05f, h * 0.30f)
            lineTo(ox + rw * 0.95f, h * 0.72f)
            lineTo(ox + rw * 0.50f, h * 0.97f)
            lineTo(ox + rw * 0.50f, h * 0.03f)
            lineTo(ox + rw * 0.95f, h * 0.28f)
            lineTo(ox + rw * 0.05f, h * 0.70f)
        }
        drawPath(
            rune, color,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (connected) {
            val r = 1.0.dp.toPx()
            drawCircle(color, r, Offset(r, h / 2))
            drawCircle(color, r, Offset(w - r, h / 2))
        }
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
    Canvas(Modifier.size(13.dp)) {
        val w = size.width
        val sw = 1.9.dp.toPx()
        drawCircle(color, radius = w / 2 - sw / 2, style = Stroke(width = sw))
        drawLine(color, Offset(w * 0.27f, w * 0.5f), Offset(w * 0.73f, w * 0.5f), sw, StrokeCap.Round)
    }
}

@Composable
private fun KeyIcon(color: Color) {
    Canvas(Modifier.size(width = 15.dp, height = 10.dp)) {
        val w = size.width
        val h = size.height
        val sw = h * 0.22f
        val r = h * 0.3f
        drawCircle(color, radius = r, center = Offset(r + sw / 2, h / 2), style = Stroke(width = sw))
        drawLine(color, Offset(2 * r + sw, h / 2), Offset(w - sw / 2, h / 2), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h / 2), Offset(w * 0.68f, h * 0.9f), sw, StrokeCap.Round)
        drawLine(color, Offset(w - sw / 2, h / 2), Offset(w - sw / 2, h * 0.9f), sw, StrokeCap.Round)
    }
}

/** Bateria estilo Pixel: corpo arredondado, parte vazia cinza, número escuro dentro e terminal à direita. */
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

    Canvas(Modifier.size(width = 31.dp, height = 15.dp)) {
        val h = size.height
        val nubW = 1.8.dp.toPx()
        val nubH = 5.5.dp.toPx()
        val bodyW = size.width - nubW - 1.2.dp.toPx()
        val track = base.copy(alpha = 0.35f)

        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, bodyW, h, CornerRadius(h * 0.26f))) }
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
        // Na parte vazia (cinza) o número é sempre escuro, como no Pixel.
        clipRect(fw, 0f, bodyW, h) {
            drawBatteryGlyphs(paint, label, showText, charging, DarkInk, bodyW)
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
        paint.textSize = 13.sp.toPx()
        paint.color = color.toArgb()
        val y = h / 2 - (paint.ascent() + paint.descent()) / 2
        drawIntoCanvas { it.nativeCanvas.drawText(label, textCenter, y, paint) }
    }
}
