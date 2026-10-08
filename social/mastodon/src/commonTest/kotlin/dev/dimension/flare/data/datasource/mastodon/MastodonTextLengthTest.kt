package dev.dimension.flare.data.datasource.mastodon

import kotlin.test.Test
import kotlin.test.assertEquals

class MastodonTextLengthTest {
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
}
