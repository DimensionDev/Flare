package dev.dimension.flare.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TextUnicodeTest {
    @Test
    fun countsGraphemesAndNormalizesNfc() {
        for (text in listOf("😀", "👍🏽", "🇨🇳", "👨‍👩‍👧‍👦", "e\u0301")) {
            assertEquals(1, text.graphemeCount(), text)
        }
        assertEquals("é", "e\u0301".normalizeNfc())
    }
}
