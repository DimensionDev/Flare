package dev.dimension.flare.data.datasource.bluesky

import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlueskyComposeTextTest {
    @Test
    fun checksGraphemeAndUtf8Limits() =
        runTest {
            val source =
                BlueskyDataSource(
                    accountKey = MicroBlogKey("did:plc:alice", "bsky.social"),
                    credentialFlow = emptyFlow(),
                    updateCredential = {},
                )
            val rule = assertNotNull(source.composeConfig(ComposeType.New).text)
            for (text in listOf("😀", "👍🏽", "🇨🇳", "👨‍👩‍👧‍👦", "e\u0301")) {
                assertEquals(299, rule.remainingLength(text).first().remainingLength, text)
            }
            assertTrue(rule.remainingLength("a".repeat(299) + "👍🏽").first().isValid)
            assertFalse(rule.remainingLength("a".repeat(300) + "👍🏽").first().isValid)
            assertTrue(rule.remainingLength("a" + "\u0301".repeat(1499)).first().isValid)
            val byteOverflow = "a" + "\u0301".repeat(1500)
            val remaining = rule.remainingLength(byteOverflow).first()
            assertEquals(299, remaining.remainingLength)
            assertFalse(remaining.isValid)
        }
}
