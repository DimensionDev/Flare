package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.rss.RssDataSource
import dev.dimension.flare.data.model.tab.AllRssTimelineData
import dev.dimension.flare.data.model.tab.RssTimelineData
import dev.dimension.flare.data.model.tab.SubscriptionTimelineData
import dev.dimension.flare.data.model.tab.TimelineLoaderFactory
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.remoteLoaderFactory
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.data.subscription.SubscriptionTimelineLoaderFactory
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map

public data object RssTimelineSpecs {
    public val rss: TimelineSpec<RssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_FEED,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = RssTimelineData.serializer(),
            targetId = { it.feedUrl },
            loaderFactory = remoteLoaderFactory { RssDataSource.fetchLoader(it.feedUrl) },
        )

    public val allRss: TimelineSpec<AllRssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_ALL,
            title = UiStrings.AllRssFeeds,
            icon = UiIcon.Rss.asType(),
            serializer = AllRssTimelineData.serializer(),
            targetId = { "all" },
            loaderFactory =
                TimelineLoaderFactory { _, context ->
                    val subscriptionRepository = context.get<SubscriptionRepository>()
                    val subscriptionTimelineLoaderFactory = context.get<SubscriptionTimelineLoaderFactory>()
                    subscriptionRepository
                        .observeAll()
                        .map { items ->
                            subscriptionTimelineLoaderFactory.mixedTimeline(
                                loaders =
                                    items.map {
                                        RssDataSource.fetchLoader(it)
                                    },
                            )
                        }
                },
        )

    public val subscription: TimelineSpec<SubscriptionTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_SUBSCRIPTION,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = SubscriptionTimelineData.serializer(),
            targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
            loaderFactory =
                remoteLoaderFactory {
                    RssDataSource.fetchLoader(it.subscriptionType, it.subscriptionUrl)
                },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            rss,
            allRss,
            subscription,
        )
}
