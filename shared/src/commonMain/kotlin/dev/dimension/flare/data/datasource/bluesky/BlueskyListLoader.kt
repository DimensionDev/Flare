package dev.dimension.flare.data.datasource.bluesky

import app.bsky.graph.GetListQueryParams
import app.bsky.graph.GetListsQueryParams
import com.atproto.repo.ApplyWritesDelete
import com.atproto.repo.ApplyWritesRequest
import com.atproto.repo.ApplyWritesRequestWriteUnion
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.PutRecordRequest
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.list.ListLoader
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import kotlin.time.Clock

internal class BlueskyListLoader(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
) : ListLoader {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList> {
        val cursor =
            when (request) {
                is PagingRequest.Append -> request.nextKey
                is PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> return PagingResult()
            }
        val result =
            service
                .getLists(
                    params =
                        GetListsQueryParams(
                            actor = Did(did = accountKey.id),
                            cursor = cursor,
                            limit = pageSize.toLong(),
                        ),
                ).requireResponse()
        val items =
            result
                .lists
                .map {
                    it.render(accountKey)
                }.toImmutableList()
        return PagingResult(
            data = items,
            nextKey = result.cursor,
        )
    }

    override suspend fun info(listId: String): UiList =
        service
            .getList(
                GetListQueryParams(
                    list = AtUri(listId),
                ),
            ).requireResponse()
            .list
            .render(accountKey)

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() =
            persistentListOf(
                ListMetaDataType.TITLE,
                ListMetaDataType.DESCRIPTION,
                ListMetaDataType.AVATAR,
            )

    override suspend fun create(metaData: ListMetaData): UiList =
        createList(
            title = metaData.title,
            description = metaData.description,
            icon = metaData.avatar,
        )

    private suspend fun createList(
        title: String,
        description: String?,
        icon: FileItem?,
    ): UiList {
        val iconInfo =
            if (icon != null) {
                service.uploadBlob(icon.readBytes()).maybeResponse()
            } else {
                null
            }
        val record =
            app.bsky.graph.List(
                purpose = app.bsky.graph.Token.Curatelist,
                name = title,
                description = description,
                avatar = iconInfo?.blob,
                createdAt = Clock.System.now(),
            )
        val response =
            service
                .createRecord(
                    request =
                        CreateRecordRequest(
                            repo = Did(did = accountKey.id),
                            collection = Nsid("app.bsky.graph.list"),
                            record = record.bskyJson(),
                        ),
                ).requireResponse()

        val uri = response.uri
        return service
            .getList(
                params =
                    GetListQueryParams(
                        list = uri,
                    ),
            ).requireResponse()
            .list
            .render(accountKey)
    }

    override suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList {
        updateList(
            uri = listId,
            title = metaData.title,
            description = metaData.description,
            icon = metaData.avatar,
        )
        return info(listId)
    }

    private suspend fun updateList(
        uri: String,
        title: String,
        description: String?,
        icon: FileItem?,
    ) {
        val currentInfo: app.bsky.graph.List =
            service
                .getRecord(
                    params =
                        com.atproto.repo.GetRecordQueryParams(
                            collection = Nsid("app.bsky.graph.list"),
                            repo = Did(did = accountKey.id),
                            rkey = RKey(uri.substringAfterLast('/')),
                        ),
                ).requireResponse()
                .value
                .decodeAs()

        val iconInfo =
            if (icon != null) {
                service.uploadBlob(icon.readBytes()).maybeResponse()
            } else {
                null
            }
        val newRecord =
            currentInfo
                .copy(
                    name = title,
                    description = description,
                ).let {
                    if (iconInfo != null) {
                        it.copy(avatar = iconInfo.blob)
                    } else {
                        it
                    }
                }
        service.putRecord(
            request =
                PutRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.graph.list"),
                    rkey = RKey(uri.substringAfterLast('/')),
                    record = newRecord.bskyJson(),
                ),
        )
    }

    override suspend fun delete(listId: String) {
        val id = listId.substringAfterLast('/')
        service.applyWrites(
            request =
                ApplyWritesRequest(
                    repo = Did(did = accountKey.id),
                    writes =
                        persistentListOf(
                            ApplyWritesRequestWriteUnion.Delete(
                                value =
                                    ApplyWritesDelete(
                                        collection = Nsid("app.bsky.graph.list"),
                                        rkey = RKey(id),
                                    ),
                            ),
                        ),
                ),
        )
    }
}
