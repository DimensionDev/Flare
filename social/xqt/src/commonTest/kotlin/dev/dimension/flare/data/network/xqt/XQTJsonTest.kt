package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.NotificationsTimelineResponse
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineNotification
import dev.dimension.flare.data.network.xqt.model.TimelineTerminateTimeline
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineCursor
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineItem
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class XQTJsonTest {
    @Test
    fun unknownInstructionsPreserveNotificationsAndCursors() {
        val response =
            decodeNotifications(
                """{"type":"TimelineFutureInstruction","payload":{"items":[1,{"nested":true}]}}""",
                ADDED_NOTIFICATION_AND_CURSOR,
                """{"entry_ids":["notification-removed"],"type":"TimelineRemoveEntries"}""",
                """{"type":"TimelineTerminateTimeline","direction":"Bottom"}""",
                """{"type":"AnotherFutureInstruction","payload":null}""",
            )
        val instructions = assertNotNull(response.data.viewerV2.userResults.result.notificationTimeline.timeline).instructions
        val entries = instructions.filterIsInstance<TimelineAddEntries>().single().propertyEntries

        assertEquals(listOf("notification-kept", "cursor-bottom"), entries.map { it.entryId })
        val item = assertIs<TimelineTimelineItem>(entries[0].content)
        val notification = assertIs<TimelineNotification>(item.itemContent)
        assertEquals("notification-kept", notification.id)
        assertEquals("Someone liked your post", notification.richMessage.text)
        val cursor = assertIs<TimelineTimelineCursor>(entries[1].content)
        assertEquals(CursorType.bottom, cursor.cursorType)
        assertEquals("next-page", cursor.value)
        assertEquals(
            TimelineTerminateTimeline.Direction.bottom,
            instructions.filterIsInstance<TimelineTerminateTimeline>().single().direction,
        )
    }

    @Test
    fun responseContainingOnlyUnknownInstructionsStillDecodes() {
        val response =
            decodeNotifications(
                """{"type":"TimelineRemoveEntries","entry_ids":["notification-removed"]}""",
                """{"type":"TimelineFutureInstruction","unrecognized_field":{}}""",
            )
        val instructions = assertNotNull(response.data.viewerV2.userResults.result.notificationTimeline.timeline).instructions

        assertEquals(emptyList(), instructions.filterIsInstance<TimelineAddEntries>())
    }

    @Test
    fun malformedKnownInstructionsStillFail() {
        assertFailsWith<SerializationException> {
            decodeNotifications("""{"type":"TimelineAddEntries"}""")
        }
    }

    @Test
    fun instructionsWithoutATypeStillFail() {
        assertFailsWith<SerializationException> {
            decodeNotifications("""{"entries":[]}""")
        }
    }

    private fun decodeNotifications(vararg instructions: String): NotificationsTimelineResponse =
        XQT_JSON.decodeFromString(
            """
            {
              "data": {
                "viewer_v2": {
                  "user_results": {
                    "result": {
                      "__typename": "User",
                      "rest_id": "me",
                      "notification_timeline": {
                        "timeline": {"instructions": [${instructions.joinToString()}]}
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
}

private val ADDED_NOTIFICATION_AND_CURSOR =
    """
    {
      "type": "TimelineAddEntries",
      "entries": [
        {
          "entryId": "notification-kept",
          "sortIndex": "2",
          "content": {
            "entryType": "TimelineTimelineItem",
            "itemContent": {
              "__typename": "TimelineNotification",
              "id": "notification-kept",
              "itemType": "TimelineNotification",
              "notification_icon": "heart_icon",
              "notification_url": {"url": "/notifications"},
              "rich_message": {"text": "Someone liked your post"},
              "template": {},
              "timestamp_ms": "1788156000000"
            }
          }
        },
        {
          "entryId": "cursor-bottom",
          "sortIndex": "1",
          "content": {
            "entryType": "TimelineTimelineCursor",
            "cursorType": "Bottom",
            "value": "next-page"
          }
        }
      ]
    }
    """.trimIndent()
