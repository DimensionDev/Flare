package dev.dimension.flare.feature.plugin.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.manifest.PluginTextResolverV1
import dev.dimension.flare.ui.component.LocalExternalTextResolver

@Composable
public fun ProvidePluginTextResolverV1(content: @Composable () -> Unit) {
    val subsystem = remember { koinGet<PluginSubsystemV1>() }
    val running by subsystem.running.collectAsState()
    val resolver = remember(running) { { text: dev.dimension.flare.ui.model.UiText.ExternalRef -> PluginTextResolverV1.resolve(text) } }
    CompositionLocalProvider(
        LocalExternalTextResolver provides resolver,
        content = content,
    )
}
