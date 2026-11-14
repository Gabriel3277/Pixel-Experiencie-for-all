@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package com.pixelexperience.forall.lockscreen.ui.clock

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import com.pixelexperience.forall.lockscreen.data.WeatherUi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.R
import com.pixelexperience.forall.lockscreen.data.ClockAlign
import com.pixelexperience.forall.lockscreen.data.ClockStyle
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.lockFont
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Relógio no estilo Pixel: data (Google Sans) em cima, números grandes empilhados (hora / minuto)
 * em Google Sans Flex arredondada, sombra suave para integrar ao wallpaper e dígitos que giram individualmente.
 * tint = cor da hora; tint2 = cor dos minutos (tonal, como no Pixel).
 */
@Composable
fun LockClock(
    style: ClockStyle,
    scale: Float,
    align: ClockAlign,
    tint: Color,
    modifier: Modifier = Modifier,
    tint2: Color = tint,
    weather: WeatherUi? = null,
) {
    val ctx = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 30L)
        }
    }
    val cal = Calendar.getInstance().apply { time = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val is24 = DateFormat.is24HourFormat(ctx)
    val hh = if (is24) "%02d".format(h) else (h % 12).let { if (it == 0) 12 else it }.toString()
    val mm = "%02d".format(cal.get(Calendar.MINUTE))
    val date = remember(now) {
        val loc = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(loc, "EEEMMMd")
        SimpleDateFormat(pattern, loc).format(now).replaceFirstChar { it.uppercase() }
    }
    val horizontal = if (align == ClockAlign.START) Alignment.Start else Alignment.CenterHorizontally

    Column(modifier.fillMaxWidth(), horizontalAlignment = horizontal) {
        // data + clima logo abaixo do relógio
        val dateStyle = TextStyle(
            fontFamily = lockFont(500),
            fontSize = (20f * scale.coerceIn(0.9f, 1.3f)).sp,
            letterSpacing = 0.25.sp,
            color = tint.copy(alpha = 0.96f),
            shadow = Shadow(Color.Black.copy(alpha = 0.34f), Offset(0f, 2f), 14f),
        )
        AnimatedContent(
            targetState = style,
            transitionSpec = {
                (fadeIn(LockMotion.effects()) + scaleIn(LockMotion.spatial(), initialScale = 0.85f)) togetherWith
                    (fadeOut(LockMotion.effects()) + scaleOut(LockMotion.effects(), targetScale = 0.92f)) using
                    SizeTransform(clip = false) { _, _ -> LockMotion.spatial() }
            },
            contentAlignment = if (align == ClockAlign.START) Alignment.TopStart else Alignment.TopCenter,
            label = "clockStyle",
        ) { st -> ClockFace(st, hh, mm, scale, horizontal, tint, tint2) }
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = date, style = dateStyle)
            if (weather != null) {
                Spacer(Modifier.width(10.dp))
                Icon(weatherIcon(weather.code, weather.isDay), null, tint = tint, modifier = Modifier.size((22f * scale.coerceIn(0.9f, 1.3f)).dp))
                Spacer(Modifier.width(4.dp))
                Text("${weather.temp}°" + if (weather.fahrenheit) "F" else "", style = dateStyle)
            }
        }
    }
}

private fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    1, 2 -> if (day) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    3, 45, 48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

@Composable
private fun ClockFace(style: ClockStyle, hh: String, mm: String, scale: Float, h: Alignment.Horizontal, tint: Color, tint2: Color) {
    val mid = lerp(tint, tint2, 0.5f)
    when (style) {
        // relógio padrão do Pixel: duas linhas, números grandes e arredondados
        ClockStyle.STACKED -> Column(horizontalAlignment = h) {
            Digits(hh, 118f, 640, tint, scale, colorEnd = mid, width = 106f)
            Digits(mm, 118f, 640, mid, scale, colorEnd = tint2, width = 106f)
        }
        ClockStyle.BOLD -> Column(horizontalAlignment = h) {
            Digits(hh, 118f, 860, tint, scale, colorEnd = mid, width = 122f)
            Digits(mm, 118f, 860, mid, scale, colorEnd = tint2, width = 122f)
        }
        ClockStyle.OUTLINE -> Column(horizontalAlignment = h) {
            Digits(hh, 118f, 760, tint, scale, colorEnd = mid, width = 112f, outline = true)
            Digits(mm, 118f, 760, mid, scale, colorEnd = tint2, width = 112f, outline = true)
        }
        ClockStyle.INFLATE -> FontStack(FontFamily(Font(R.font.inflate)), hh, mm, tint, tint2, scale)
        ClockStyle.GROWTH -> FontStack(FontFamily(Font(R.font.growth)), hh, mm, tint, tint2, scale)
        ClockStyle.ROTATION -> FontStack(FontFamily(Font(R.font.rotation)), hh, mm, tint, tint2, scale)
        ClockStyle.FRAME -> FontStack(FontFamily(Font(R.font.frameweather)), hh, mm, tint, tint2, scale)
        ClockStyle.RUBY -> FontStack(FontFamily(Font(R.font.ruby)), hh, mm, tint, tint2, scale)
        ClockStyle.FLEX -> FontStack(
            FontFamily(Font(R.font.google_sans_flex, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))),
            hh, mm, tint, tint2, scale,
        )
        ClockStyle.INLINE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Digits(hh, 88f, 520, tint, scale, colorEnd = mid)
            Colon(88f, mid, scale)
            Digits(mm, 88f, 520, mid, scale, colorEnd = tint2)
        }
        ClockStyle.COMPACT -> Row(verticalAlignment = Alignment.CenterVertically) {
            Digits(hh, 56f, 600, tint, scale, colorEnd = mid, depth = false)
            Colon(56f, mid, scale)
            Digits(mm, 56f, 600, mid, scale, colorEnd = tint2, depth = false)
        }
    }
}

@Composable
private fun Colon(sizeSp: Float, color: Color, scale: Float) {
    val s = sizeSp * scale
    Canvas(Modifier.padding(horizontal = (s * 0.02f).dp).size(width = (s * 0.16f).dp, height = (s * 0.40f).dp)) {
        val r = size.width / 2f
        val c = color.copy(alpha = 0.88f)
        drawCircle(c, r, Offset(size.width / 2f, size.height * 0.12f))
        drawCircle(c, r, Offset(size.width / 2f, size.height * 0.88f))
    }
}

/** Cada dígito anima sozinho (só o que mudou gira), com mola expressiva. */
@Composable
private fun Digits(
    text: String, sizeSp: Float, weight: Int, color: Color, scale: Float,
    width: Float = 100f, outline: Boolean = false, family: FontFamily? = null, depth: Boolean = true,
    colorEnd: Color? = null,
) {
    val size = (sizeSp * scale).sp
    val base = TextStyle(
        fontFamily = family ?: lockFont(weight, width, 100f),
        fontSize = size,
        lineHeight = size * 0.86f,
        letterSpacing = (-sizeSp * scale * 0.012f).sp,
        color = color,
        drawStyle = if (outline) Stroke(width = 4f) else null,
        shadow = if (depth && !outline) Shadow(Color.Black.copy(alpha = 0.26f), Offset(0f, 6f), 28f) else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    // transição tonal contínua entre hora e minuto (azul -> lilás -> rosa no Dynamic Color)
    val style = if (colorEnd != null && !outline) base.copy(brush = Brush.verticalGradient(listOf(color, colorEnd)), alpha = 1f) else base
    Row {
        text.forEachIndexed { i, c ->
            key(i) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        (slideInVertically(animationSpec = LockMotion.spatial()) { it / 2 } +
                            fadeIn(LockMotion.effects()) + scaleIn(LockMotion.spatial(), initialScale = 0.8f)) togetherWith
                            (slideOutVertically(animationSpec = LockMotion.spatial()) { -it / 2 } +
                                fadeOut(LockMotion.effects()) + scaleOut(LockMotion.effects(), targetScale = 0.8f))
                    },
                    label = "digit",
                ) { ch -> Text(text = ch.toString(), style = style) }
            }
        }
    }
}

@Composable
private fun FontStack(f: FontFamily, hh: String, mm: String, tint: Color, tint2: Color, scale: Float) {
    val mid = lerp(tint, tint2, 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Digits(hh, 92f, 700, tint, scale, colorEnd = mid, family = f)
        Colon(92f, mid, scale)
        Digits(mm, 92f, 700, mid, scale, colorEnd = tint2, family = f)
    }
}
