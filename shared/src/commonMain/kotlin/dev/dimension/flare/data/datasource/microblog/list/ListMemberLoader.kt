package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList

internal interface ListMemberLoader {
    suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listKey: MicroBlogKey,
    ): PagingResult<DbUser>

    suspend fun addMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): DbUser

    suspend fun removeMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    )

    suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList>
}
