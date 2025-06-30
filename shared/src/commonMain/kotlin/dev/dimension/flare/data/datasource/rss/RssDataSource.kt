package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object RssDataSource :
    KoinComponent {
    private val database: CacheDatabase by inject()

    @OptIn(ExperimentalPagingApi::class)
    fun fetch(
        url: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ) = Pager(
        config = PagingConfig(pageSize = pageSize),
        remoteMediator =
            RssTimelineRemoteMediator(
                url = url,
                cacheDatabase = database,
            ),
        pagingSourceFactory = {
            database.pagingTimelineDao().getPagingSource(
                accountType = AccountType.Guest,
                pagingKey = url,
            )
        },
    ).flow
        .map {
            it.map {
                it.render(null)
            }
        }.cachedIn(scope)
}
