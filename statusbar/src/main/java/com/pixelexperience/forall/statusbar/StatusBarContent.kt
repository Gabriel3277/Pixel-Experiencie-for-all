package com.pixelexperience.forall.statusbar

import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkInk = Color(0xFF1B1B1F)

@Composable
internal fun StatusBarContent(s: StatusBarState) {
    val fg = if (s.darkIcons) DarkInk else Color.White
    val shadow = if (s.darkIcons) null else Shadow(Color(0x55000000), Offset(0f, 1f), 3f)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = s.time,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(shadow = shadow)
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (s.vpn) KeyIcon(fg)
            if (s.dnd) DndIcon(fg)
            if (s.ringerMode == AudioManager.RINGER_MODE_SILENT && s.showMute) BellOffIcon(fg)
            if (s.ringerMode == AudioManager.RINGER_MODE_VIBRATE && s.showVibrate) VibrateIcon(fg)
            SignalIcon(s.signalLevel, fg)
            if (s.wifi) WifiIcon(s.wifiLevel, fg)
            BatteryPill(s)
        }
    }
}

@Composable
private fun SignalIcon(level: Int, color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = 12.dp)) {
        val bars = 4
        val gap = size.width * 0.09f
        val bw = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = size.height * (0.38f + 0.62f * i / (bars - 1))
            drawRoundRect(
                color = color.copy(alpha = if (i < level) 1f else 0.3f),
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2)
            )
        }
    }
}

@Composable
private fun WifiIcon(level: Int, color: Color) {
    val arcs = (level * 3 + 3) / 4
    Canvas(Modifier.size(width = 15.dp, height = 12.dp)) {
        val sw = size.height * 0.14f
        val cx = size.width / 2
        val cy = size.height - sw
        drawCircle(color, radius = sw * 0.8f, center = Offset(cx, cy))
        val radii = floatArrayOf(size.height * 0.28f, size.height * 0.5f, size.height * 0.72f)
        for (i in radii.indices) {
            val r = radii[i]
            drawArc(
                color = color.copy(alpha = if (i < arcs) 1f else 0.3f),
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun BellOffIcon(color: Color) {
    Canvas(Modifier.size(11.dp)) {
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
        drawPath(bell, color, style = Stroke(width = sw))
        drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.9f))
        drawLine(color, Offset(w * 0.04f, h * 0.06f), Offset(w * 0.96f, h * 0.96f), sw, StrokeCap.Round)
    }
}

@Composable
private fun VibrateIcon(color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = 12.dp)) {
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
    Canvas(Modifier.size(11.dp)) {
        val w = size.width
        val sw = w * 0.13f
        drawCircle(color, radius = w / 2 - sw / 2, style = Stroke(width = sw))
        drawLine(color, Offset(w * 0.27f, w * 0.5f), Offset(w * 0.73f, w * 0.5f), sw, StrokeCap.Round)
    }
}

@Composable
private fun KeyIcon(color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
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

/** Pílula de bateria do Android 17: número dentro, cor muda ao carregar, economia e nível baixo. */
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
    val base = if (dark) DarkInk else Color.White
    val fill = accent ?: base
    val textOnFill = if (accent == null && dark) Color.White else DarkInk
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(Modifier.size(width = 30.dp, height = 15.dp)) {
        val w = size.width
        val h = size.height
        val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(h / 2))) }
        drawPath(pill, base.copy(alpha = 0.35f))
        val fw = w * s.batteryPct.coerceIn(0, 100) / 100f
        clipPath(pill) { drawRect(fill, size = Size(fw, h)) }

        if (s.showBatteryPct) {
            paint.textSize = 10.sp.toPx()
            val y = h / 2 - (paint.ascent() + paint.descent()) / 2
            val text = s.batteryPct.toString()
            clipRect(0f, 0f, fw, h) {
                drawIntoCanvas {
                    paint.color = textOnFill.toArgb()
                    it.nativeCanvas.drawText(text, w / 2, y, paint)
                }
            }
            clipRect(fw, 0f, w, h) {
                drawIntoCanvas {
                    paint.color = base.toArgb()
                    it.nativeCanvas.drawText(text, w / 2, y, paint)
                }
            }
        }
    }
}
