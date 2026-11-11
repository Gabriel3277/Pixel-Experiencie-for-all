package com.pixelexperience.forall.ui.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixelexperience.forall.MainUiState
import com.pixelexperience.forall.R
import com.pixelexperience.forall.core.modules.ModuleState
import com.pixelexperience.forall.core.modules.ModuleStatus
import com.pixelexperience.forall.core.modules.PixelModuleId
import com.pixelexperience.forall.ui.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    onOpenModule: (PixelModuleId) -> Unit,
    onOpenAppearance: () -> Unit,
    onToggleModule: (PixelModuleId, Boolean) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenAppearance) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.appearance_title),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CoreStatusCard(state) }
            item {
                Text(
                    text = stringResource(R.string.home_modules_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
            items(state.modules, key = { it.info.id.name }) { module ->
                ModuleCard(
                    module = module,
                    onClick = { onOpenModule(module.info.id) },
                    onToggle = { onToggleModule(module.info.id, it) },
                )
            }
        }
    }
}

@Composable
private fun CoreStatusCard(state: MainUiState) {
    val available = state.modules.filter { it.info.status == ModuleStatus.AVAILABLE }
    val active = available.count { it.enabled }

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
                text = stringResource(R.string.home_core_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_core_subtitle),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.home_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.home_device, Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.home_modules_active, active, state.modules.size),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ModuleCard(
    module: ModuleState,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val available = module.info.status == ModuleStatus.AVAILABLE

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (available) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (available) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = module.info.id.icon(), contentDescription = null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(module.info.title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(module.info.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(if (available) R.string.status_available else R.string.status_planned),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (available) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (available) {
                Spacer(Modifier.width(12.dp))
                Switch(checked = module.enabled, onCheckedChange = onToggle)
            }
        }
    }
}
