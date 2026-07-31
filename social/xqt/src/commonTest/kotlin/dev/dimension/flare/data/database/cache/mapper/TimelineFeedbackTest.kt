package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.xqt.model.FeedbackInfo
import dev.dimension.flare.data.network.xqt.model.ItemResult
import dev.dimension.flare.data.network.xqt.model.Timeline
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntry
import dev.dimension.flare.data.network.xqt.model.TimelineResponseObjects
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineItem
import dev.dimension.flare.data.network.xqt.model.TimelineTweet
import dev.dimension.flare.data.network.xqt.model.Tweet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TimelineFeedbackTest {
    @Test
    fun timelineResolvesTweetFeedbackKeyToDontLikeAction() {
        val responseObjects =
            """
            {
              "feedbackActions": [
                {
                  "key": "root-feedback",
                  "value": {
                    "childKeys": ["child-feedback"],
                    "feedbackType": "DontLike",
                    "feedbackUrl": "/2/timeline/feedback.json?feedback_type=DontLike&action_metadata=abc%2Bdef%3D%3D",
                    "prompt": "Not interested in this post"
                  }
                }
              ]
            }
            """.trimIndent()
                .decodeJson<TimelineResponseObjects>()
        val feedbackInfo =
            """
            {"feedbackKeys":["root-feedback"]}
            """.decodeJson<FeedbackInfo>()
        val item =
            TimelineTimelineItem(
                itemContent =
                    TimelineTweet(
                        tweetResults = ItemResult(result = Tweet(restId = "tweet-id")),
                    ),
                feedbackInfo = feedbackInfo,
            )
        val timeline =
            Timeline(
                instructions =
                    listOf(
                        TimelineAddEntries(
                            propertyEntries =
                                listOf(
                                    TimelineAddEntry(
                                        content = item,
                                        entryId = "tweet-tweet-id",
                                        sortIndex = "1",
                                    ),
                                ),
                        ),
                    ),
                responseObjects = responseObjects,
            )

        val action = timeline.tweets().single().notInterestedAction

        assertNotNull(action)
        assertEquals("DontLike", action.feedbackType)
        assertEquals("Not interested in this post", action.prompt)
    }
}
