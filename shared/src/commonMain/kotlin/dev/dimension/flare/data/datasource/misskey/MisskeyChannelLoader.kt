package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.list.ListLoader
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.ChannelsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFollowRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFollowedRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsUpdateRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal class MisskeyChannelLoader(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val source: Source,
) : ListLoader {
    enum class Source {
        Followed,
        MyFavorites,
        Owned,
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList> {
        if (request is PagingRequest.Prepend) {
            return PagingResult()
        }
        val untilId =
            when (request) {
                is PagingRequest.Append -> request.nextKey
                else -> null
            }

        val result =
            when (source) {
                Source.Followed ->
                    service.channelsFollowed(
                        ChannelsFollowedRequest(
                            untilId = untilId,
                            limit = pageSize,
                        ),
                    )

                Source.MyFavorites ->
                    service.channelsMyFavorites(
                        ChannelsFollowedRequest(
                            untilId = untilId,
                            limit = pageSize,
                        ),
                    )

                Source.Owned ->
                    service.channelsOwned(
                        ChannelsFollowedRequest(
                            untilId = untilId,
                            limit = pageSize,
                        ),
                    )
            }.map {
                it.render()
            }.toImmutableList()

        return PagingResult(
            data = result,
            nextKey = result.lastOrNull()?.id,
        )
    }

    override suspend fun info(listId: String): UiList =
        service
            .channelsShow(
                ChannelsFollowRequest(
                    channelId = listId,
                ),
            ).render()

    override suspend fun create(metaData: ListMetaData): UiList =
        service
            .channelsCreate(
                ChannelsCreateRequest(
                    name = metaData.title,
                    description = metaData.description,
                ),
            ).render()

    override suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList =
        service
            .channelsUpdate(
                ChannelsUpdateRequest(
                    channelId = listId,
                    name = metaData.title,
                    description = metaData.description,
                ),
            ).render()

    override suspend fun delete(listId: String): Unit = throw UnsupportedOperationException("Delete channel is not supported")

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf(ListMetaDataType.TITLE, ListMetaDataType.DESCRIPTION)
}
