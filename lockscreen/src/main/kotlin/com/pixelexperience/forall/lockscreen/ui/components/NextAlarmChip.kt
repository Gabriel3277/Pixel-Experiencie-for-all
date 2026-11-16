package com.pixelexperience.forall.lockscreen.ui.components

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pixelexperience.forall.lockscreen.data.ClockAlign
import com.pixelexperience.forall.lockscreen.ui.theme.UiFont
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun readNext(ctx: Context): Long? =
    ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime

private fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** "07:30", "Amanhã 07:30" ou "Seg 07:30", respeitando 12h/24h do sistema. */
private fun label(ctx: Context, t: Long): String {
    val loc = Locale.getDefault()
    val pt = loc.language == "pt"
    val pattern = DateFormat.getBestDateTimePattern(loc, if (DateFormat.is24HourFormat(ctx)) "Hm" else "hm")
    val time = SimpleDateFormat(pattern, loc).format(Date(t))
    val diff = Math.round((dayStart(t) - dayStart(System.currentTimeMillis())) / 86_400_000.0)
    return when {
        diff <= 0L -> time
        diff == 1L -> (if (pt) "Amanhã " else "Tomorrow ") + time
        else -> SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEE"), loc).format(Date(t))
            .replaceFirstChar { it.uppercase() } + " " + time
    }
}

/**
 * Próximo alarme (AlarmManager.getNextAlarmClock, sem permissão extra). Atualiza pelo broadcast oficial
 * ACTION_NEXT_ALARM_CLOCK_CHANGED e some quando não há alarme.
 */
@Composable
fun NextAlarmChip(tint: Color, align: ClockAlign, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var next by remember { mutableStateOf(readNext(ctx)) }
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { next = readNext(c) }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        next = readNext(ctx)
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    val t = next ?: return
    val start = align == ClockAlign.START
    Row(
        modifier.fillMaxWidth().padding(start = if (start) 34.dp else 28.dp, end = 28.dp, top = 4.dp),
        horizontalArrangement = if (start) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Alarm, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label(ctx, t), style = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = tint))
    }
}
