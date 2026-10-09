package dev.dimension.flare.data.datasource.mastodon

import kotlin.test.Test
import kotlin.test.assertEquals

class MastodonTextLengthTest {
    @Test
    fun stripsBodyAndCWBeforeCounting() {
        assertEquals(500L, mastodonTextLength("a".repeat(499), "x ", 23))
        assertEquals(500L, mastodonTextLength(" \t" + "a".repeat(499) + "\r\n", "\n x \t", 23))
        assertEquals(501L, mastodonTextLength("a".repeat(500), "x ", 23))
        assertEquals(24L, mastodonTextLength("\t https://example.com/ \n", " x \r\n", 23))
    }

    @Test
    fun stripsOnlyMastodonBoundaryWhitespace() {
        for (character in listOf('\u0000', ' ', '\t', '\n', '\u000B', '\u000C', '\r')) {
            assertEquals(2L, mastodonTextLength("${character}a$character", "${character}x$character", 23))
            assertEquals(0L, mastodonTextLength("$character", "$character", 23))
        }
        assertEquals(6L, mastodonTextLength("\u00a0a\u2003", "\u2003x\u00a0", 23))
        assertEquals(6L, mastodonTextLength(" a b ", " x y ", 23))
    }

    @Test
    fun countsGraphemesEntitiesAndCW() {
        assertEquals(1L, mastodonTextLength("👍🏽", null, 23))
        assertEquals(500L, mastodonTextLength("a".repeat(499) + "👍🏽", null, 23))
        assertEquals(500L, mastodonTextLength("a".repeat(476) + " https://example.com/" + "a".repeat(50), null, 23))
        assertEquals(503L, mastodonTextLength("a".repeat(479) + " https://x.com", null, 23))
        assertEquals(497L, mastodonTextLength("a".repeat(494) + " @a@remote.example", null, 23))
        assertEquals(501L, mastodonTextLength("a".repeat(500), "x", 23))
        assertEquals(42L, mastodonTextLength("https://example.com/path/@a@b.com", null, 42))
        assertEquals(11L, mastodonTextLength("example.com", null, 23))
        assertEquals(0L, mastodonTextLength("https://example.com", null, 0))
        assertEquals(Int.MAX_VALUE.toLong() * 2 + 1, mastodonTextLength("https://x.com https://x.com", null, Int.MAX_VALUE))
    }

    @Test
    fun countsOnlyRemoteMentionEntities() {
        assertEquals(11L, mastodonTextLength("@first.last@remote.example", null, 23))
        assertEquals(11L, mastodonTextLength("@first-last@remote.example", null, 23))
        assertEquals(5L, mastodonTextLength("@a@remote.example...", null, 23))
        assertEquals(6L, mastodonTextLength("@a@remote.example:123", null, 23))
        assertEquals(2L, mastodonTextLength("@a@例子.测试", null, 23))
        assertEquals(2L, mastodonTextLength("@a@Ⅰ", null, 23))
        assertEquals(4L, mastodonTextLength("@a@½", null, 23))
        assertEquals(3L, mastodonTextLength("½@a@remote.example", null, 23))
        for (text in listOf(
            "/@a@remote.example",
            "=@a@remote.example",
            "中@a@remote.example",
            "@a@remote.example@other.example",
            "@a@" + "x".repeat(254),
        )) {
            assertEquals(text.length.toLong(), mastodonTextLength(text, null, 23))
        }
    }
}
