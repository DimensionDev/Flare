package dev.dimension.flare.data.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreTranslationContentRulesTest {
    @Test
    fun canonicalTranslationLanguage_normalizesCommonVariants() {
        assertEquals("en", PreTranslationContentRules.canonicalTranslationLanguage("en-US"))
        assertEquals("en", PreTranslationContentRules.canonicalTranslationLanguage("en_GB"))
        assertEquals("zh-hans", PreTranslationContentRules.canonicalTranslationLanguage("zh-CN"))
        assertEquals("zh-hant", PreTranslationContentRules.canonicalTranslationLanguage("zh-TW"))
    }

    @Test
    fun shouldSkipForExcludedSourceLanguage_matchesNormalizedLanguages() {
        assertTrue(
            PreTranslationContentRules.shouldSkipForExcludedSourceLanguage(
                sourceLanguages = listOf("en-US"),
                excludedLanguages = listOf("en"),
            ),
        )
        assertTrue(
            PreTranslationContentRules.shouldSkipForExcludedSourceLanguage(
                sourceLanguages = listOf("zh-CN"),
                excludedLanguages = listOf("zh-Hans"),
            ),
        )
    }

    @Test
    fun shouldSkipForExcludedSourceLanguage_ignoresInvalidOrNonMatchingLanguages() {
        assertFalse(
            PreTranslationContentRules.shouldSkipForExcludedSourceLanguage(
                sourceLanguages = listOf("fr"),
                excludedLanguages = emptyList(),
            ),
        )
        assertFalse(
            PreTranslationContentRules.shouldSkipForExcludedSourceLanguage(
                sourceLanguages = listOf("fr"),
                excludedLanguages = listOf("en", " "),
            ),
        )
    }
}
