package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.SocialPlatformRegistry
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

            SubscriptionType.MASTODON_TRENDS -> {
                requireNotNull(
                    platformRegistry.createSubscriptionLoader(
                        type = PlatformType.Mastodon,
                        subscriptionType = "MASTODON_TRENDS",
                        url = subscription.url,
                        locale = Locale.language,
                    )
                )
            }

            SubscriptionType.MASTODON_PUBLIC -> {
                requireNotNull(
                    platformRegistry.createSubscriptionLoader(
                        type = PlatformType.Mastodon,
                        subscriptionType = "MASTODON_PUBLIC",
                        url = subscription.url,
                        locale = Locale.language,
                    )
                )
            }

            SubscriptionType.MASTODON_LOCAL -> {
                requireNotNull(
                    platformRegistry.createSubscriptionLoader(
                        type = PlatformType.Mastodon,
                        subscriptionType = "MASTODON_LOCAL",
                        url = subscription.url,
                        locale = Locale.language,
                    )
                )
            }
        }
}
