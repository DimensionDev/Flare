package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.ui.model.UiList
import kotlinx.collections.immutable.ImmutableList

public interface ListLoader {
    public suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList>

    public suspend fun info(listId: String): UiList

    public suspend fun create(metaData: ListMetaData): UiList

    public suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList

    public suspend fun delete(listId: String)

    public val supportedMetaData: ImmutableList<ListMetaDataType>
}
