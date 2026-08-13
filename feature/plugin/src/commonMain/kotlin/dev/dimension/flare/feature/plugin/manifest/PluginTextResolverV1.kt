package dev.dimension.flare.feature.plugin.manifest

import dev.dimension.flare.common.Locale
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.ui.model.UiText

/** Resolves plugin-owned text without copying catalogs into every UiText value. */
public object PluginTextResolverV1 {
    public fun resolve(text: UiText.ExternalRef): String =
        runCatching {
            val stateStore = koinGet<PluginSubsystemV1>().stateStore
            val installed =
                stateStore.running.plugins[text.namespace]?.installed
                    ?: stateStore.desired.value.plugins[text.namespace]
                    ?: return text.fallbackText()
            PluginCatalogBundleV1(
                pluginId = installed.pluginId,
                defaultLocale = installed.manifest.defaultLocale,
                catalogs = installed.catalogs,
            ).resolve(text, Locale.language)
        }.getOrElse { text.fallbackText() }
}
