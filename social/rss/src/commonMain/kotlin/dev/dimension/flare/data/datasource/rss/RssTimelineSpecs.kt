package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.timeline.StandaloneTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

public data object RssTimelineSpecs {
    @Serializable
    public data class RssData(
        public val feedUrl: String,
    ) : TimelineSpec.Data

    @Serializable
    public data object AllRssData : TimelineSpec.Data

    @Serializable
    public data class SubscriptionData(
        public val subscriptionUrl: String,
        public val subscriptionType: SubscriptionType,
    ) : TimelineSpec.Data

    public val rss: StandaloneTimelineSpec<RssData> =
        StandaloneTimelineSpec(
            id = "rss.feed",
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = RssData.serializer(),
            stableKeyFactory = { it.feedUrl },
            loaderFactory = { _, data ->
                flowOf(RssDataSource.fetchLoader(data.feedUrl))
            },
        )

    public val allRss: StandaloneTimelineSpec<AllRssData> =
        StandaloneTimelineSpec(
            id = "rss.all",
            title = UiStrings.AllRssFeeds,
            icon = UiIcon.Rss.asType(),
            serializer = AllRssData.serializer(),
            stableKeyFactory = { "all" },
            loaderFactory = { context, _ ->
                context.appDatabase
                    .rssSourceDao()
                    .getAll()
                    .map { items ->
                        MixedRemoteMediator(
                            database = context.cacheDatabase,
                            mediators =
                                items.map {
                                    RssDataSource.fetchLoader(it)
                                },
                        )
                    }
            },
        )

    public val subscription: StandaloneTimelineSpec<SubscriptionData> =
        StandaloneTimelineSpec(
            id = "rss.subscription",
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = SubscriptionData.serializer(),
            stableKeyFactory = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
            loaderFactory = { _, data ->
                flowOf(RssDataSource.fetchLoader(data.toDbRssSource()))
            },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            rss,
            allRss,
            subscription,
        )
}

private fun RssTimelineSpecs.SubscriptionData.toDbRssSource(): DbRssSources =
    DbRssSources(
        url = subscriptionUrl,
        title = null,
        icon = null,
        lastUpdate = 0,
        type = subscriptionType,
    )
