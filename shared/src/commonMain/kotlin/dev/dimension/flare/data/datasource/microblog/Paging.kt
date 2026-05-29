package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingConfig
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public val pagingConfig: PagingConfig =
    PagingConfig(
        pageSize = 20,
        prefetchDistance = 2,
        initialLoadSize = 20,
    )
