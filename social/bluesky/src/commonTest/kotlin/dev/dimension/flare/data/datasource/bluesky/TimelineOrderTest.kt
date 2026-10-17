package dev.dimension.flare.data.datasource.bluesky

import dev.dimension.flare.data.datasource.microblog.paging.TimelineOrder
import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineOrderTest {
    @Test
    fun generatedFeedsDoNotInheritFollowingTimelineContinuity() {
        val account = MicroBlogKey("did:plc:reader", "bsky.social")
        val following = HomeTimelineRemoteMediator({ error("No network expected") }, account)
        val generated = FeedTimelineRemoteMediator({ error("No network expected") }, account, "at://feed")
        assertEquals(TimelineOrder.Continuous, following.timelineOrder)
        assertEquals(TimelineOrder.Ranked, generated.timelineOrder)
    }
}
