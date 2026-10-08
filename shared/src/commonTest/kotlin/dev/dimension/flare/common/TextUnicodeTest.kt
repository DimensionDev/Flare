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
}
