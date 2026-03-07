package dev.dimension.flare.data.datasource.rss

import dev.dimension.flare.data.database.app.AppDatabase
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
}
