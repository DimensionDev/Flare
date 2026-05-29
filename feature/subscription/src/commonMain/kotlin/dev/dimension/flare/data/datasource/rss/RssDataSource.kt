package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimelineV2
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object RssDataSource :
    KoinComponent {
    private val subscriptionRepository: SubscriptionRepository by inject()
    private val platformRegistry: PlatformRegistry by inject()

    fun fetchLoader(url: String) =
        RssTimelineRemoteMediator(
            url = url,
            fetchSource = {
                subscriptionRepository.findByUrl(it).firstOrNull()
            },
        )

    fun fetchLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2> =
        when (type) {
            SubscriptionType.RSS -> {
                RssTimelineRemoteMediator(
                    url = url,
                    fetchSource = {
                        subscriptionRepository.findByUrl(it).firstOrNull()
                    },
                )
            }

            else -> {
                platformRegistry
                    .requireSubscriptionTimelineSpec(type)
                    .createLoader(
                        host = url,
                        locale = Locale.language,
                    )
            }
        }

    fun fetchLoader(subscription: UiRssSource): CacheableRemoteLoader<UiTimelineV2> =
        fetchLoader(
            type = subscription.type,
            url = subscription.url,
        )
}
