package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.datasource.microblog.list.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.MastodonList
import dev.dimension.flare.data.network.mastodon.api.model.PostAccounts
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList

internal class MastodonListMemberLoader(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
) : ListMemberLoader {
    override suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listKey: MicroBlogKey,
    ): PagingResult<DbUser> {
        val maxId =
            when (request) {
                is PagingRequest.Append -> request.nextKey
                is PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> return PagingResult()
            }

        val response =
            service.listMembers(
                listId = listKey.id,
                limit = pageSize,
                max_id = maxId,
            )

        val users =
            response.map {
                it.toDbUser(accountKey.host)
            }

        return PagingResult(
            data = users,
            nextKey = response.next,
        )
    }

    override suspend fun addMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): DbUser {
        service.addMember(
            listId = listKey.id,
            accounts = PostAccounts(listOf(userKey.id)),
        )
        return service
            .lookupUser(userKey.id)
            .toDbUser(accountKey.host)
    }

    override suspend fun removeMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) {
        service.removeMember(
            listId = listKey.id,
            accounts = PostAccounts(listOf(userKey.id)),
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
        val response = service.accountLists(userKey.id)
        val lists =
            response.mapNotNull {
                it.toUiList(accountKey)
            }
        return PagingResult(
            data = lists,
        )
    }

    private fun MastodonList.toUiList(accountKey: MicroBlogKey): UiList.List? {
        val id = id ?: return null
        val title = title ?: return null
        return UiList.List(
            key = MicroBlogKey(id = id, host = accountKey.host),
            title = title,
        )
    }
}
