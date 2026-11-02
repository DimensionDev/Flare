package dev.dimension.flare.data.network.nostr

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NostrTextLimitsTest {
    @Test
    fun checksFinalContentAndEntireUtf8Message() {
        assertNull(NostrTextLimits().error("中".repeat(70000), "{}"))
        assertNull(NostrTextLimits(maxContentLength = 2).error("👍🏽", "{}"))
        assertNotNull(NostrTextLimits(maxContentLength = 1).error("👍🏽", "{}"))
        assertNotNull(NostrTextLimits(maxContentLength = 5).error("hi\nhttps://media.example/image", "{}"))
        assertNull(NostrTextLimits(maxMessageLength = 12).error("", "{}"))
        assertNotNull(NostrTextLimits(maxMessageLength = 11).error("", "{}"))
        val json = "{\"content\":\"" + "中".repeat(21846) + "\",\"tags\":[[\"content-warning\",\"x\"]]}"
        assertNotNull(NostrTextLimits(maxMessageLength = 65535).error("中".repeat(21846), json))
    }
}
