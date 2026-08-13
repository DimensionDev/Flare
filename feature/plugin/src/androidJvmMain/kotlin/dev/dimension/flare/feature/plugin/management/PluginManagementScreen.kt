package dev.dimension.flare.feature.plugin.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningTypeV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningV1
import dev.dimension.flare.ui.component.resolveText

public data class PluginManagementStringsV1(
    val title: String,
    val install: String,
    val empty: String,
    val restartRequired: String,
    val unverifiedWarning: String,
    val permissions: String,
    val capabilities: String,
    val confirm: String,
    val cancel: String,
    val uninstall: String,
    val cleanup: String,
    val rebuildIndex: String,
    val retry: String,
    val running: String,
    val inactive: String,
    val addedPermissionWarning: String,
    val downgradeWarning: String,
    val changedPackageWarning: String,
    val compatibilityWarning: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PluginManagementScreen(
    state: PluginManagementStateV1,
    strings: PluginManagementStringsV1,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onConfirmInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
    onRetryRuntime: (String) -> Unit,
    onCleanup: () -> Unit,
    onRebuildIndex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.pendingInstall?.let { review ->
        AlertDialog(
            onDismissRequest = onCancelInstall,
            title = { Text("${review.name} ${review.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.unverifiedWarning, color = MaterialTheme.colorScheme.error)
                    if (review.capabilities.isNotEmpty()) {
                        Text(strings.capabilities, style = MaterialTheme.typography.titleSmall)
                        review.capabilities.forEach { capability -> Text("• $capability") }
                    }
                    if (review.permissions.isNotEmpty()) {
                        Text(strings.permissions, style = MaterialTheme.typography.titleSmall)
                        review.permissions.forEach { permission ->
                            Text("• ${permission.origin}${permission.cookieName?.let { ": $it" }.orEmpty()}")
                        }
                    }
                    review.warnings
                        .filterNot { it.type == PluginInstallWarningTypeV1.UnverifiedLocal }
                        .forEach { warning -> Text("• ${warning.message(strings)}") }
                }
            },
            confirmButton = { TextButton(onClick = onConfirmInstall) { Text(strings.confirm) } },
            dismissButton = { TextButton(onClick = onCancelInstall) { Text(strings.cancel) } },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(strings.title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
                actions = { TextButton(onClick = onInstall, enabled = !state.busy) { Text(strings.install) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.requiresRestart) {
                item { Text(strings.restartRequired, color = MaterialTheme.colorScheme.primary) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canRebuildIndex) {
                        TextButton(onClick = onRebuildIndex, enabled = !state.busy) { Text(strings.rebuildIndex) }
                    }
                    TextButton(onClick = onCleanup, enabled = !state.busy && !state.canRebuildIndex) { Text(strings.cleanup) }
                }
            }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            state.issues.forEach { issue -> item { Text(issue.message, color = MaterialTheme.colorScheme.error) } }
            state.runtimeIssues.forEach { issue ->
                item(key = "runtime:${issue.pluginId}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${issue.pluginId}: ${issue.code}",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { onRetryRuntime(issue.pluginId) }, enabled = !state.busy) {
                            Text(strings.retry)
                        }
                    }
                }
            }
            if (state.plugins.isEmpty()) item { Text(strings.empty) }
            items(state.plugins, key = PluginManagementItemV1::pluginId) { plugin ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plugin.nameText.resolveText(), style = MaterialTheme.typography.titleMedium)
                                Text("${plugin.platformId} · ${plugin.version}", style = MaterialTheme.typography.bodySmall)
                                Text(if (plugin.running) strings.running else strings.inactive, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = plugin.enabled,
                                enabled = !state.busy,
                                onCheckedChange = { onSetEnabled(plugin.pluginId, it) },
                            )
                        }
                        TextButton(onClick = { onUninstall(plugin.pluginId) }, enabled = !state.busy) {
                            Text(strings.uninstall)
                        }
                    }
                }
            }
            if (state.busy) item { CircularProgressIndicator() }
        }
    }
}

private fun PluginInstallWarningV1.message(strings: PluginManagementStringsV1): String {
    val prefix =
        when (type) {
            PluginInstallWarningTypeV1.UnverifiedLocal -> strings.unverifiedWarning
            PluginInstallWarningTypeV1.AddedPermission -> strings.addedPermissionWarning
            PluginInstallWarningTypeV1.Downgrade -> strings.downgradeWarning
            PluginInstallWarningTypeV1.SameVersionDifferentHash -> strings.changedPackageWarning
            PluginInstallWarningTypeV1.Compatibility -> strings.compatibilityWarning
        }
    return detail?.let { "$prefix: $it" } ?: prefix
}
