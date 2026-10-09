package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MisskeyComposeTextTest {
    @Test
    fun countsCodePointsAndChecksCwIndependently() {
        val source =
            MisskeyDataSource(
                accountKey = MicroBlogKey("alice", "example.com"),
                host = "example.com",
                credentialFlow = emptyFlow(),
            )
        assertTrue(source.checkText("a".repeat(2998) + "👍🏽", null, 3000).isValid)
        assertFalse(source.checkText("a".repeat(2999) + "👍🏽", null, 3000).isValid)
        assertTrue(source.checkText("a".repeat(3000), "a".repeat(100), 3000).isValid)
        assertFalse(source.checkText("x", "a".repeat(101), 3000).isValid)
        assertEquals(2993, source.checkText("👨‍👩‍👧‍👦", null, 3000).remainingLength)
        assertEquals(999, source.checkText("x", null, 1000).remainingLength)
    }
}
