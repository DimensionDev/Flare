package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.network.fanbox.FanboxService
import dev.dimension.flare.data.platform.FanboxCredential
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FanboxTimelineConfigurationTest {
    @Test
    fun homeAndSupportingFeedsCanJoinButRecommendationsCannot() {
        val service = FanboxService(flowOf(FanboxCredential(sessionId = "test", userId = "test")))
        val account = MicroBlogKey("test", "fanbox.cc")
        assertTrue(FanboxHomeTimelineLoader(service, account).canJoinTimeline)
        assertTrue(FanboxSupportedTimelineLoader(service, account).canJoinTimeline)
        assertFalse(FanboxRecommendedCreatorsTimelineLoader(service, account).canJoinTimeline)
    }
}
