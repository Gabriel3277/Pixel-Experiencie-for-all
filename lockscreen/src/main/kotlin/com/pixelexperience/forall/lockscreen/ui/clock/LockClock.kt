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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.data.ClockAlign
import com.pixelexperience.forall.lockscreen.data.ClockStyle
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.lockFont
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LockClock(
    style: ClockStyle,
    scale: Float,
    align: ClockAlign,
    tint: Color,
    modifier: Modifier = Modifier,
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
        AnimatedContent(
            targetState = style,
            transitionSpec = {
                (fadeIn(LockMotion.effects()) + scaleIn(LockMotion.bouncy(), initialScale = 0.82f)) togetherWith
                    (fadeOut(LockMotion.effects()) + scaleOut(LockMotion.effects(), targetScale = 0.9f)) using
                    SizeTransform(clip = false) { _, _ -> LockMotion.spatial() }
            },
            contentAlignment = if (align == ClockAlign.START) Alignment.TopStart else Alignment.TopCenter,
            label = "clockStyle",
        ) { st -> ClockFace(st, hh, mm, scale, horizontal, tint) }
        Text(
            text = date,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(
                fontFamily = lockFont(500), fontSize = (17f * scale.coerceAtLeast(0.85f)).sp, color = tint.copy(alpha = 0.92f),
            ),
        )
    }
}

@Composable
private fun ClockFace(style: ClockStyle, hh: String, mm: String, scale: Float, h: Alignment.Horizontal, tint: Color) {
    when (style) {
        ClockStyle.STACKED -> Column(horizontalAlignment = h) {
            Digits(hh, 118f, 500, tint, scale)
            Digits(mm, 118f, 500, tint.copy(alpha = 0.85f), scale)
        }
        ClockStyle.BOLD -> Column(horizontalAlignment = h) {
            Digits(hh, 126f, 800, tint, scale)
            Digits(mm, 126f, 800, tint.copy(alpha = 0.85f), scale)
        }
        ClockStyle.OUTLINE -> Column(horizontalAlignment = h) {
            Digits(hh, 118f, 700, tint, scale, outline = true)
            Digits(mm, 118f, 700, tint, scale, outline = true)
        }
        ClockStyle.INLINE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Digits(hh, 84f, 400, tint, scale)
            Colon(84f, tint, scale)
            Digits(mm, 84f, 400, tint, scale)
        }
        ClockStyle.COMPACT -> Row(verticalAlignment = Alignment.CenterVertically) {
            Digits(hh, 54f, 600, tint, scale)
            Colon(54f, tint, scale)
            Digits(mm, 54f, 600, tint, scale)
        }
    }
}

@Composable
private fun Colon(sizeSp: Float, color: Color, scale: Float) {
    val size = (sizeSp * scale).sp
    Text(":", style = TextStyle(fontFamily = lockFont(400), fontSize = size, color = color.copy(alpha = 0.8f)))
}

@Composable
private fun Digits(text: String, sizeSp: Float, weight: Int, color: Color, scale: Float, outline: Boolean = false) {
    val size = (sizeSp * scale).sp
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(animationSpec = LockMotion.spatial()) { it / 3 } + fadeIn(LockMotion.effects())) togetherWith
                (slideOutVertically(animationSpec = LockMotion.spatial()) { -it / 3 } + fadeOut(LockMotion.effects()))
        },
        label = "digits",
    ) { t ->
        Text(
            text = t,
            style = TextStyle(
                fontFamily = lockFont(weight),
                fontSize = size,
                lineHeight = size * 0.88f,
                letterSpacing = (-2).sp,
                color = color,
                drawStyle = if (outline) Stroke(width = 4f) else null,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
        )
    }
}
