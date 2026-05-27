package dev.dimension.flare.data.subscription

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2
import org.koin.core.annotation.Single

@Single
public class SubscriptionTimelineLoaderFactory internal constructor(
    private val database: CacheDatabase,
) {
    public fun mixedTimeline(loaders: List<CacheableRemoteLoader<UiTimelineV2>>): CacheableRemoteLoader<UiTimelineV2> =
        MixedRemoteMediator(
            database = database,
            mediators = loaders,
        )
}
