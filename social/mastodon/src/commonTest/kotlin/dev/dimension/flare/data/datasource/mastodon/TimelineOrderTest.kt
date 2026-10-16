package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.guest.mastodon.GuestPublicTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.TimelineOrder
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineOrderTest {
    private val account = MicroBlogKey("reader", "mastodon.social")
    private val service = MastodonService("https://mastodon.social/", flowOf(""))

    @Test
    fun publicAndLocalTimelinesHaveTheSameContinuityWithAndWithoutAuthentication() {
        for (local in listOf(false, true)) {
            assertEquals(TimelineOrder.Continuous, PublicTimelineRemoteMediator(service, account, local).timelineOrder)
            assertEquals(TimelineOrder.Continuous, GuestPublicTimelineRemoteMediator(account.host, "en", local).timelineOrder)
        }
        assertEquals(TimelineOrder.Continuous, HomeTimelineRemoteMediator(service, account).timelineOrder)
        assertEquals(TimelineOrder.Continuous, ListTimelineRemoteMediator("list", service, account).timelineOrder)
    }

    @Test
    fun collectionCursorsDoNotProveContinuityOfStatusIdentities() {
        assertEquals(TimelineOrder.Ranked, BookmarkTimelineRemoteMediator(service, account).timelineOrder)
        assertEquals(TimelineOrder.Ranked, FavouriteTimelineRemoteMediator(service, account).timelineOrder)
        assertEquals(TimelineOrder.Ranked, DiscoverStatusRemoteMediator(service, account).timelineOrder)
    }
}
