package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.data.network.pixiv.PixivRankingMode
import dev.dimension.flare.data.network.pixiv.PixivRestrict
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixivTimelineConfigurationTest {
    @Test
    fun followingFeedsCanJoinButRecommendationsCannot() {
        val service = PixivService()
        val account = MicroBlogKey("test", "pixiv.net")
        assertTrue(PixivHomeTimelineLoader(service, account).canJoinTimeline)
        for (restrict in PixivRestrict.entries) {
            assertTrue(PixivFollowingTimelineLoader(service, account, restrict).canJoinTimeline)
        }
        assertFalse(PixivDiscoverTimelineLoader(service, account).canJoinTimeline)
        for (mode in PixivRankingMode.entries) {
            assertFalse(PixivRankingTimelineLoader(service, account, mode).canJoinTimeline)
        }
    }
}
