package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingConfig
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public val pagingConfig: PagingConfig =
    PagingConfig(
        pageSize = 20,
        prefetchDistance = 1,
        initialLoadSize = 20,
    )

@HiddenFromObjC
public val offsetPagingConfig: PagingConfig
    get() =
        PagingConfig(
            pageSize = pagingConfig.pageSize,
            prefetchDistance = pagingConfig.prefetchDistance,
            enablePlaceholders = false,
            initialLoadSize = pagingConfig.initialLoadSize,
            maxSize = pagingConfig.maxSize,
            jumpThreshold = pagingConfig.jumpThreshold,
        )
