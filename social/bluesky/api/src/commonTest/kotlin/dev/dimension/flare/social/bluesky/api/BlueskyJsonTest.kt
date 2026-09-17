package dev.dimension.flare.social.bluesky.api

import app.bsky.feed.PostEmbedUnion
import app.bsky.notification.ListNotificationsNotificationReason
import app.bsky.notification.ListNotificationsResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
        // Native cannot discover this generated open enum's serializer through a reified lookup.
        val serializer = ListNotificationsNotificationReason.serializer()
        val reason = BlueskyJson.decodeFromString(serializer, "\"future-reason\"")

        assertEquals(ListNotificationsNotificationReason.Unknown("future-reason"), reason)
        assertEquals("\"future-reason\"", BlueskyJson.encodeToString(serializer, reason))
    }

    @Test
    fun preservesUnknownReasonsInNotificationResponses() {
        val response =
            BlueskyJson.decodeFromString<ListNotificationsResponse>(
                """
                {
                  "notifications": [{
                    "uri": "at://did:plc:test/app.bsky.feed.like/test",
                    "cid": "bafyreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku",
                    "author": {"did": "did:plc:test", "handle": "test.example.com"},
                    "reason": "future-reason",
                    "record": {},
                    "isRead": false,
                    "indexedAt": "2026-09-17T00:00:00Z"
                  }]
                }
                """.trimIndent(),
            )

        assertEquals(ListNotificationsNotificationReason.Unknown("future-reason"), response.notifications.single().reason)
        val encoded = BlueskyJson.encodeToJsonElement(ListNotificationsResponse.serializer(), response).jsonObject
        val notification =
            encoded
                .getValue("notifications")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("future-reason", notification.getValue("reason").jsonPrimitive.content)
    }
}
