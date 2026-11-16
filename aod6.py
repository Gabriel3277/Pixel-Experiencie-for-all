import os, sys

R = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/PixelExperienceForAll")
B = R + "/lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen"
PKG = "com.pixelexperience.forall.lockscreen"


def patch(path, edits):
    s = open(path, encoding="utf-8").read()
    for marker, old, new in edits:
        if marker in s:
            continue
        assert s.count(old) == 1, f"{os.path.basename(path)}: trecho ausente ou duplicado: {old[:70]!r}"
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(text)


# =====================================================================
# 1) Bateria: normal / carregando / fraca
# =====================================================================
write(B + "/ui/components/BatteryStatus.kt", r'''package com.pixelexperience.forall.lockscreen.ui.components

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
''')

# =====================================================================
# 2) Próximo alarme
# =====================================================================
write(B + "/ui/components/NextAlarmChip.kt", r'''package com.pixelexperience.forall.lockscreen.ui.components

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
''')

# =====================================================================
# 3) Estilos de wallpaper
# =====================================================================
write(B + "/ui/components/WallpaperFitStyle.kt", r'''package com.pixelexperience.forall.lockscreen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.pixelexperience.forall.lockscreen.data.WallpaperFit

/** Preencher e Topo cortam (Topo ancora no alto, bom para retratos); Ajustar mostra a imagem inteira. */
fun WallpaperFit.contentScale(): ContentScale = if (this == WallpaperFit.FIT) ContentScale.Fit else ContentScale.Crop

fun WallpaperFit.alignment(): Alignment = if (this == WallpaperFit.TOP) Alignment.TopCenter else Alignment.Center

/** Fundo do modo Ajustar: a própria imagem ampliada e desfocada, para as bordas não ficarem pretas. */
@Composable
fun FitBackdrop(image: ImageBitmap, preview: Boolean) {
    Image(
        bitmap = image, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.2f
            scaleY = 1.2f
            renderEffect = if (!preview) BlurEffect(48f, 48f, TileMode.Clamp) else null
        },
    )
}
''')

# =====================================================================
# 4) LockSettings: WallpaperFit + showBatteryPercent
# =====================================================================
patch(B + "/data/LockSettings.kt", [
    ("enum class WallpaperFit",
     'enum class ClockColor(val label: String) { DYNAMIC("Dinâmica"), ACCENT("Destaque"), WHITE("Branca") }\n',
     'enum class ClockColor(val label: String) { DYNAMIC("Dinâmica"), ACCENT("Destaque"), WHITE("Branca") }\n'
     'enum class WallpaperFit(val label: String) { FILL("Preencher"), FIT("Ajustar"), TOP("Topo") }\n'),
    ("val showBatteryPercent: Boolean",
     "    val aodEnabled: Boolean = true,\n",
     "    val aodEnabled: Boolean = true,\n    val wallpaperFit: WallpaperFit = WallpaperFit.FILL,\n    val showBatteryPercent: Boolean = true,\n"),
    ("K.wpFit", '    val aod = booleanPreferencesKey("aod_enabled")\n',
     '    val aod = booleanPreferencesKey("aod_enabled")\n    val wpFit = stringPreferencesKey("wallpaper_fit")\n    val batPct = booleanPreferencesKey("show_battery_pct")\n'),
    ("enumOf(K.wpFit", "        aodEnabled = this[K.aod] ?: d.aodEnabled,\n",
     "        aodEnabled = this[K.aod] ?: d.aodEnabled,\n        wallpaperFit = enumOf(K.wpFit, d.wallpaperFit),\n        showBatteryPercent = this[K.batPct] ?: d.showBatteryPercent,\n"),
    ("this[K.wpFit] =", "    this[K.aod] = s.aodEnabled\n",
     "    this[K.aod] = s.aodEnabled\n    this[K.wpFit] = s.wallpaperFit.name\n    this[K.batPct] = s.showBatteryPercent\n"),
])

# =====================================================================
# 5) LockScreenContent: integra tudo
# =====================================================================
BAT_OLD = '''                        val bat = rememberBatteryText()
                        if (bat != null) {
                            Text(
                                bat, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = UiFont, fontSize = 15.sp, letterSpacing = 0.1.sp,
                                    color = Color.White.copy(alpha = 0.92f),
                                    shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 1f), 12f),
                                ),
                            )
                        }
'''
patch(B + "/ui/LockScreenContent.kt", [
    ("components.BatteryStatus",
     "import com.pixelexperience.forall.lockscreen.ui.components.FingerprintHint\n",
     "import com.pixelexperience.forall.lockscreen.ui.components.FingerprintHint\n"
     "import com.pixelexperience.forall.lockscreen.ui.components.BatteryStatus\n"
     "import com.pixelexperience.forall.lockscreen.ui.components.NextAlarmChip\n"
     "import com.pixelexperience.forall.lockscreen.ui.components.FitBackdrop\n"
     "import com.pixelexperience.forall.lockscreen.ui.components.alignment\n"
     "import com.pixelexperience.forall.lockscreen.ui.components.contentScale\n"
     "import com.pixelexperience.forall.lockscreen.data.WallpaperFit\n"
     "import androidx.compose.ui.platform.LocalView\n"),
    ("val view = LocalView.current",
     "    val density = LocalDensity.current\n    val palette = wallpaper?.palette ?: LockPalette.Default\n",
     "    val density = LocalDensity.current\n    val view = LocalView.current\n    val palette = wallpaper?.palette ?: LockPalette.Default\n"),
    # haptic de confirmação no desbloqueio por gesto (o sistema não vibra nesse caminho)
    ("fun confirmHaptic()",
     "    suspend fun settle(vy: Float) {\n",
     "    fun confirmHaptic() {\n"
     "        view.performHapticFeedback(\n"
     "            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM\n"
     "            else android.view.HapticFeedbackConstants.CONTEXT_CLICK\n"
     "        )\n"
     "    }\n\n"
     "    suspend fun settle(vy: Float) {\n"),
    ("} else confirmHaptic()",
     "                if (!ok) {\n                    unlocking = false\n                    scope.launch { drag.animateTo(0f, LockMotion.bouncy()) }\n                }\n",
     "                if (!ok) {\n                    unlocking = false\n                    scope.launch { drag.animateTo(0f, LockMotion.bouncy()) }\n                } else confirmHaptic()\n"),
    # wallpaper: estilo
    ("FitBackdrop(w.image",
     "                    // camada 1: wallpaper com blur leve + profundidade (escala cresce ao arrastar)\n",
     "                    if (settings.wallpaperFit == WallpaperFit.FIT) FitBackdrop(w.image, preview)\n"
     "                    // camada 1: wallpaper com blur leve + profundidade (escala cresce ao arrastar)\n"),
    ("alignment = settings.wallpaperFit.alignment(),\n                        modifier",
     "                        bitmap = w.image, contentDescription = null, contentScale = ContentScale.Crop,\n                        modifier = Modifier.fillMaxSize().graphicsLayer {",
     "                        bitmap = w.image, contentDescription = null,\n                        contentScale = settings.wallpaperFit.contentScale(), alignment = settings.wallpaperFit.alignment(),\n                        modifier = Modifier.fillMaxSize().graphicsLayer {"),
    ("alignment = settings.wallpaperFit.alignment(),\n                            modifier",
     "                            bitmap = w.image, contentDescription = null, contentScale = ContentScale.Crop,\n                            modifier = Modifier.fillMaxSize()\n                                .graphicsLayer {",
     "                            bitmap = w.image, contentDescription = null,\n                            contentScale = settings.wallpaperFit.contentScale(), alignment = settings.wallpaperFit.alignment(),\n                            modifier = Modifier.fillMaxSize()\n                                .graphicsLayer {"),
    # próximo alarme, logo abaixo do relógio/data
    ("NextAlarmChip(",
     "                )\n                Spacer(Modifier.height(16.dp))\n                MediaPlayerCard(\n",
     "                )\n"
     "                NextAlarmChip(\n"
     "                    tint = clockTint, align = settings.clockAlign,\n"
     "                    modifier = Modifier.layerFx(e1::value, dragFn, 0.35f, 1.4f, distFn),\n"
     "                )\n"
     "                Spacer(Modifier.height(16.dp))\n                MediaPlayerCard(\n"),
    # bateria
    ("BatteryStatus(showPercent", BAT_OLD,
     "                        BatteryStatus(showPercent = settings.showBatteryPercent, scheme = scheme)\n"),
])

# =====================================================================
# 6) FingerprintHint: saída de "sucesso" (expande e some)
# =====================================================================
patch(B + "/ui/components/FingerprintHint.kt", [
    ("import androidx.compose.animation.scaleOut",
     "import androidx.compose.animation.slideInHorizontally\n",
     "import androidx.compose.animation.scaleOut\nimport androidx.compose.animation.slideInHorizontally\n"),
    ("scaleOut(tween(220)", "            exit = fadeOut(tween(150)),\n",
     "            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.6f),\n"),
])

# =====================================================================
# 7) Tela de configurações
# =====================================================================
S = B + "/ui/settings/LockSettingsScreen.kt"
s0 = open(S, encoding="utf-8").read()
imp_anchor = "import com.pixelexperience.forall.lockscreen.data.ShortcutType\n"
assert imp_anchor in s0 or "data.WallpaperFit" in s0, "import ShortcutType não encontrado na tela de configurações"
patch(S, [
    ("data.WallpaperFit", imp_anchor, imp_anchor + "import com.pixelexperience.forall.lockscreen.data.WallpaperFit\n"),
    ("s.wallpaperFit",
     '                    Text(\n                        "As cores do relógio e dos cards seguem o papel de parede escolhido.",\n',
     '                    Text("Apresentação", style = MaterialTheme.typography.labelLarge)\n'
     '                    ChipRow(WallpaperFit.entries, s.wallpaperFit, { it.label }) { v -> set { it.copy(wallpaperFit = v) } }\n'
     '                    Text(\n                        "As cores do relógio e dos cards seguem o papel de parede escolhido.",\n'),
    ("s.showBatteryPercent",
     "                    ChipRow(ShortcutType.entries, s.rightShortcut, { it.label }) { v -> set { it.copy(rightShortcut = v) } }\n",
     "                    ChipRow(ShortcutType.entries, s.rightShortcut, { it.label }) { v -> set { it.copy(rightShortcut = v) } }\n"
     '                    SwitchRow("Mostrar porcentagem da bateria", s.showBatteryPercent) { v -> set { it.copy(showBatteryPercent = v) } }\n'),
])
print("OK")
