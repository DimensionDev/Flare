package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.list.ListLoader
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.MastodonList
import dev.dimension.flare.data.network.mastodon.api.model.PostList
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal class MastodonListLoader(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
) : ListLoader {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList> {
        if (request is PagingRequest.Prepend) {
            return PagingResult()
        }
        val lists = service.lists()
        return PagingResult(
            data = lists.mapNotNull { it.toUiList(accountKey) },
        )
    }

    override suspend fun info(listId: String): UiList =
        service.getList(listId).toUiList(accountKey)
            ?: error("Failed to parse list info")

    override suspend fun create(metaData: ListMetaData): UiList =
        service
            .createList(PostList(title = metaData.title))
            .toUiList(accountKey)
            ?: error("Failed to parse created list")

    override suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList =
        service
            .updateList(listId, PostList(title = metaData.title))
            .toUiList(accountKey)
            ?: error("Failed to parse updated list")

    override suspend fun delete(listId: String) {
        service.deleteList(listId)
    }

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf(ListMetaDataType.TITLE)

    private fun MastodonList.toUiList(accountKey: MicroBlogKey): UiList.List? {
        val id = id ?: return null
        val title = title ?: return null
        return UiList.List(
            id = id,
            title = title,
        )
    }
}
