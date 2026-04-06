package dev.dimension.flare.data.translation

import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.datastore.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal object TranslationSettingsSupport {
    fun displayOptionsFlow(appDataStore: AppDataStore): Flow<TranslationDisplayOptions> =
        appDataStore.appSettingsStore.data
            .map(::displayOptions)
            .distinctUntilChanged()

    fun displayOptions(settings: AppSettings): TranslationDisplayOptions =
        TranslationDisplayOptions(
            translationEnabled = true,
            autoDisplayEnabled = settings.translateConfig.preTranslate,
            providerCacheKey = settings.translationProviderCacheKey(),
        )
}

internal object TranslationPromptFormatter {
    fun buildTranslatePrompt(
        settings: AppSettings,
        targetLanguage: String,
        sourceTemplate: String,
    ): String =
        settings.aiConfig.translatePrompt
            .ifBlank {
                AiPromptDefaults.TRANSLATE_PROMPT
            }.replace("{target_language}", targetLanguage)
            .replace("{source_text}", sourceTemplate)
}

internal object TranslationResponseSanitizer {
    fun clean(content: String): String =
        content
            .removePrefix("```json")
            .removePrefix("```html")
            .removePrefix("```xml")
            .removePrefix("```markup")
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
}
