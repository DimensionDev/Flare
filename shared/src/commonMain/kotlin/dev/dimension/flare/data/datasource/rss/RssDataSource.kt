package dev.dimension.flare.data.datasource.rss

import androidx.paging.Pager
import androidx.paging.PagingConfig

internal object RssDataSource {
    fun fetch(url: String) =
        Pager(
            config =
                PagingConfig(
                    pageSize = 20,
                ),
            pagingSourceFactory = { RssTimelinePagingSource(url) },
        ).flow
}
