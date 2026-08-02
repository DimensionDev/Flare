package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationsTimelineRequestTest {
    @Test
    fun requestMatchesNotificationsTimelineEndpoint() {
        assertEquals(
            """{"timeline_type":"All","count":20}""",
            NotificationsTimelineRequest(
                timelineType = "All",
                count = 20,
            ).encodeJson(),
        )
        assertEquals(
            """{"timeline_type":"All","count":20,"cursor":"next"}""",
            NotificationsTimelineRequest(
                timelineType = "All",
                count = 20,
                cursor = "next",
            ).encodeJson(),
        )
    }
}
