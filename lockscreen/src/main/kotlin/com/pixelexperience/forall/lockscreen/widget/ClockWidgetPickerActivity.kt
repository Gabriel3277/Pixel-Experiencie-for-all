package com.pixelexperience.forall.lockscreen.widget

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ProviderItem(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
)

/**
 * Seleção do widget de relógio: lista os widgets instalados, faz o vínculo
 * (autorização do sistema), abre a configuração se existir e salva a escolha.
 * Abrir com: startActivity(Intent(ctx, ClockWidgetPickerActivity::class.java))
 */
class ClockWidgetPickerActivity : ComponentActivity() {

    private lateinit var controller: ClockWidgetController
    private var pendingId = ClockWidgetStore.INVALID
    private var pendingProvider: ComponentName? = null
    private var message by mutableStateOf<String?>(null)
    private var currentLabel by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ClockWidgetController.get(this)

        savedInstanceState?.let {
            pendingId = it.getInt(KEY_PENDING_ID, ClockWidgetStore.INVALID)
            pendingProvider = it.getString(KEY_PENDING_PROVIDER)
                ?.let { s -> ComponentName.unflattenFromString(s) }
        }
        refreshCurrent()

        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                val items by produceState(initialValue = emptyList<ProviderItem>()) {
                    value = withContext(Dispatchers.Default) { loadItems() }
                }
                PickerScreen(
                    items = items,
                    currentLabel = currentLabel,
                    message = message,
                    onPick = ::onPick,
                    onRemove = ::onRemove,
                    onConfigure = ::onConfigure,
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_ID, pendingId)
        outState.putString(KEY_PENDING_PROVIDER, pendingProvider?.flattenToString())
    }

    override fun onDestroy() {
        if (isFinishing) discardPending()
        super.onDestroy()
    }

    private fun loadItems(): List<ProviderItem> {
        val pm = packageManager
        return controller.installedProviders().map { info ->
            val label = runCatching { info.loadLabel(pm) }.getOrNull()
                .orEmpty().ifBlank { info.provider.shortClassName }
            val app = runCatching {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(info.provider.packageName, 0).loadLabel(pm).toString()
            }.getOrDefault(info.provider.packageName)
            ProviderItem(info, label, app)
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    private fun refreshCurrent() {
        currentLabel = controller.selectedInfo()?.let { runCatching { it.loadLabel(packageManager) }.getOrNull() }
    }

    private fun onPick(info: AppWidgetProviderInfo) {
        message = null
        discardPending()
        val id = controller.allocateId()
        pendingId = id
        pendingProvider = info.provider
        if (controller.bindIfAllowed(id, info.provider)) {
            afterBind()
        } else {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(controller.bindIntent(id, info.provider), REQ_BIND)
            } catch (e: Exception) {
                fail("Não foi possível pedir autorização do widget.")
            }
        }
    }

    private fun afterBind() {
        val id = pendingId
        val info = controller.infoFor(id)
        if (info == null) {
            fail("Widget indisponível.")
            return
        }
        if (info.configure != null) {
            try {
                controller.startConfigure(this, id, REQ_CONFIGURE)
            } catch (e: Exception) {
                fail("Não foi possível abrir a configuração do widget.")
            }
        } else {
            commitPending()
        }
    }

    private fun commitPending() {
        val provider = pendingProvider
        val id = pendingId
        if (provider == null || id == ClockWidgetStore.INVALID) {
            fail("Seleção inválida.")
            return
        }
        controller.commit(id, provider)
        pendingId = ClockWidgetStore.INVALID
        pendingProvider = null
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun onConfigure() {
        message = null
        val id = controller.selectedId()
        if (controller.infoFor(id)?.configure == null) {
            message = "Este widget não tem configuração."
            return
        }
        try {
            controller.startConfigure(this, id, REQ_RECONFIG)
        } catch (e: Exception) {
            message = "Não foi possível abrir a configuração."
        }
    }

    private fun onRemove() {
        discardPending()
        controller.remove()
        refreshCurrent()
        message = "Relógio removido."
    }

    private fun fail(text: String?) {
        discardPending()
        message = text
    }

    private fun discardPending() {
        if (pendingId != ClockWidgetStore.INVALID) controller.deleteId(pendingId)
        pendingId = ClockWidgetStore.INVALID
        pendingProvider = null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND ->
                if (resultCode == RESULT_OK) afterBind() else fail("Autorização negada.")
            REQ_RECONFIG -> refreshCurrent()
            REQ_CONFIGURE ->
                if (resultCode == RESULT_OK || controller.infoFor(pendingId) != null) commitPending() else fail("Configuração cancelada.")
        }
    }

    private companion object {
        const val REQ_BIND = 7101
        const val REQ_CONFIGURE = 7102
        const val REQ_RECONFIG = 7103
        const val KEY_PENDING_ID = "pending_id"
        const val KEY_PENDING_PROVIDER = "pending_provider"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScreen(
    items: List<ProviderItem>,
    currentLabel: String?,
    message: String?,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onRemove: () -> Unit,
    onConfigure: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.label.contains(query, true) || it.appLabel.contains(query, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relógio da tela de bloqueio") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Voltar") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (currentLabel != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Atual: $currentLabel", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = onConfigure) { Text("Configurar") }
                        TextButton(onClick = onRemove) { Text("Remover") }
                    }
                }
            }
            if (message != null) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar widget ou app") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item.info) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PreviewImage(item.info, Modifier.size(72.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text(item.appLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(info: AppWidgetProviderInfo, modifier: Modifier) {
    val ctx = LocalContext.current
    val drawable = remember(info) {
        runCatching { info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0) }.getOrNull()
    }
    AndroidView(
        factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageDrawable(drawable) },
        modifier = modifier,
    )
}
