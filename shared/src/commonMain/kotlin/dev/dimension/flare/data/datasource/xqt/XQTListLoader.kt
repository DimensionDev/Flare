package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.datasource.microblog.list.ListLoader
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.CreateListRequest
import dev.dimension.flare.data.network.xqt.model.RemoveListRequest
import dev.dimension.flare.data.network.xqt.model.UpdateListRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.list
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal class XQTListLoader(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : ListLoader {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList> {
        val cursor = (request as? PagingRequest.Append)?.nextKey
        val response =
            service
                .getListsManagementPageTimeline(
                    variables =
                        buildString {
                            append("{\"count\":$pageSize")
                            if (cursor != null) {
                                append(",\"cursor\":\"${cursor}\"")
                            }
                            append("}")
                        },
                ).body()
                ?.data
                ?.viewer
                ?.listManagementTimeline
                ?.timeline
                ?.instructions

        val nextCursor = response?.cursor()

        val result =
            response
                ?.list(accountKey = accountKey)
                .orEmpty()

        return PagingResult(
            data = result,
            nextKey = nextCursor,
        )
    }

    override suspend fun info(listId: String): UiList =
        service
            .getListByRestId(
                variables = "{\"listId\":\"${listId}\"}",
            ).body()
            ?.data
            ?.list
            ?.render(accountKey = accountKey)
            ?: throw Exception("List not found")

    override suspend fun create(metaData: ListMetaData): UiList {
        val response =
            service.createList(
                request =
                    CreateListRequest(
                        variables =
                            CreateListRequest.Variables(
                                name = metaData.title,
                                description = metaData.description.orEmpty(),
                                isPrivate = false,
                            ),
                    ),
            )
        val data = response.body()?.data?.list
        if (data?.idStr != null) {
            return UiList.List(
                id = data.idStr,
                title = metaData.title,
                description = metaData.description,
                creator = null,
                avatar = null,
                readonly = false,
            )
        } else {
            throw Exception("Failed to create list")
        }
    }

    override suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList {
        service.updateList(
            request =
                UpdateListRequest(
                    variables =
                        UpdateListRequest.Variables(
                            listID = listId,
                            name = metaData.title,
                            description = metaData.description.orEmpty(),
                            isPrivate = false,
                        ),
                ),
        )
        return info(listId).let {
            if (it is UiList.List) {
                it.copy(
                    title = metaData.title,
                    description = metaData.description,
                )
            } else {
                it
            }
        }
    }

    override suspend fun delete(listId: String) {
        service.deleteList(
            request =
                RemoveListRequest(
                    variables =
                        RemoveListRequest.Variables(
                            listID = listId,
                        ),
                ),
        )
    }

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf(ListMetaDataType.TITLE, ListMetaDataType.DESCRIPTION)
}
