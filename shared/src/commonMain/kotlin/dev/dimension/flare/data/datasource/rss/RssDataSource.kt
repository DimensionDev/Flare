package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.data.database.cache.CacheDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object RssDataSource :
    KoinComponent {
    private val database: CacheDatabase by inject()

    fun fetchLoader(url: String) =
        RssTimelineRemoteMediator(
            url = url,
            cacheDatabase = database,
        )
}
