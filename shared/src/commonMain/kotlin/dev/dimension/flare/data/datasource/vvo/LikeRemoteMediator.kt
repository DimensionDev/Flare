package dev.dimension.flare.data.datasource.vvo

import SnowflakeIdGenerator
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

internal class LikeRemoteMediator(
    private val service: VVOService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "like_$accountKey"
    private val containerId = "2308691748186704_-_mix"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                is Request.Append -> request.nextKey.toIntOrNull()
                else -> null
            }

        val response =
            when (request) {
                Request.Refresh -> {
                    service.getContainerIndex(containerId = containerId)
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.getContainerIndex(
                        containerId = containerId,
                        page = page,
                        openApp = 0,
                    )
                }
            }

        val status =
            response.data
                ?.cards
                ?.flatMap {
                    listOfNotNull(
                        it.mblog,
                    ) +
                        it.cardGroup
                            ?.mapNotNull {
                                it.mblog
                            }.orEmpty()
                }?.filter { it.user?.id != null }
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
        val nextKey = response.data?.cardlistInfo?.page

        return Result(
            endOfPaginationReached = nextKey == null,
            data = data,
            nextKey = nextKey?.toString(),
        )
    }
}
