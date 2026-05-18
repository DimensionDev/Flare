package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile

public interface ListMemberLoader {
    public suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listId: String,
    ): PagingResult<UiProfile>

    public suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ): UiProfile

    public suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    )

    public suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList>
}
