package dev.dimension.flare.data.datasource.bluesky

import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertNotEquals

class FeedTimelineIdentityTest {
    @Test
    fun theSameFeedKeepsAccountSpecificRequestsAndHistorySeparate() {
        val first = FeedTimelineRemoteMediator({ error("No request expected") }, MicroBlogKey("first", "bsky.social"), "at://same/feed")
        val second = FeedTimelineRemoteMediator({ error("No request expected") }, MicroBlogKey("second", "bsky.social"), "at://same/feed")
        assertNotEquals(first.pagingKey, second.pagingKey)
    }
}
