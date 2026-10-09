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

    @Test
    fun `matches official emoji variants and sequence boundaries`() {
        assertEquals(2, "©\uFE0F".xWeightedLength())
        assertEquals(3, "©\uFE0E".xWeightedLength())
        assertEquals(4, "☀\uFE0E".xWeightedLength())
        assertEquals(2, "1\uFE0F\u20E3".xWeightedLength())
        assertEquals(2, "1\u20E3".xWeightedLength())
        assertEquals(2, "👩🏽\u200D⚕\uFE0F".xWeightedLength())
        assertEquals(2, "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F".xWeightedLength())
        assertEquals(4, "🇦🇦".xWeightedLength())
        assertEquals(4, "🇯🇵🇺🇸".xWeightedLength())
        assertEquals(10, "a👨‍👩‍👧‍👦b👍🏽c🇯🇵d".xWeightedLength())
    }

    @Test
    fun `matches supplementary emoji at UTF-16 offsets`() {
        assertEquals(0, xEmojiLengthAt("a👍🏽b", 0))
        assertEquals(4, xEmojiLengthAt("a👍🏽b", 1))
        assertEquals(0, xEmojiLengthAt("a👍🏽b", 5))
        assertEquals(11, xEmojiLengthAt("a👨‍👩‍👧‍👦b", 1))
        assertEquals(2, xEmojiLengthAt("☀🏽", 1))
    }
}
