package dev.dimension.flare.data.platform

import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.rss.AllRssTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.RssTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.SubscriptionTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
        TimelineSpec(
            id = "rss.feed",
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = RssData.serializer(),
            targetId = { it.feedUrl },
            presenterFactory = { RssTimelinePresenter(it.feedUrl) },
        )

    val allRss =
        TimelineSpec(
            id = "rss.all",
            title = UiStrings.AllRssFeeds,
            icon = UiIcon.Rss.asType(),
            serializer = AllRssData.serializer(),
            targetId = { "all" },
            presenterFactory = { AllRssTimelinePresenter() },
        )

    val subscription =
        TimelineSpec(
            id = "rss.subscription",
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = SubscriptionData.serializer(),
            targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
            presenterFactory = {
                SubscriptionTimelinePresenter(
                    type = it.subscriptionType,
                    url = it.subscriptionUrl,
                )
            },
        )

    val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            rss,
            allRss,
            subscription,
        )
}
