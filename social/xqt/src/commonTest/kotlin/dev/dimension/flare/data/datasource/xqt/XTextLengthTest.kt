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

    @Test
    fun `matches official normalization emoji and URL rules`() {
        assertEquals(1, "e\u0301".xWeightedLength())
        assertEquals(1, "©".xWeightedLength())
        assertEquals(280, ("a".repeat(279) + "©").xWeightedLength())
        assertEquals(4, "☀🏽".xWeightedLength())
        assertEquals(5, "☀\u200D☀".xWeightedLength())
        assertEquals(23, "https://example.com/a%20b".xWeightedLength())
        assertEquals(23, "https://example.com/hello_(world)".xWeightedLength())
    }
}
