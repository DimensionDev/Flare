package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.ui.model.UiList
import kotlinx.collections.immutable.ImmutableList

internal interface ListLoader<T : UiList> {
    suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<T>

    suspend fun info(listId: String): T

    suspend fun create(metaData: ListMetaData): T

    suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): T

    suspend fun delete(listId: String)

    val supportedMetaData: ImmutableList<ListMetaDataType>
}
