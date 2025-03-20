package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.users
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class FansPagingSource(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : BasePagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        val cursor = params.key
        val limit = params.loadSize
        val response =
            service
                .getFollowers(
                    variables =
                        FollowVar(
                            userID = userKey.id,
                            count = limit.toLong(),
                            cursor = cursor,
                        ).encodeJson(),
                ).body()
        val users =
            response
                ?.data
                ?.user
                ?.result
                ?.timeline
                ?.timeline
                ?.instructions
                ?.users()
                .orEmpty()
        val nextCursor =
            response
                ?.data
                ?.user
                ?.result
                ?.timeline
                ?.timeline
                ?.instructions
                ?.cursor()
        return LoadResult.Page(
            data =
                users.map {
                    it.render(accountKey = accountKey)
                },
            prevKey = null,
            nextKey = nextCursor,
        )
    }
}

@Serializable
internal data class FollowVar(
    @SerialName("userId")
    val userID: String? = null,
    val count: Long? = null,
    val cursor: String? = null,
    @Required
    val includePromotedContent: Boolean = false,
)
