package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.model.SubscriptionTimelineTypeKey
import dev.dimension.flare.model.SubscriptionTimelineTypes
import dev.dimension.flare.ui.model.UiTimelineV2
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public object RssDataSource :
    KoinComponent {
    private val appDatabase: AppDatabase by inject()
    private val platformRegistry: SocialPlatformRegistry by inject()

    public fun fetchLoader(url: String): RssTimelineRemoteMediator =
        RssTimelineRemoteMediator(
            url = url,
            fetchSource = {
                appDatabase
                    .rssSourceDao()
                    .getByUrl(it)
                    .firstOrNull()
            },
        )

    public fun fetchLoader(subscription: DbRssSources): CacheableRemoteLoader<UiTimelineV2> =
        when (subscription.type) {
            SubscriptionType.RSS -> {
                RssTimelineRemoteMediator(
                    url = subscription.url,
                    fetchSource = {
                        appDatabase
                            .rssSourceDao()
                            .getByUrl(it)
                            .firstOrNull()
                    },
                )
            }

            SubscriptionType.MASTODON_TRENDS ->
                fetchPlatformSubscription(
                    subscriptionType = SubscriptionTimelineTypes.MastodonTrends,
                    url = subscription.url,
                )

            SubscriptionType.MASTODON_PUBLIC ->
                fetchPlatformSubscription(
                    subscriptionType = SubscriptionTimelineTypes.MastodonPublic,
                    url = subscription.url,
                )

            SubscriptionType.MASTODON_LOCAL ->
                fetchPlatformSubscription(
                    subscriptionType = SubscriptionTimelineTypes.MastodonLocal,
                    url = subscription.url,
                )
        }

    private fun fetchPlatformSubscription(
        subscriptionType: SubscriptionTimelineTypeKey,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2> =
        platformRegistry.createSubscriptionLoader(
            type = PlatformType.Mastodon,
            subscriptionType = subscriptionType,
            url = url,
            locale = Locale.language,
        ) ?: UnsupportedSubscriptionRemoteLoader(subscriptionType, url)
}

private class UnsupportedSubscriptionRemoteLoader(
    subscriptionType: SubscriptionTimelineTypeKey,
    url: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "unsupported_subscription:${subscriptionType.value}:$url"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> = PagingResult(endOfPaginationReached = true)
}
