package com.pixelexperience.forall.statusbar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class StatusBarSettingsActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)
    private var pendingEnable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            MaterialTheme(colorScheme = colors) {
                SettingsScreen(refresh = refresh, onToggleEnabled = ::onToggleEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingEnable && Settings.canDrawOverlays(this)) {
            pendingEnable = false
            StatusBarPrefs.set(this, StatusBarPrefs.ENABLED, true)
            StatusBar.start(this)
        }
        refresh++
    }

    private fun onToggleEnabled(on: Boolean) {
        if (!on) {
            pendingEnable = false
            StatusBarPrefs.set(this, StatusBarPrefs.ENABLED, false)
            StatusBar.stop(this)
        } else if (Settings.canDrawOverlays(this)) {
            StatusBarPrefs.set(this, StatusBarPrefs.ENABLED, true)
            StatusBar.start(this)
        } else {
            pendingEnable = true
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        refresh++
    }
}

@Composable
private fun SettingsScreen(refresh: Int, onToggleEnabled: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val enabled = remember(refresh) { StatusBarPrefs.bool(ctx, StatusBarPrefs.ENABLED, false) }
    var mute by remember(refresh) { mutableStateOf(StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_MUTE, true)) }
    var vibrate by remember(refresh) { mutableStateOf(StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_VIBRATE, true)) }
    var pct by remember(refresh) { mutableStateOf(StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_BATTERY_PCT, true)) }
    var dark by remember(refresh) { mutableStateOf(StatusBarPrefs.bool(ctx, StatusBarPrefs.DARK_ICONS, false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Barra de status",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SwitchRow(
            title = "Ativar barra de status",
            subtitle = "Desenha a barra por cima da do sistema (precisa da permissão de sobreposição)",
            checked = enabled,
            onChange = onToggleEnabled
        )

        Text(
            text = "Ícones do sistema",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp)
        )

        SwitchRow("Mudo", "Mostrar quando o aparelho está no silencioso", mute) {
            mute = it; StatusBarPrefs.set(ctx, StatusBarPrefs.SHOW_MUTE, it)
        }
        SwitchRow("Vibrar", "Mostrar quando o aparelho está no modo vibrar", vibrate) {
            vibrate = it; StatusBarPrefs.set(ctx, StatusBarPrefs.SHOW_VIBRATE, it)
        }
        SwitchRow("Porcentagem da bateria", "Mostrar o nível dentro da pílula", pct) {
            pct = it; StatusBarPrefs.set(ctx, StatusBarPrefs.SHOW_BATTERY_PCT, it)
        }
        SwitchRow("Ícones escuros", "Use em telas claras", dark) {
            dark = it; StatusBarPrefs.set(ctx, StatusBarPrefs.DARK_ICONS, it)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable { onChange(!checked) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
