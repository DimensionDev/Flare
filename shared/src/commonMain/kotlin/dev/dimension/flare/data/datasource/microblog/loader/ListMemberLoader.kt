package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile

internal interface ListMemberLoader {
    suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listId: String,
    ): PagingResult<UiProfile>

    suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ): UiProfile

    suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    )

    suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList>
}
