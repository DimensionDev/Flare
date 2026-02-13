package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.datasource.microblog.list.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersListsListRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsMembershipRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsPullRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.toImmutableList

internal class MisskeyListMemberLoader(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
) : ListMemberLoader {
    override suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listKey: MicroBlogKey,
    ): PagingResult<DbUser> {
        val cursor =
            when (request) {
                is PagingRequest.Append -> request.nextKey
                is PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> return PagingResult()
            }

        val response =
            service
                .usersListsGetMemberships(
                    UsersListsMembershipRequest(
                        listId = listKey.id,
                        untilId = cursor,
                        limit = pageSize,
                    ),
                ).orEmpty()

        val users =
            response.map {
                it.user.toDbUser(accountKey.host)
            }

        return PagingResult(
            data = users,
            nextKey = response.lastOrNull()?.id,
        )
    }

    override suspend fun addMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): DbUser {
        service.usersListsPush(
            UsersListsPullRequest(
                listId = listKey.id,
                userId = userKey.id,
            ),
        )
        return service
            .usersShow(
                UsersShowRequest(
                    userId = userKey.id,
                ),
            ).toDbUser(accountKey.host)
    }

    override suspend fun removeMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) {
        service.usersListsPull(
            UsersListsPullRequest(
                listId = listKey.id,
                userId = userKey.id,
            ),
        )
    }

    override suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList> {
        if (request is PagingRequest.Prepend) {
            return PagingResult()
        }
        if (request is PagingRequest.Append) {
            return PagingResult(nextKey = null)
        }

        val result =
            service
                .usersListsList(
                    UsersListsListRequest(),
                ).orEmpty()
                .filter {
                    it.userIds?.contains(userKey.id) == true
                }.map {
                    it.render()
                }.toImmutableList()

        return PagingResult(
            data = result,
            nextKey = null,
        )
    }
}
