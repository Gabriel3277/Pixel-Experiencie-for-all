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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
    val hasAccess = remember(refresh) { StatusBar.hasNotificationAccess(ctx) }
    var mute by remember(refresh) { mutableStateOf(StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_MUTE, true)) }
    var vibrate by remember(refresh) { mutableStateOf(StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_VIBRATE, true)) }
    var pct by remember(refresh) { mutableStateOf(StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_BATTERY_PCT, true)) }
    var notifMode by remember(refresh) { mutableIntStateOf(StatusBarPrefs.int(ctx, StatusBarPrefs.NOTIF_MODE, 0)) }
    var iconMode by remember(refresh) { mutableIntStateOf(StatusBarPrefs.iconMode(ctx)) }

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

        SectionTitle("Ícones de notificação")
        RadioRow("Todas", "Mostrar todos os ícones de notificação", notifMode == 0) {
            notifMode = 0; StatusBarPrefs.setInt(ctx, StatusBarPrefs.NOTIF_MODE, 0)
        }
        RadioRow("Só prioridade", "Mostrar apenas notificações de prioridade", notifMode == 1) {
            notifMode = 1; StatusBarPrefs.setInt(ctx, StatusBarPrefs.NOTIF_MODE, 1)
        }
        RadioRow("Nenhuma", "Não mostrar ícones de notificação", notifMode == 2) {
            notifMode = 2; StatusBarPrefs.setInt(ctx, StatusBarPrefs.NOTIF_MODE, 2)
        }
        ActionRow(
            title = "Acesso às notificações",
            subtitle = if (hasAccess) "Concedido" else "Necessário para mostrar os ícones. Toque para conceder"
        ) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        SectionTitle("Ícones do sistema")
        SwitchRow("Mudo", "Mostrar quando o aparelho está no silencioso", mute) {
            mute = it; StatusBarPrefs.set(ctx, StatusBarPrefs.SHOW_MUTE, it)
        }
        SwitchRow("Vibrar", "Mostrar quando o aparelho está no modo vibrar", vibrate) {
            vibrate = it; StatusBarPrefs.set(ctx, StatusBarPrefs.SHOW_VIBRATE, it)
        }
        SwitchRow("Porcentagem da bateria", "Mostrar o nível dentro da pílula", pct) {
            pct = it; StatusBarPrefs.set(ctx, StatusBarPrefs.SHOW_BATTERY_PCT, it)
        }

        SectionTitle("Cor dos ícones")
        RadioRow("Automática", "Escuros se o papel de parede for claro", iconMode == 0) {
            iconMode = 0; StatusBarPrefs.setInt(ctx, StatusBarPrefs.ICON_MODE, 0)
        }
        RadioRow("Claros", "Brancos, para fundos escuros", iconMode == 1) {
            iconMode = 1; StatusBarPrefs.setInt(ctx, StatusBarPrefs.ICON_MODE, 1)
        }
        RadioRow("Escuros", "Pretos, para telas claras", iconMode == 2) {
            iconMode = 2; StatusBarPrefs.setInt(ctx, StatusBarPrefs.ICON_MODE, 2)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun RowCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}

@Composable
private fun TitleSubtitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowCard(onClick = { onChange(!checked) }) {
        TitleSubtitle(title, subtitle, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    RowCard(onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(14.dp))
        TitleSubtitle(title, subtitle, Modifier.weight(1f))
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    RowCard(onClick = onClick) {
        TitleSubtitle(title, subtitle, Modifier.weight(1f))
    }
}
