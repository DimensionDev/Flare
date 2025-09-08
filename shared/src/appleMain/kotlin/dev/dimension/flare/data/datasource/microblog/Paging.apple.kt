package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingConfig

internal actual val pagingConfig: PagingConfig =
    PagingConfig(
        pageSize = 20,
    )
