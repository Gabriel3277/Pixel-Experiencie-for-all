package com.pixelexperience.forall.controlcenter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pixelexperience.forall.controlcenter.ControlCenterStatus
import com.pixelexperience.forall.controlcenter.CrashLog
import com.pixelexperience.forall.controlcenter.NotificationStore
import com.pixelexperience.forall.controlcenter.R

/**
 * Tela do módulo dentro do app: mostra o estado real do serviço e da permissão,
 * e leva o usuário às telas do Android onde ele ativa cada uma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCenterScreen(
    moduleEnabled: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var serviceOn by remember { mutableStateOf(ControlCenterStatus.isServiceEnabled(context)) }
    var canWrite by remember { mutableStateOf(ControlCenterStatus.canWriteSettings(context)) }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    var notifOn by remember { mutableStateOf(NotificationStore.hasAccess(context)) }

    // Ao voltar das telas de Configurações, relê o estado real.
    LifecycleResumeEffect(Unit) {
        serviceOn = ControlCenterStatus.isServiceEnabled(context)
        canWrite = ControlCenterStatus.canWriteSettings(context)
        crash = CrashLog.read(context)
        notifOn = NotificationStore.hasAccess(context)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cc_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cc_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!moduleEnabled) {
                InfoCard(
                    title = stringResource(R.string.cc_module_off_title),
                    body = stringResource(R.string.cc_module_off_body),
                    error = true,
                )
            }

            StatusCard(
                title = stringResource(R.string.cc_service_card_title),
                body = stringResource(R.string.cc_service_card_body),
                status = stringResource(if (serviceOn) R.string.cc_service_on else R.string.cc_service_off),
                ok = serviceOn,
                buttonText = stringResource(R.string.cc_open_accessibility),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )

            StatusCard(
                title = stringResource(R.string.cc_write_card_title),
                body = stringResource(R.string.cc_write_card_body),
                status = stringResource(if (canWrite) R.string.cc_write_granted else R.string.cc_write_missing),
                ok = canWrite,
                buttonText = stringResource(R.string.cc_grant),
                showButton = !canWrite,
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )

            StatusCard(
                title = stringResource(R.string.cc_np_card_title),
                body = stringResource(R.string.cc_np_card_body),
                status = stringResource(if (notifOn) R.string.cc_service_on else R.string.cc_service_off),
                ok = notifOn,
                buttonText = stringResource(R.string.cc_np_access_button),
                showButton = !notifOn,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )

            crash?.let { text ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.cc_vp_crash_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SelectionContainer {
                            Text(
                                text = text.take(1800),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("erro", text))
                            }) { Text(stringResource(R.string.cc_vp_crash_copy)) }
                            Button(onClick = {
                                CrashLog.clear(context)
                                crash = null
                            }) { Text(stringResource(R.string.cc_vp_crash_clear)) }
                        }
                    }
                }
            }

            InfoCard(
                title = stringResource(R.string.cc_howto_title),
                body = stringResource(R.string.cc_howto_body),
            )
            InfoCard(
                title = stringResource(R.string.cc_limits_title),
                body = stringResource(R.string.cc_limits_body),
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    status: String,
    ok: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    showButton: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = status, style = MaterialTheme.typography.labelLarge)
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (showButton) {
                Button(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    error: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
