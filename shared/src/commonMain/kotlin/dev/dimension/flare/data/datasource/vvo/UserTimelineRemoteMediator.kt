package dev.dimension.flare.data.datasource.vvo

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvo

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val mediaOnly: Boolean,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey =
        buildString {
            append("user_timeline")
            if (mediaOnly) {
                append("_mediaOnly")
            }
            append(accountKey.toString())
            append(userKey.toString())
        }
    private var containerid: String? = null

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        if (mediaOnly) {
            // Not supported yet
            return Result(
                endOfPaginationReached = true,
            )
        }

        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }
        if (containerid == null) {
            containerid =
                service
                    .getContainerIndex(type = "uid", value = userKey.id)
                    .data
                    ?.tabsInfo
                    ?.tabs
                    ?.firstOrNull {
                        it.tabType == vvo
                    }?.containerid
        }
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getContainerIndex(
                            type = "uid",
                            value = userKey.id,
                            containerId = containerid,
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.getContainerIndex(
                        type = "uid",
                        value = userKey.id,
                        containerId = containerid,
                        sinceId = request.nextKey,
                    )
                }
            }
        val status =
            response.data
                ?.cards
                ?.mapNotNull { it.mblog }
                .orEmpty()

        val data =
            status.map { statusItem ->
                statusItem.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = {
                        -SnowflakeIdGenerator.nextId()
                    },
                )
            }

        return Result(
            endOfPaginationReached = response.data?.cardlistInfo?.sinceID == null,
            data = data,
            nextKey =
                response.data
                    ?.cardlistInfo
                    ?.sinceID
                    ?.toString(),
        )
    }
}
