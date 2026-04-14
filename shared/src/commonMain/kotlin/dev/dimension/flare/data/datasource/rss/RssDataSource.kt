package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.guest.mastodon.GuestPublicTimelineRemoteMediator
import dev.dimension.flare.data.datasource.guest.mastodon.GuestTrendsRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object RssDataSource :
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    fun fetchLoader(url: String) =
        RssTimelineRemoteMediator(
            url = url,
            fetchSource = {
                appDatabase
                    .rssSourceDao()
                    .getByUrl(it)
                    .firstOrNull()
            },
        )

    fun fetchLoader(subscription: DbRssSources): CacheableRemoteLoader<UiTimelineV2> =
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
                GuestTrendsRemoteMediator(
                    host = subscription.url,
                    locale = Locale.language,
                )
            }

            SubscriptionType.MASTODON_PUBLIC -> {
                GuestPublicTimelineRemoteMediator(
                    host = subscription.url,
                    locale = Locale.language,
                    local = false,
                )
            }

            SubscriptionType.MASTODON_LOCAL -> {
                GuestPublicTimelineRemoteMediator(
                    host = subscription.url,
                    locale = Locale.language,
                    local = true,
                )
            }
        }
}
