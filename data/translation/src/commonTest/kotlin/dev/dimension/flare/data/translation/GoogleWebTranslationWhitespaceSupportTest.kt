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

    @Test
    fun normalizeExpandedInternalNewlines_collapsesUnexpectedBlankLines() {
        assertEquals(
            "甲段\n乙标记\n丙值\n丁标记\n戊值\n己尾部",
            GoogleWebTranslationWhitespaceSupport.normalizeExpandedInternalNewlines(
                sourceText = "line-a\nlabel-b\nvalue-c\nlabel-d\nvalue-e\nline-f",
                translatedText = "甲段\n\n乙标记\n\n丙值\n\n丁标记\n\n戊值\n\n己尾部",
            ),
        )
    }

    @Test
    fun normalizeExpandedInternalNewlines_keepsExistingParagraphBreaks() {
        assertEquals(
            "第一段\n\n第二段",
            GoogleWebTranslationWhitespaceSupport.normalizeExpandedInternalNewlines(
                sourceText = "First paragraph\n\nSecond paragraph",
                translatedText = "第一段\n\n第二段",
            ),
        )
    }
}
