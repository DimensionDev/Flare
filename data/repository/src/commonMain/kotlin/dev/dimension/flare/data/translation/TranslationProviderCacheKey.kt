package dev.dimension.flare.data.translation

import dev.dimension.flare.data.datastore.model.AppSettings

public fun AppSettings.translationProviderCacheKey(): String = translateConfig.provider.cacheKey()

public fun AppSettings.TranslateConfig.Provider.cacheKey(): String =
    when (this) {
        AppSettings.TranslateConfig.Provider.AI -> "ai"
        AppSettings.TranslateConfig.Provider.GoogleWeb -> "google-web"
        is AppSettings.TranslateConfig.Provider.DeepL -> "deepl:${if (usePro) "pro" else "free"}"
        is AppSettings.TranslateConfig.Provider.GoogleCloud -> "google-cloud:v2"
        is AppSettings.TranslateConfig.Provider.LibreTranslate -> "libretranslate:${baseUrl.trimEnd('/').lowercase()}"
    }
