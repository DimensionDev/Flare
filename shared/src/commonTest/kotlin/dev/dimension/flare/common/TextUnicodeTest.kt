package dev.dimension.flare.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TextUnicodeTest {
    @Test
    fun countsGraphemesAndNormalizesNfc() {
        for (text in listOf("😀", "👍🏽", "🇨🇳", "👨‍👩‍👧‍👦", "🫱🏽‍🫲🏻", "e\u0301", "\u0915\u094d\u0915")) {
            assertEquals(1, text.graphemeCount(), text)
        }
        assertEquals(300, ("a".repeat(299) + "🫱🏽‍🫲🏻").graphemeCount())
        assertEquals(0, "".graphemeCount())
        assertEquals(1, "\r\n".graphemeCount())
        assertEquals(3, "🇨🇳🇺🇸🇯".graphemeCount())
        assertEquals("é", "e\u0301".normalizeNfc())
    }

    @Test
    fun respectsGraphemeBoundaryRules() {
        val cases =
            listOf(
                "\n\u0301" to 2,
                "\u0301\n" to 2,
                "\u1100\u1161\u11A8" to 1,
                "\u0600a" to 1,
                "a\u0903" to 1,
                "\u0915\u094D\u200C\u0915" to 2,
                "a\u0301\u200D❤️" to 2,
                "😀\u0301\u200D😀" to 1,
                "😀\u200D\u200D😀" to 2,
                "🇨\u0301🇳" to 2,
            )
        for ((text, expected) in cases) {
            assertEquals(expected, text.graphemeCount(), text)
        }
    }
}
