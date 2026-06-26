package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.subscription.KoinSubscriptionDataSource
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimelineV2

internal object RssDataSource {
    fun fetchLoader(url: String) = KoinSubscriptionDataSource.createTimelineLoader(SubscriptionType.RSS, url)

    fun fetchLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2> = KoinSubscriptionDataSource.createTimelineLoader(type, url)

    fun fetchLoader(subscription: UiRssSource): CacheableRemoteLoader<UiTimelineV2> =
        fetchLoader(
            type = subscription.type,
            url = subscription.url,
        )
}
