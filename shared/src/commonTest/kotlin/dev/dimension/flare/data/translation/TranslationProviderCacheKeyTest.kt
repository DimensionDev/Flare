package dev.dimension.flare.data.translation

import dev.dimension.flare.data.datastore.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationProviderCacheKeyTest {
    @Test
    fun cacheKeyDifferentiatesDedicatedProviders() {
        assertEquals(
            "google-web",
            AppSettings.TranslateConfig.Provider.GoogleWeb
                .cacheKey(),
        )
        assertEquals(
            "deepl:free",
            AppSettings.TranslateConfig.Provider
                .DeepL(
                    apiKey = "secret",
                    usePro = false,
                ).cacheKey(),
        )
        assertEquals(
            "deepl:pro",
            AppSettings.TranslateConfig.Provider
                .DeepL(
                    apiKey = "secret",
                    usePro = true,
                ).cacheKey(),
        )
        assertEquals(
            "google-cloud:v2",
            AppSettings.TranslateConfig.Provider
                .GoogleCloud(
                    apiKey = "secret",
                ).cacheKey(),
        )
        assertEquals(
            "libretranslate:https://translate.example.com",
            AppSettings.TranslateConfig.Provider
                .LibreTranslate(
                    baseUrl = "https://translate.example.com/",
                    apiKey = "",
                ).cacheKey(),
        )
    }
}
