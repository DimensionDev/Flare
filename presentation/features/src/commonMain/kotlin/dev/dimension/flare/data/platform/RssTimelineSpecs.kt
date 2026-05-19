package dev.dimension.flare.data.platform

import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.rss.RssDataSource
import dev.dimension.flare.data.model.tab.StandaloneTimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

internal data object RssTimelineSpecs {
    @Serializable
    data class RssData(
        val feedUrl: String,
    ) : TimelineSpec.Data

    @Serializable
    data object AllRssData : TimelineSpec.Data

    @Serializable
    data class SubscriptionData(
        val subscriptionUrl: String,
        val subscriptionType: SubscriptionType,
    ) : TimelineSpec.Data

    val rss =
        StandaloneTimelineSpec(
            id = "rss.feed",
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = RssData.serializer(),
            targetId = { it.feedUrl },
            loaderFactory = { _, data ->
                flowOf(RssDataSource.fetchLoader(data.feedUrl))
            },
        )

    val allRss =
        StandaloneTimelineSpec(
            id = "rss.all",
            title = UiStrings.AllRssFeeds,
            icon = UiIcon.Rss.asType(),
            serializer = AllRssData.serializer(),
            targetId = { "all" },
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

    val subscription =
        StandaloneTimelineSpec(
            id = "rss.subscription",
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = SubscriptionData.serializer(),
            targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
            loaderFactory = { _, data ->
                flowOf(RssDataSource.fetchLoader(data.toDbRssSource()))
            },
        )

    val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
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
