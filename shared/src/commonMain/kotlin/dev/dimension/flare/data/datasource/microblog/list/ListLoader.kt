package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.ui.model.UiList
import kotlinx.collections.immutable.ImmutableList

internal interface ListLoader {
    suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList>

    suspend fun info(listId: String): UiList

    suspend fun create(metaData: ListMetaData): UiList

    suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList

    suspend fun delete(listId: String)

    val supportedMetaData: ImmutableList<ListMetaDataType>
}
