import os, sys

R = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/PixelExperienceForAll")
B = R + "/lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen"


def patch(path, edits):
    s = open(path, encoding="utf-8").read()
    for marker, old, new in edits:
        if marker in s:
            continue  # já aplicado
        assert s.count(old) == 1, f"{path}: trecho ausente ou duplicado: {old[:60]!r}"
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)


# ---------- 1) novo arquivo ----------
AOD = r'''package com.pixelexperience.forall.lockscreen.ui.aod

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.data.ClockAlign
import com.pixelexperience.forall.lockscreen.data.ClockColor
import com.pixelexperience.forall.lockscreen.data.LockPalette
import com.pixelexperience.forall.lockscreen.data.LockSettings
import com.pixelexperience.forall.lockscreen.notifications.LockNotification
import com.pixelexperience.forall.lockscreen.ui.clock.LockClock
import com.pixelexperience.forall.lockscreen.ui.rememberBatteryText
import com.pixelexperience.forall.lockscreen.ui.theme.DateFont
import com.pixelexperience.forall.lockscreen.ui.theme.lockColorScheme

/** Compartilhado entre o serviço (SCREEN_OFF) e a Activity. */
object AodState {
    /** true enquanto a tela está acesa por causa do AOD: o próximo SCREEN_OFF vem dele e não abre outro AOD. */
    @Volatile var screenIsAod = false
}

private const val MAX_ICONS = 5

/**
 * Modo de espera no estilo Pixel: fundo preto, o mesmo LockClock da tela de bloqueio (sem alterações),
 * ícones monocromáticos dos apps com notificação e bateria só quando carregando ou baixa.
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
    // mesmas cores do relógio da lockscreen (esquema escuro do wallpaper)
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
    // um ícone por app; só recalcula quando a lista muda
    val apps = remember(notifications, settings.hideOngoing) {
        notifications.filter { it.icon != null && !(settings.hideOngoing && it.ongoing) }.distinctBy { it.packageName }
    }
    val battery = rememberBatteryText()
    // textos de carga contêm "·"; sem carga, só aparece com 20% ou menos
    val showBattery = remember(battery) {
        battery != null && (battery.contains('·') || (Regex("\\d+").find(battery)?.value?.toIntOrNull() ?: 100) <= 20)
    }
    val top = (LocalConfiguration.current.screenHeightDp * settings.clockY).dp
    val start = settings.clockAlign == ClockAlign.START

    Box(
        modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { onWake() } }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(top))
            LockClock(
                style = settings.clockStyle, scale = settings.clockScale, align = settings.clockAlign,
                tint = tint, tint2 = tint2,
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
                    if (apps.size > MAX_ICONS) {
                        Text("+${apps.size - MAX_ICONS}", style = TextStyle(fontFamily = DateFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tint))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (showBattery && battery != null) {
                Text(battery, style = TextStyle(fontFamily = DateFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tint))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
'''
os.makedirs(B + "/ui/aod", exist_ok=True)
open(B + "/ui/aod/AodContent.kt", "w", encoding="utf-8").write(AOD)

# ---------- 2) LockSettings: aodEnabled ----------
patch(B + "/data/LockSettings.kt", [
    ("val aodEnabled", "    val showWeather: Boolean = true,\n)",
     "    val showWeather: Boolean = true,\n    val aodEnabled: Boolean = true,\n)"),
    ("K.aod", '    val weather = booleanPreferencesKey("show_weather")\n',
     '    val weather = booleanPreferencesKey("show_weather")\n    val aod = booleanPreferencesKey("aod_enabled")\n'),
    ("aodEnabled = this[K.aod]", "        showWeather = this[K.weather] ?: d.showWeather,\n",
     "        showWeather = this[K.weather] ?: d.showWeather,\n        aodEnabled = this[K.aod] ?: d.aodEnabled,\n"),
    ("this[K.aod] = s.aodEnabled", "    this[K.weather] = s.showWeather\n",
     "    this[K.weather] = s.showWeather\n    this[K.aod] = s.aodEnabled\n"),
])

# ---------- 3) LockScreenContent: reaproveita o texto de bateria ----------
patch(B + "/ui/LockScreenContent.kt", [
    ("internal fun rememberBatteryText", "private fun rememberBatteryText()", "internal fun rememberBatteryText()"),
])

# ---------- 4) Service ----------
patch(B + "/service/LockScreenService.kt", [
    ("import com.pixelexperience.forall.lockscreen.ui.aod.AodState",
     "import com.pixelexperience.forall.lockscreen.ui.LockScreenActivity\n",
     "import com.pixelexperience.forall.lockscreen.ui.LockScreenActivity\nimport com.pixelexperience.forall.lockscreen.ui.aod.AodState\n"),
    ("showLock(aod)",
     "            if (intent.action == Intent.ACTION_SCREEN_OFF) showLock()\n",
     "            if (intent.action == Intent.ACTION_SCREEN_OFF) {\n"
     "                // o SCREEN_OFF que encerra um AOD não abre outro (senão a tela nunca apagaria)\n"
     "                val aod = LockSettingsRepository.get(context).settings.value.aodEnabled && !AodState.screenIsAod\n"
     "                showLock(aod)\n"
     "            }\n"),
    ("private fun showLock(aod: Boolean", "    private fun showLock() {\n", "    private fun showLock(aod: Boolean = false) {\n"),
    ("AodState.screenIsAod = aod",
     "        if (!LockSettingsRepository.get(this).settings.value.enabled) return\n",
     "        if (!LockSettingsRepository.get(this).settings.value.enabled) return\n        AodState.screenIsAod = aod\n"),
    ("EXTRA_AOD, aod",
     "Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION\n                )",
     "Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION\n                ).putExtra(LockScreenActivity.EXTRA_AOD, aod)"),
])

# ---------- 5) Activity ----------
METHODS = '''    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyAod(intent.getBooleanExtra(EXTRA_AOD, false))
    }

    /** AOD em LCD: tela acesa em brilho mínimo por AOD_WINDOW_MS; depois libera e o timeout do sistema apaga. */
    private fun applyAod(on: Boolean) {
        handler.removeCallbacks(endAod)
        aod.value = on
        if (on) {
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = 0.01f }
            handler.postDelayed(endAod, AOD_WINDOW_MS)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            setTurnScreenOn(false)
        }
    }

    companion object {
        const val EXTRA_AOD = "pxe_aod"
        private const val AOD_WINDOW_MS = 20_000L
    }

'''
patch(B + "/ui/LockScreenActivity.kt", [
    ("import com.pixelexperience.forall.lockscreen.ui.aod.AodContent",
     "import com.pixelexperience.forall.lockscreen.ui.theme.LockTheme\n",
     "import com.pixelexperience.forall.lockscreen.ui.theme.LockTheme\n"
     "import androidx.compose.animation.Crossfade\nimport androidx.compose.animation.core.tween\n"
     "import android.os.Handler\nimport android.os.Looper\nimport android.view.WindowManager\n"
     "import com.pixelexperience.forall.lockscreen.data.LockPalette\n"
     "import com.pixelexperience.forall.lockscreen.ui.aod.AodContent\n"
     "import com.pixelexperience.forall.lockscreen.ui.aod.AodState\n"),
    ("private val aod = mutableStateOf",
     "    private val notifAccess = mutableStateOf(true)\n",
     "    private val notifAccess = mutableStateOf(true)\n"
     "    private val aod = mutableStateOf(false)\n"
     "    private val handler = Handler(Looper.getMainLooper())\n"
     "    // fim da janela do AOD: só solta a tela; o timeout do sistema apaga (o conteúdo continua preto)\n"
     "    private val endAod = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }\n"),
    ("applyAod(intent?.getBooleanExtra",
     "        setShowWhenLocked(true)\n",
     "        setShowWhenLocked(true)\n        applyAod(intent?.getBooleanExtra(EXTRA_AOD, false) == true)\n"),
    ("override fun onNewIntent", "    override fun onResume() {\n", METHODS + "    override fun onResume() {\n"),
    ("Crossfade(targetState",
     "                LockScreenContent(\n                    settings = settings,\n",
     "                Crossfade(targetState = aod.value, animationSpec = tween(280), label = \"aod\") { isAod ->\n"
     "                if (isAod) AodContent(\n"
     "                    settings = settings,\n"
     "                    notifications = notifs,\n"
     "                    palette = wallpaper?.palette ?: LockPalette.Default,\n"
     "                    onWake = { AodState.screenIsAod = false; applyAod(false) },\n"
     "                ) else LockScreenContent(\n                    settings = settings,\n"),
    ("                }\n            }\n        }\n    }\n\n    override fun onNewIntent",
     "                    onRequestUnlock = { cb -> unlock(onFail = { cb(false) }) },\n                )\n",
     "                    onRequestUnlock = { cb -> unlock(onFail = { cb(false) }) },\n                )\n                }\n"),
    ("handler.removeCallbacks(endAod)\n        runCatching",
     "        runCatching { unregisterReceiver(userPresent) }\n",
     "        handler.removeCallbacks(endAod)\n        runCatching { unregisterReceiver(userPresent) }\n"),
])
print("OK")
