package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTimelineRequestTest {
    @Test
    fun rankingIsTheOnlyAdditionalVariable() {
        val following = HomeTimelineRequest(count = 20)

        assertEquals(
            """{"count":20,"includePromotedContent":true,"latestControlAvailable":true,"requestContext":"launch","withCommunity":true,"seenTweetIds":[]}""",
            following.encodeJson(),
        )
        assertEquals(
            """{"count":20,"enableRanking":true,"includePromotedContent":true,"latestControlAvailable":true,"requestContext":"launch","withCommunity":true,"seenTweetIds":[]}""",
            following.copy(enableRanking = true).encodeJson(),
        )
    }
}
