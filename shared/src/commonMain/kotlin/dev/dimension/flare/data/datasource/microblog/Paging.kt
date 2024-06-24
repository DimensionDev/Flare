package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.RemoteMediator
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.cash.sqldelight.paging3.QueryPagingSource
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@OptIn(ExperimentalPagingApi::class)
internal fun timelinePager(
    pageSize: Int,
    pagingKey: String,
    accountKey: MicroBlogKey,
    database: CacheDatabase,
    scope: CoroutineScope,
    filterFlow: Flow<List<String>>,
    mediator: RemoteMediator<Int, DbPagingTimelineWithStatusView>,
): Flow<PagingData<UiStatus>> {
    val pagerFlow =
        Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = mediator,
            pagingSourceFactory = {
                QueryPagingSource(
                    countQuery =
                        database.dbPagingTimelineQueries.pageCount(
                            account_key = accountKey,
                            paging_key = pagingKey,
                        ),
                    transacter = database.dbPagingTimelineQueries,
                    context = Dispatchers.IO,
                    queryProvider = { limit, offset ->
                        database.dbPagingTimelineQueries.getPage(
                            account_key = accountKey,
                            paging_key = pagingKey,
                            offset = offset,
                            limit = limit,
                        )
                    },
                )
            },
        ).flow.cachedIn(scope)
    return combine(pagerFlow, filterFlow) { pagingData, filters ->
        pagingData
            .map {
                it.toUi()
            }.filter {
                !it.contains(filters)
            }
    }.cachedIn(scope)
}

private fun UiStatus.contains(keywords: List<String>): Boolean {
    val text = textToFilter
    return keywords.any { keyword ->
        text.any { it.contains(keyword, ignoreCase = true) }
    }
}
