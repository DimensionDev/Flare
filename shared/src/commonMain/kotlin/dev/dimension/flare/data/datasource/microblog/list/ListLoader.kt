package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.collections.immutable.ImmutableList

internal interface ListLoader {
    suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList>

    suspend fun info(listKey: MicroBlogKey): UiList

    suspend fun create(metaData: ListMetaData): UiList

    suspend fun update(
        listKey: MicroBlogKey,
        metaData: ListMetaData,
    ): UiList

    suspend fun delete(listKey: MicroBlogKey)

    val supportedMetaData: ImmutableList<ListMetaDataType>
}
