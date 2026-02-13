package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.list.ListLoader
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersListsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsListRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsShowRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsUpdateRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal class MisskeyListLoader(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
) : ListLoader {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList> {
        if (request is PagingRequest.Prepend) {
            return PagingResult()
        }
        val result =
            service
                .usersListsList(
                    UsersListsListRequest(),
                ).orEmpty()
                .map {
                    it.render()
                }.toImmutableList()

        return PagingResult(
            data = result,
            nextKey = null,
        )
    }

    override suspend fun info(listKey: MicroBlogKey): UiList =
        service
            .usersListsShow(
                UsersListsShowRequest(
                    listId = listKey.id,
                ),
            ).render()

    override suspend fun create(metaData: ListMetaData): UiList {
        val response =
            service.usersListsCreate(
                UsersListsCreateRequest(
                    name = metaData.title,
                ),
            )
        return UiList.List(
            key = MicroBlogKey(response.id, accountKey.host),
            title = metaData.title,
            description = null,
            avatar = null,
            creator = null,
        )
    }

    override suspend fun update(
        listKey: MicroBlogKey,
        metaData: ListMetaData,
    ): UiList =
        service
            .usersListsUpdate(
                UsersListsUpdateRequest(
                    listId = listKey.id,
                    name = metaData.title,
                ),
            ).render()

    override suspend fun delete(listKey: MicroBlogKey) {
        service.usersListsDelete(
            UsersListsDeleteRequest(listId = listKey.id),
        )
    }

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf(ListMetaDataType.TITLE)
}
