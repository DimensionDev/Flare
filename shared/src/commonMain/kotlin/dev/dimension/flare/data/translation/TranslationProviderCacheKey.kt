package dev.dimension.flare.data.translation

import dev.dimension.flare.data.datastore.model.AppSettings

internal fun AppSettings.translationProviderCacheKey(): String = translateConfig.provider.cacheKey()

internal fun AppSettings.TranslateConfig.Provider.cacheKey(): String =
    when (this) {
        AppSettings.TranslateConfig.Provider.AI -> "ai"
        AppSettings.TranslateConfig.Provider.Google -> "google"
    }
