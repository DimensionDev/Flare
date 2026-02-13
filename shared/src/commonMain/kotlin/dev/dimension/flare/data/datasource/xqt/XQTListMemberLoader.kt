package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.users
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.datasource.microblog.list.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.AddMemberRequest
import dev.dimension.flare.data.network.xqt.model.RemoveMemberRequest
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render

internal class XQTListMemberLoader(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : ListMemberLoader {
    override suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listKey: MicroBlogKey,
    ): PagingResult<DbUser> {
        val cursor = (request as? PagingRequest.Append)?.nextKey
        val response =
            service
                .getListMembers(
                    variables =
                        buildString {
                            append("{\"listId\":\"${listKey.id}\",\"count\":$pageSize")
                            if (cursor != null) {
                                append(",\"cursor\":\"${cursor}\"")
                            }
                            append("}")
                        },
                ).body()
                ?.data
                ?.list
                ?.membersTimeline
                ?.timeline
                ?.instructions

        val nextCursor = response?.cursor()

        val result =
            response?.users().orEmpty().map {
                it.toDbUser(accountKey = accountKey)
            }

        return PagingResult(
            data = result,
            nextKey = nextCursor,
        )
    }

    override suspend fun addMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): DbUser {
        service.addMember(
            request =
                AddMemberRequest(
                    variables =
                        AddMemberRequest.Variables(
                            listID = listKey.id,
                            userID = userKey.id,
                        ),
                ),
        )
        return service
            .userById(userKey.id)
            .body()
            ?.data
            ?.user
            ?.result
            ?.let {
                when (it) {
                    is User -> it
                    is UserUnavailable -> null
                }
            }?.toDbUser(accountKey)
            ?: throw Exception("User not found")
    }

    override suspend fun removeMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) {
        service.removeMember(
            request =
                RemoveMemberRequest(
                    variables =
                        RemoveMemberRequest.Variables(
                            listID = listKey.id,
                            userID = userKey.id,
                        ),
                ),
        )
    }

    override suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList> {
        // XQT getListsMemberships seems to return all lists or doesn't support pagination in the used endpoint/method signature easily?
        // The original implementation didn't use pagination.
        val result =
            service
                .getListsMemberships(
                    userId = userKey.id,
                ).body()
                ?.lists
                ?.map {
                    it.render(accountKey = accountKey)
                }.orEmpty()

        return PagingResult(
            data = result,
        )
    }
}
