package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
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
) : PagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        try {
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
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
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
