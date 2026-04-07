package dev.dimension.flare.data.translation

import dev.dimension.flare.data.datastore.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationPromptFormatterTest {
    @Test
    fun buildTranslatePrompt_usesPlainTemplateDefaultPromptWhenBlank() {
        val prompt =
            TranslationPromptFormatter.buildTranslatePrompt(
                settings =
                    AppSettings(
                        version = "",
                    ),
                targetLanguage = "zh-CN",
                sourceTemplate = "<<<B0>>>\n{{T0}}Hello\n<<<E0>>>",
            )

        assertTrue(prompt.contains("Translate the following template"))
        assertTrue(prompt.contains("<<<B0>>>"))
        assertTrue(prompt.contains("Copying the original source text"))
        assertTrue(prompt.contains("Example output"))
        assertFalse(prompt.contains("Translate the following JSON"))
        assertFalse(prompt.contains("\"blocks\""))
    }
}
