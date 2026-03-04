package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.MastodonList
import dev.dimension.flare.data.network.mastodon.api.model.PostAccounts
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class MastodonListMemberLoader(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
) : ListMemberLoader {
    override suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listId: String,
    ): PagingResult<UiProfile> {
        val maxId =
            when (request) {
                is PagingRequest.Append -> request.nextKey
                is PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> return PagingResult()
            }

        val response =
            service.listMembers(
                listId = listId,
                limit = pageSize,
                max_id = maxId,
            )

        val users =
            response.map {
                it.render(accountKey = accountKey, host = accountKey.host)
            }

        return PagingResult(
            data = users,
            nextKey = response.next,
        )
    }

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ): UiProfile {
        service.addMember(
            listId = listId,
            accounts = PostAccounts(listOf(userKey.id)),
        )
        return service
            .lookupUser(userKey.id)
            .render(accountKey = accountKey, host = accountKey.host)
    }

    override suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        service.removeMember(
            listId = listId,
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
            id = id,
            title = title,
        )
    }
}
