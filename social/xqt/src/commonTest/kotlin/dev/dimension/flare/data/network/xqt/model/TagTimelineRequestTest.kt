package dev.dimension.flare.data.network.xqt.model

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.xqt.XQTTimelineQueryIds
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagTimelineRequestTest {
    @Test
    fun requestMatchesCapturedHomeTimelineShape() {
        assertEquals(
            "9GXlcvTyuEyONW2ua63Zfw",
            XQTTimelineQueryIds.PINNED_TIMELINES_MANAGEMENT_SHEET,
        )
        assertEquals("wp06oo3fRGU4P1sK8rECqQ", XQTTimelineQueryIds.TAG_TIMELINE)

        val request =
            TagTimelineRequest(
                variables =
                    TagTimelineVariables(
                        count = 20,
                        tag = "1925949503837798401",
                    ),
            )
        val root = JSON.parseToJsonElement(request.encodeJson()).jsonObject
        val variables = root.getValue("variables").jsonObject
        val features = root.getValue("features").jsonObject

        assertEquals(XQTTimelineQueryIds.TAG_TIMELINE, root.getValue("queryId").jsonPrimitive.content)
        assertEquals(
            setOf(
                "count",
                "includePromotedContent",
                "requestContext",
                "tag",
                "withCommunity",
                "seenTweetIds",
            ),
            variables.keys,
        )
        assertEquals("1925949503837798401", variables.getValue("tag").jsonPrimitive.content)
        assertEquals(38, features.size)
        assertTrue(features.getValue("responsive_web_profile_redirect_enabled").jsonPrimitive.boolean)
        assertFalse(features.getValue("post_ctas_fetch_enabled").jsonPrimitive.boolean)
    }

    @Test
    fun cursorIsOnlyAddedForPagination() {
        val variables =
            TagTimelineVariables(
                count = 20,
                cursor = "next-page",
                tag = "tag-id",
            )

        assertEquals(
            """{"count":20,"cursor":"next-page","includePromotedContent":true,"requestContext":"launch","tag":"tag-id","withCommunity":true,"seenTweetIds":[]}""",
            variables.encodeJson(),
        )
    }
}
