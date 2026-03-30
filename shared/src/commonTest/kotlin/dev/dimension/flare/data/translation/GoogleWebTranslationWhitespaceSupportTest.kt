package dev.dimension.flare.data.translation

import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleWebTranslationWhitespaceSupportTest {
    @Test
    fun preserveSourceBoundaryWhitespace_restoresTrailingSpace() {
        assertEquals(
            "你好 ",
            GoogleWebTranslationWhitespaceSupport.preserveSourceBoundaryWhitespace(
                sourceText = "Hello ",
                translatedText = "你好",
            ),
        )
    }

    @Test
    fun preserveSourceBoundaryWhitespace_restoresLeadingAndTrailingNewlines() {
        assertEquals(
            "\n你好\n\n",
            GoogleWebTranslationWhitespaceSupport.preserveSourceBoundaryWhitespace(
                sourceText = "\nHello\n\n",
                translatedText = "你好",
            ),
        )
    }

    @Test
    fun trimBoundaryWhitespace_keepsInnerWhitespace() {
        assertEquals(
            "Hello  world",
            GoogleWebTranslationWhitespaceSupport.trimBoundaryWhitespace("  Hello  world  "),
        )
    }
}
