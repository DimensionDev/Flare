package dev.dimension.flare.data.platform

import dev.dimension.flare.data.model.tab.AllRssTimelineData
import dev.dimension.flare.data.model.tab.RssTimelineData
import dev.dimension.flare.data.model.tab.SubscriptionTimelineData
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.rss.AllRssTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.RssTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.SubscriptionTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data object RssTimelineSpecs {
    public val rss: TimelineSpec<RssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_FEED,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = RssTimelineData.serializer(),
            targetId = { it.feedUrl },
            presenterFactory = { RssTimelinePresenter(it.feedUrl) },
        )

    public val allRss: TimelineSpec<AllRssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_ALL,
            title = UiStrings.AllRssFeeds,
            icon = UiIcon.Rss.asType(),
            serializer = AllRssTimelineData.serializer(),
            targetId = { "all" },
            presenterFactory = { AllRssTimelinePresenter() },
        )

    public val subscription: TimelineSpec<SubscriptionTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_SUBSCRIPTION,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = SubscriptionTimelineData.serializer(),
            targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
            presenterFactory = {
                SubscriptionTimelinePresenter(
                    type = it.subscriptionType,
                    url = it.subscriptionUrl,
                )
            },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            rss,
            allRss,
            subscription,
        )
}
