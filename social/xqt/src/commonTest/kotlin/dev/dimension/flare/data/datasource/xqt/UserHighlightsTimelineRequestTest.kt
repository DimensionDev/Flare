package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals

class UserHighlightsTimelineRequestTest {
    @Test
    fun requestMatchesUserHighlightsEndpoint() {
        assertEquals("06hDMOiAMANzKwXHHUwD1w", USER_HIGHLIGHTS_QUERY_ID)
        assertEquals(
            """{"userId":"780401075354382336","count":20,"includePromotedContent":true,"withVoice":true}""",
            UserHighlightsTimelineRequest(
                userID = "780401075354382336",
                count = 20,
            ).encodeJson(),
        )
        assertEquals(
            """{"userId":"780401075354382336","count":20,"cursor":"next","includePromotedContent":true,"withVoice":true}""",
            UserHighlightsTimelineRequest(
                userID = "780401075354382336",
                count = 20,
                cursor = "next",
            ).encodeJson(),
        )
    }
}
