package dev.dimension.flare.data.network.xqt.model

import dev.dimension.flare.common.decodeJson
import kotlin.test.Test
import kotlin.test.assertEquals

class PinnedTimelinesManagementSheetResponseTest {
    @Test
    fun extractsOnlyValidTagPinnedTimelines() {
        val response =
            """
            {
              "data": {
                "pinnable_timelines": {
                  "pinnable_timelines": [
                    {
                      "__typename": "ListPinnedTimeline",
                      "list": {"rest_id": "list-id", "name": "A list"}
                    },
                    {
                      "__typename": "TagPinnedTimeline",
                      "icon_name": "topic_technology",
                      "name": "Technology",
                      "scribe": "scribe-id",
                      "tab_label": "Tech",
                      "tag": "tag-id"
                    },
                    {
                      "__typename": "CommunityPinnedTimeline",
                      "community_results": {"id": "community-id"}
                    },
                    {
                      "__typename": "TagPinnedTimeline",
                      "name": "Missing tag"
                    }
                  ]
                }
              }
            }
            """.trimIndent().decodeJson<PinnedTimelinesManagementSheetResponse>()

        assertEquals(
            listOf(
                TagPinnedTimeline(
                    iconName = "topic_technology",
                    name = "Technology",
                    scribe = "scribe-id",
                    tabLabel = "Tech",
                    tag = "tag-id",
                ),
            ),
            response.tagPinnedTimelines,
        )
        assertEquals("Tech", response.tagPinnedTimelines.single().title)
    }
}
