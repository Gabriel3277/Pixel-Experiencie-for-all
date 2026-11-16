package com.pixelexperience.forall.lockscreen.ui.aod

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveCircleOutline
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pixelexperience.forall.lockscreen.data.ClockAlign
import com.pixelexperience.forall.lockscreen.data.ClockColor
import com.pixelexperience.forall.lockscreen.data.ClockStyle
import com.pixelexperience.forall.lockscreen.data.LockPalette
import com.pixelexperience.forall.lockscreen.data.LockSettings
import com.pixelexperience.forall.lockscreen.notifications.LockNotification
import com.pixelexperience.forall.lockscreen.ui.clock.LockClock
import com.pixelexperience.forall.lockscreen.ui.rememberBatteryText
import com.pixelexperience.forall.lockscreen.ui.theme.DateFont
import com.pixelexperience.forall.lockscreen.ui.theme.lockColorScheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compartilhado entre o serviço (SCREEN_OFF) e a Activity. */
object AodState {
    /** true enquanto a tela está acesa por causa do AOD: o próximo SCREEN_OFF vem dele e não abre outro AOD. */
    @Volatile var screenIsAod = false
}

private const val MAX_ICONS = 5

/**
 * AOD no estilo Pixel: fundo preto, data pequena no canto superior esquerdo (com Não perturbar),
 * relógio nativo em contorno (nunca o widget do Pix), ícones monocromáticos dos apps e bateria embaixo.
 * Sem Glassmorphism, sem animação contínua. Toque = abre a tela de bloqueio completa.
 */
@Composable
fun AodContent(
    settings: LockSettings,
    notifications: List<LockNotification>,
    palette: LockPalette,
    onWake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = remember(palette.accent) { lockColorScheme(palette.accent) }
    val tint = when (settings.clockColor) {
        ClockColor.DYNAMIC -> scheme.onPrimaryContainer
        ClockColor.ACCENT -> scheme.primary
        ClockColor.WHITE -> Color.White
    }
    val tint2 = when (settings.clockColor) {
        ClockColor.DYNAMIC -> scheme.onTertiaryContainer
        ClockColor.ACCENT -> scheme.tertiary
        ClockColor.WHITE -> Color.White
    }
    val apps = remember(notifications, settings.hideOngoing) {
        notifications.filter { it.icon != null && !(settings.hideOngoing && it.ongoing) }.distinctBy { it.packageName }
    }
    val battery = rememberBatteryText()
    val showBattery = remember(battery) {
        battery != null && (battery.contains('·') || (Regex("\\d+").find(battery)?.value?.toIntOrNull() ?: 100) <= 20)
    }
    val dnd = rememberDnd()
    val date = remember {
        val loc = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEEMMMd"), loc).format(Date()).replaceFirstChar { it.uppercase() }
    }
    val small = TextStyle(fontFamily = DateFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tint)
    val top = (LocalConfiguration.current.screenHeightDp * settings.clockY).dp
    val start = settings.clockAlign == ClockAlign.START

    Box(
        modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { onWake() } }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            // data no canto superior esquerdo + Não perturbar, como no Pixel
            Row(Modifier.align(Alignment.Start).padding(start = 28.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(date, style = small)
                if (dnd) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.RemoveCircleOutline, null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(top))
            // relógio nativo em contorno; o widget do Pix não é usado no AOD
            LockClock(
                style = ClockStyle.OUTLINE, scale = settings.clockScale, align = settings.clockAlign,
                tint = tint, tint2 = tint2, showDate = false,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                    horizontalArrangement = if (start) Arrangement.spacedBy(12.dp) else Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    apps.take(MAX_ICONS).forEach { n ->
                        val ic = n.icon ?: return@forEach
                        Image(ic, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(tint))
                    }
                    if (apps.size > MAX_ICONS) Text("+${apps.size - MAX_ICONS}", style = small)
                }
            }
            Spacer(Modifier.weight(1f))
            if (showBattery && battery != null) Text(battery, style = small)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Não perturbar ativo (qualquer filtro diferente de "todas"); só reage a mudanças, sem polling. */
@Composable
private fun rememberDnd(): Boolean {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(false) }
    DisposableEffect(ctx) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        fun read() {
            val f = nm.currentInterruptionFilter
            on = f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        read()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read()
        }
        ContextCompat.registerReceiver(
            ctx, r, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return on
}
