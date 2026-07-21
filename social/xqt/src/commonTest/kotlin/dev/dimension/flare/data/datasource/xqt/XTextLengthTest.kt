package dev.dimension.flare.data.datasource.xqt

import kotlin.test.Test
import kotlin.test.assertEquals

class XTextLengthTest {
    @Test
    fun `counts X weighted text`() {
        assertEquals(280, "a".repeat(280).xWeightedLength())
        assertEquals(280, "あ".repeat(140).xWeightedLength())
        assertEquals(282, "あ".repeat(141).xWeightedLength())
        assertEquals(2, "👨‍👩‍👧‍👦".xWeightedLength())
        assertEquals(2, "👍🏽".xWeightedLength())
        assertEquals(2, "🇯🇵".xWeightedLength())
        assertEquals(23, "https://example.com/a/very/long/path".xWeightedLength())
    }
}
