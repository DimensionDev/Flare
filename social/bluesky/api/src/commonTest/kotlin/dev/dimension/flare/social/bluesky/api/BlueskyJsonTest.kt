package dev.dimension.flare.social.bluesky.api

import app.bsky.feed.PostEmbedUnion
import app.bsky.notification.ListNotificationsNotificationReason
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import sh.christian.ozone.BlueskyJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BlueskyJsonTest {
    @Test
    fun preservesUnknownEmbedContent() {
        val embed =
            BlueskyJson.decodeFromString<PostEmbedUnion>(
                """{"${'$'}type":"app.test.futureEmbed","text":"future content"}""",
            )

        val unknown = assertIs<PostEmbedUnion.Unknown>(embed)
        val content = unknown.value.decodeAs<JsonObject>()
        assertEquals("future content", content.getValue("text").jsonPrimitive.content)
        assertEquals("app.test.futureEmbed", content.getValue("${'$'}type").jsonPrimitive.content)
    }

    @Test
    fun preservesUnknownNotificationReasons() {
        val reason = BlueskyJson.decodeFromString<ListNotificationsNotificationReason>("\"future-reason\"")

        assertEquals(ListNotificationsNotificationReason.Unknown("future-reason"), reason)
        assertEquals("\"future-reason\"", BlueskyJson.encodeToString(ListNotificationsNotificationReason.serializer(), reason))
    }
}
