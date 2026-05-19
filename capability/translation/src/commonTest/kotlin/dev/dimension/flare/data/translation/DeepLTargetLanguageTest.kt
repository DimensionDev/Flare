package dev.dimension.flare.data.translation

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLTargetLanguageTest {
    @Test
    fun `maps Chinese locales to DeepL supported values`() {
        assertEquals("ZH-HANS", "zh-CN".toDeepLTargetLanguage())
        assertEquals("ZH-HANS", "zh_Hans".toDeepLTargetLanguage())
        assertEquals("ZH-HANS", "ZH-CH".toDeepLTargetLanguage())
        assertEquals("ZH-HANT", "zh-TW".toDeepLTargetLanguage())
        assertEquals("ZH-HANT", "zh-Hant".toDeepLTargetLanguage())
        assertEquals("ZH", "zh".toDeepLTargetLanguage())
    }

    @Test
    fun `normalizes regional locales that DeepL does not accept directly`() {
        assertEquals("JA", "ja-JP".toDeepLTargetLanguage())
        assertEquals("DE", "de-DE".toDeepLTargetLanguage())
        assertEquals("EN-US", "en".toDeepLTargetLanguage())
        assertEquals("EN-GB", "en-GB".toDeepLTargetLanguage())
        assertEquals("PT-BR", "pt-BR".toDeepLTargetLanguage())
        assertEquals("PT-PT", "pt-PT".toDeepLTargetLanguage())
    }
}
