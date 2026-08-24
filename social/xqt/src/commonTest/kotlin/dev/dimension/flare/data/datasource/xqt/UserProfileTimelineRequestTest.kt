package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals

class UserProfileTimelineRequestTest {
    @Test
    fun requestMatchesProfileTimelineEndpoints() {
        assertEquals("ryXhzHPlD6YJE137gSf7mQ", UserProfileTimelineType.Highlights.queryId)
        assertEquals("UserHighlightsTweets", UserProfileTimelineType.Highlights.operationName)
        assertEquals("ZmMjUyrTpwYfTGAdylEyMw", UserProfileTimelineType.Articles.queryId)
        assertEquals("UserArticlesTweets", UserProfileTimelineType.Articles.operationName)
        assertEquals("bV_DHAIvQ945LAA1-eIIow", UserProfileTimelineType.Reposts.queryId)
        assertEquals("UserRepostsTimeline", UserProfileTimelineType.Reposts.operationName)
        assertEquals(
            """{"userId":"780401075354382336","count":20,"includePromotedContent":true,"withVoice":true}""",
            UserProfileTimelineRequest(
                userID = "780401075354382336",
                count = 20,
            ).encodeJson(),
        )
        assertEquals(
            """{"userId":"780401075354382336","count":20,"cursor":"next","includePromotedContent":true,"withVoice":true}""",
            UserProfileTimelineRequest(
                userID = "780401075354382336",
                count = 20,
                cursor = "next",
            ).encodeJson(),
        )
    }
}
