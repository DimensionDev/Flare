package dev.dimension.flare.feature.plugin.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.dimension.flare.feature.plugin.manifest.PluginTextResolverV1
import dev.dimension.flare.ui.component.LocalExternalTextResolver

@Composable
public fun ProvidePluginTextResolverV1(content: @Composable () -> Unit) {
    val resolver = remember { PluginTextResolverV1::resolve }
    CompositionLocalProvider(
        LocalExternalTextResolver provides resolver,
        content = content,
    )
}
