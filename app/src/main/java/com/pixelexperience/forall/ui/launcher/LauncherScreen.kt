@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.pixelexperience.forall.ui.launcher

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pixelexperience.forall.R
import com.pixelexperience.forall.launcher.InstalledLauncher
import com.pixelexperience.forall.launcher.LauncherBridge
import com.pixelexperience.forall.launcher.LauncherStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    moduleEnabled: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val bridge = remember { LauncherBridge(context) }

    // Reescaneia ao voltar da tela de configurações do sistema.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val status by produceState<LauncherStatus?>(initialValue = null, refresh) {
        value = withContext(Dispatchers.Default) { bridge.scan() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.launcher_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!moduleEnabled) {
                NoteCard(stringResource(R.string.launcher_module_off))
            }

            val current = status
            if (current != null) {
                val lawnchair = current.lawnchair
                if (lawnchair == null) {
                    NoteCard(stringResource(R.string.launcher_not_found))
                } else {
                    LawnchairCard(
                        launcher = lawnchair,
                        enabled = moduleEnabled,
                        onOpen = { start(context, bridge.openIntent(lawnchair)) },
                        onSetDefault = {
                            if (!start(context, bridge.defaultHomeSettingsIntent(), quiet = true)) {
                                start(context, bridge.fallbackSettingsIntent())
                            }
                        },
                    )
                }

                if (current.launchers.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.launcher_installed_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                    current.launchers.forEach { LauncherRow(it) }
                }

                Text(
                    text = stringResource(R.string.launcher_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LawnchairCard(
    launcher: InstalledLauncher,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.launcher_found),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = launcher.label, style = MaterialTheme.typography.headlineSmall)
            Text(text = launcher.packageName, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (launcher.isDefault) R.string.launcher_is_default else R.string.launcher_not_default,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpen,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.launcher_open)) }
                if (!launcher.isDefault) {
                    FilledTonalButton(
                        onClick = onSetDefault,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.launcher_set_default)) }
                }
            }
        }
    }
}

@Composable
private fun LauncherRow(launcher: InstalledLauncher) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = launcher.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = launcher.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (launcher.isDefault) {
                Text(
                    text = stringResource(R.string.launcher_default_tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Abre a intent; devolve false se não houver app para tratá-la. */
private fun start(context: Context, intent: Intent, quiet: Boolean = false): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        if (!quiet) {
            Toast.makeText(context, R.string.launcher_open_failed, Toast.LENGTH_SHORT).show()
        }
        false
    }
