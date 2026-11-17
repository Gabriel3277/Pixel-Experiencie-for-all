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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sqrt

private val DarkInk = Color(0xFF1B1B1F)
private const val MAX_NOTIF_ICONS = 4

/** Espessura única de traço para todos os indicadores de linha. */
private val IconStroke = 2.0.dp

/** Altura padrão dos indicadores do sistema (alinhamento vertical uniforme). */
private val SystemIconHeight = 16.dp

private val NotifIconSize = 16.dp

/**
 * Fonte do relógio e da bateria: Google Sans do sistema, se existir (Pixels e ROMs que a trazem);
 * senão Quicksand embutida (assets/quicksand.ttf, peso 600); senão a fonte padrão.
 */
private fun loadClockTypeface(context: Context): Typeface {
    val googleSans = Typeface.create("google-sans", Typeface.NORMAL)
    if (googleSans != Typeface.DEFAULT) {
        return Typeface.create(googleSans, 500, false)
    }
    return runCatching {
        Typeface.Builder(context.assets, "quicksand.ttf")
            .setFontVariationSettings("'wght' 600")
            .build()
    }.getOrNull() ?: Typeface.DEFAULT
}

@Composable
internal fun StatusBarContent(s: StatusBarState) {
    val fg by animateColorAsState(
        targetValue = if (s.darkIcons) DarkInk else Color.White,
        animationSpec = tween(250),
        label = "fg"
    )
    val context = LocalContext.current
    val clockTypeface = remember { loadClockTypeface(context) }

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
        // Esquerda: relógio
        ClockText(s.time, fg, clockTypeface, !s.darkIcons)

        // Centro: ícones de notificação. Ocupa só o espaço que sobra entre o relógio e os
        // indicadores do sistema e corta o excesso, então nunca há sobreposição.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                notifs.take(MAX_NOTIF_ICONS).forEach { icon ->
                    Image(
                        bitmap = icon.bitmap,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(fg),
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.size(NotifIconSize)
                    )
                }
                if (notifs.size > MAX_NOTIF_ICONS) {
                    Canvas(Modifier.size(5.dp)) { drawCircle(fg) }
                }
            }
        }

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
            Slot(true, 9.dp) { BatteryPill(s, clockTypeface) }
        }
    }
}

/**
 * Relógio desenhado direto no Canvas: o centro vertical dos dígitos é o centro da barra,
 * igual ao dos ícones, sem depender das métricas da fonte (que deslocavam o texto para cima).
 */
@Composable
private fun ClockText(text: String, color: Color, typeface: Typeface, shadow: Boolean) {
    val density = LocalDensity.current
    val paint = remember(typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
            fontFeatureSettings = "tnum"
        }
    }
    val ref = remember { android.graphics.Rect() }
    val widthDp = with(density) {
        paint.textSize = 18.sp.toPx()
        paint.measureText(text.ifEmpty { "00:00" }).toDp()
    }
    Canvas(Modifier.size(width = widthDp, height = 24.dp)) {
        paint.textSize = 18.sp.toPx()
        paint.color = color.toArgb()
        if (shadow) {
            paint.setShadowLayer(3f, 0f, 1f, 0x66000000)
        } else {
            paint.clearShadowLayer()
        }
        paint.getTextBounds("0", 0, 1, ref)
        val y = size.height / 2 - ref.exactCenterY()
        drawIntoCanvas { it.nativeCanvas.drawText(text, 0f, y, paint) }
    }
}

/** Entrada e saída discretas: some e aparece com fade e deslocamento horizontal. */
@Composable
private fun Slot(visible: Boolean, gap: Dp = 7.dp, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
        exit = fadeOut(tween(140)) + shrinkHorizontally(tween(140))
    ) {
        Box(Modifier.padding(start = gap), contentAlignment = Alignment.Center) { content() }
    }
}

// ---------------------------------------------------------------------------
// Geometria auxiliar (formas preenchidas com cantos arredondados, sem strokes sobrepostos)
// ---------------------------------------------------------------------------

/** Ponto a distância [d] de [from] na direção de [to] (no máximo até a metade do segmento). */
private fun towards(from: Offset, to: Offset, d: Float): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len == 0f) return from
    val k = (d / len).coerceAtMost(0.5f)
    return Offset(from.x + dx * k, from.y + dy * k)
}

/** Curva quadrática escrita como cúbica (compatível com qualquer versão do Compose). */
private fun Path.quad(p0: Offset, q: Offset, p2: Offset) {
    cubicTo(
        p0.x + (q.x - p0.x) * 2f / 3f, p0.y + (q.y - p0.y) * 2f / 3f,
        p2.x + (q.x - p2.x) * 2f / 3f, p2.y + (q.y - p2.y) * 2f / 3f,
        p2.x, p2.y
    )
}

/** Polígono com todos os cantos arredondados em raio [d]. */
private fun roundedPolygon(pts: List<Offset>, d: Float): Path {
    val path = Path()
    val n = pts.size
    for (i in 0 until n) {
        val cur = pts[i]
        val a = towards(cur, pts[(i + n - 1) % n], d)
        val b = towards(cur, pts[(i + 1) % n], d)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quad(a, cur, b)
    }
    path.close()
    return path
}

// ---------------------------------------------------------------------------
// Indicadores do sistema
// ---------------------------------------------------------------------------

/** Sinal móvel do Pixel: 4 barras verticais crescentes; as barras sem sinal ficam translúcidas. */
@Composable
private fun SignalIcon(level: Int, color: Color) {
    val frac by animateFloatAsState(level.coerceIn(0, 4) / 4f, tween(200), label = "signal")
    Canvas(Modifier.size(width = 17.dp, height = SystemIconHeight)) {
        val barW = 3.dp.toPx()
        val gap = (size.width - 4 * barW) / 3f
        val heights = floatArrayOf(0.38f, 0.60f, 0.80f, 1f)
        for (i in 0 until 4) {
            val lit = (frac * 4f - i).coerceIn(0f, 1f)
            val bh = size.height * heights[i]
            drawRoundRect(
                color = color.copy(alpha = 0.3f + 0.7f * lit),
                topLeft = Offset(i * (barW + gap), size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2)
            )
        }
    }
}

/** Wi-Fi do Pixel: ponto + dois arcos de traço grosso e pontas redondas; o nível acende de baixo para cima. */
@Composable
private fun WifiIcon(level: Int, color: Color) {
    val frac by animateFloatAsState(level.coerceIn(0, 4) / 4f, tween(250), label = "wifi")
    Canvas(Modifier.size(width = 18.dp, height = SystemIconHeight)) {
        val sw = 2.3.dp.toPx()
        val dotR = 1.7.dp.toPx()
        val cx = size.width / 2
        val cy = size.height - dotR - 0.4.dp.toPx()
        fun lit(threshold: Int) = (frac * 4f - (threshold - 1)).coerceIn(0f, 1f)
        fun alpha(threshold: Int) = 0.3f + 0.7f * lit(threshold)

        // Arco externo (nível 4), arco interno (nível 2), ponto (nível 1).
        for ((radiusDp, threshold) in listOf(11.dp.value to 4, 6.2.dp.value to 2)) {
            val r = radiusDp.dp.toPx()
            drawArc(
                color = color.copy(alpha = alpha(threshold)),
                startAngle = -140f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        drawCircle(color.copy(alpha = alpha(1)), radius = dotR, center = Offset(cx, cy))
    }
}

/** Alto-falante cortado (silencioso). */
@Composable
private fun SpeakerOffIcon(color: Color) {
    Canvas(Modifier.size(SystemIconHeight)) {
        val u = size.width / 14f
        val sw = IconStroke.toPx()
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val body = roundedPolygon(
            listOf(p(1f, 5f), p(3.6f, 5f), p(7.2f, 1.8f), p(7.2f, 12.2f), p(3.6f, 9f), p(1f, 9f)),
            0.9f * u
        )
        drawPath(body, color)
        drawArc(
            color = color,
            startAngle = -42f,
            sweepAngle = 84f,
            useCenter = false,
            topLeft = p(2.6f, 3f),
            size = Size(8f * u, 8f * u),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        drawLine(color, p(1.8f, 1.8f), p(12.2f, 12.2f), sw, StrokeCap.Round)
    }
}

/** Runa do Bluetooth; quando há dispositivo conectado, ganha um ponto de cada lado. */
@Composable
private fun BluetoothIcon(connected: Boolean, color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = SystemIconHeight)) {
        val w = size.width
        val h = size.height
        val sw = IconStroke.toPx()
        val rw = 8.dp.toPx()
        val ox = (w - rw) / 2
        val top = sw / 2
        val bottom = h - sw / 2
        val span = bottom - top
        val rune = Path().apply {
            moveTo(ox + rw * 0.05f, top + span * 0.30f)
            lineTo(ox + rw * 0.95f, top + span * 0.72f)
            lineTo(ox + rw * 0.50f, bottom)
            lineTo(ox + rw * 0.50f, top)
            lineTo(ox + rw * 0.95f, top + span * 0.28f)
            lineTo(ox + rw * 0.05f, top + span * 0.70f)
        }
        drawPath(
            rune, color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (connected) {
            val r = 1.0.dp.toPx()
            drawCircle(color, r, Offset(r, h / 2))
            drawCircle(color, r, Offset(w - r, h / 2))
        }
    }
}

/** Vibrar: aparelho com marcas de vibração nas laterais. */
@Composable
private fun VibrateIcon(color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = SystemIconHeight)) {
        val w = size.width
        val h = size.height
        val sw = IconStroke.toPx()
        val pw = 5.4.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset((w - pw) / 2, sw / 2),
            size = Size(pw, h - sw),
            cornerRadius = CornerRadius(1.6.dp.toPx()),
            style = Stroke(width = sw)
        )
        drawLine(color, Offset(sw / 2, h * 0.30f), Offset(sw / 2, h * 0.70f), sw, StrokeCap.Round)
        drawLine(color, Offset(w - sw / 2, h * 0.30f), Offset(w - sw / 2, h * 0.70f), sw, StrokeCap.Round)
    }
}

/** Não Perturbe: círculo com traço central. */
@Composable
private fun DndIcon(color: Color) {
    Canvas(Modifier.size(SystemIconHeight)) {
        val w = size.width
        val sw = IconStroke.toPx()
        drawCircle(color, radius = w / 2 - sw / 2, style = Stroke(width = sw))
        drawLine(color, Offset(w * 0.28f, w * 0.5f), Offset(w * 0.72f, w * 0.5f), sw, StrokeCap.Round)
    }
}

/** VPN: chave. */
@Composable
private fun KeyIcon(color: Color) {
    Canvas(Modifier.size(width = 15.dp, height = 10.dp)) {
        val w = size.width
        val h = size.height
        val sw = IconStroke.toPx()
        val r = (h - sw) / 2
        drawCircle(color, radius = r, center = Offset(r + sw / 2, h / 2), style = Stroke(width = sw))
        drawLine(color, Offset(2 * r + sw, h / 2), Offset(w - sw / 2, h / 2), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h / 2), Offset(w * 0.68f, h - sw / 2), sw, StrokeCap.Round)
        drawLine(color, Offset(w - sw / 2, h / 2), Offset(w - sw / 2, h - sw / 2), sw, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// Bateria
// ---------------------------------------------------------------------------

private class BatteryMetrics(val body: Float, val bolt: Float, val gap: Float)

/** Bateria em cápsula (estilo Pixel): parte vazia cinza, número centralizado e terminal discreto. */
@Composable
private fun BatteryPill(s: StatusBarState, typeface: Typeface) {
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

    val density = LocalDensity.current
    val paint = remember(typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
    }
    val bounds = remember { android.graphics.Rect() }
    val label = s.batteryPct.coerceIn(0, 100).toString()
    val showText = s.showBatteryPct
    val charging = s.charging

    // A largura segue o conteúdo; 1 e 2 dígitos usam a mesma largura para não "pular".
    val metrics = with(density) {
        paint.textSize = 15.sp.toPx()
        val ref = if (showText) paint.measureText(if (label.length >= 3) "100" else "88") else 0f
        val bolt = if (charging) 6.5.dp.toPx() else 0f
        val gap = if (charging && showText) 1.5.dp.toPx() else 0f
        val pad = 6.dp.toPx()
        val minW = (if (showText) 27.dp else 22.dp).toPx()
        BatteryMetrics(max(minW, ref + bolt + gap + 2 * pad), bolt, gap)
    }
    val bodyDp = with(density) { metrics.body.toDp() }

    Canvas(Modifier.size(width = bodyDp + 2.8.dp, height = 18.dp)) {
        val h = size.height
        val bodyW = metrics.body
        val nubGap = 1.dp.toPx()
        val nubW = 1.8.dp.toPx()
        val nubH = 6.dp.toPx()
        val track = base.copy(alpha = 0.35f)

        val body = Path().apply {
            addRoundRect(RoundRect(0f, 0f, bodyW, h, CornerRadius(h * 0.42f)))
        }
        drawPath(body, track)
        drawRoundRect(
            color = if (pct > 0.97f) fill else track,
            topLeft = Offset(bodyW + nubGap, (h - nubH) / 2),
            size = Size(nubW, nubH),
            cornerRadius = CornerRadius(nubW / 2)
        )

        val fw = bodyW * pct
        clipPath(body) { drawRect(fill, size = Size(fw, h)) }
        clipRect(0f, 0f, fw, h) {
            drawBatteryGlyphs(paint, bounds, label, showText, charging, textOnFill, metrics)
        }
        // Na parte vazia (cinza) o número é sempre escuro, como no Pixel.
        clipRect(fw, 0f, bodyW, h) {
            drawBatteryGlyphs(paint, bounds, label, showText, charging, DarkInk, metrics)
        }
    }
}

private fun DrawScope.drawBatteryGlyphs(
    paint: Paint,
    bounds: android.graphics.Rect,
    label: String,
    showText: Boolean,
    charging: Boolean,
    color: Color,
    m: BatteryMetrics
) {
    val h = size.height
    paint.textSize = 15.sp.toPx()
    val textW = if (showText) paint.measureText(label) else 0f
    val groupW = m.bolt + m.gap + textW
    val start = (m.body - groupW) / 2

    if (charging) {
        val bw = m.bolt
        val bh = h * 0.62f
        val oy = (h - bh) / 2
        fun b(x: Float, y: Float) = Offset(start + bw * x, oy + bh * y)
        val bolt = roundedPolygon(
            listOf(
                b(0.62f, 0f), b(0.05f, 0.56f), b(0.45f, 0.56f),
                b(0.36f, 1f), b(0.95f, 0.42f), b(0.55f, 0.42f)
            ),
            0.6.dp.toPx()
        )
        drawPath(bolt, color)
    }

    if (showText) {
        paint.color = color.toArgb()
        paint.getTextBounds(label, 0, label.length, bounds)
        // Centraliza a altura real dos dígitos (não a caixa da fonte) no meio da cápsula.
        val y = h / 2 - bounds.exactCenterY()
        val cx = start + m.bolt + m.gap + textW / 2
        drawIntoCanvas { it.nativeCanvas.drawText(label, cx, y, paint) }
    }
}
