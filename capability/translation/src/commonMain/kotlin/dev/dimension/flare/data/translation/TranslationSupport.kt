package dev.dimension.flare.data.translation

import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.datastore.model.AppSettings

public object TranslationPromptFormatter {
    public fun buildTranslatePrompt(
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

public object TranslationResponseSanitizer {
    public fun clean(content: String): String =
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
