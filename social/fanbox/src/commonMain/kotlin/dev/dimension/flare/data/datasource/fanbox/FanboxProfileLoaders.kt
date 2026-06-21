package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.fanbox.FanboxFollowRequest
import dev.dimension.flare.data.network.fanbox.FanboxService
import dev.dimension.flare.data.network.fanbox.requireCsrfToken
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException

internal class FanboxLoader(
    private val accountKey: MicroBlogKey,
    private val service: FanboxService,
) : UserLoader,
    RelationLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val creatorId = uiHandle.normalizedRaw.removePrefix("@")
        val imageHeaders = service.fanboxImageHeaders()
        return runCatching {
            service
                .getCreator(creatorId = creatorId)
                .body
                .toUiProfile(
                    accountKey = accountKey,
                    imageHeaders = imageHeaders,
                )
        }.getOrElse { cause ->
            if (cause is CancellationException) {
                throw cause
            }
            service
                .searchCreatorsRaw(query = creatorId, page = 0)
                .body
                .creators
                .firstOrNull { it.creatorId == creatorId || it.user?.name == creatorId }
                ?.toUiProfile(
                    accountKey = accountKey,
                    imageHeaders = imageHeaders,
                )
                ?: throw NoSuchElementException("FANBOX creator not found: ${uiHandle.canonical}")
        }
    }

    override suspend fun userById(id: String): UiProfile {
        if (id == accountKey.id) {
            val credential = service.credentialWithCsrf()
            return credential
                .toUiProfile(
                    accountKey = accountKey,
                    profileKey = accountKey,
                    imageHeaders = credential.toFanboxImageHeaders(),
                )
        }
        return service
            .getCreator(creatorId = id)
            .body
            .toUiProfile(
                accountKey = accountKey,
                imageHeaders = service.fanboxImageHeaders(),
            )
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        if (userKey == accountKey) {
            return UiRelation()
        }
        val creator = service.getCreator(creatorId = userKey.id).body
        return UiRelation(
            following = creator.isFollowed,
            isFans = creator.isSupported,
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        val creator = service.getCreator(creatorId = userKey.id).body
        val userId = creator.user?.userId
        require(!userId.isNullOrBlank()) { "FANBOX creator user id is missing: ${userKey.id}" }
        val credential = service.credentialWithCsrf()
        service.followCreator(
            csrfToken = credential.requireCsrfToken(),
            request = FanboxFollowRequest(userId),
        )
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        val creator = service.getCreator(creatorId = userKey.id).body
        val userId = creator.user?.userId
        require(!userId.isNullOrBlank()) { "FANBOX creator user id is missing: ${userKey.id}" }
        val credential = service.credentialWithCsrf()
        service.unfollowCreator(
            csrfToken = credential.requireCsrfToken(),
            request = FanboxFollowRequest(userId),
        )
    }

    override suspend fun block(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("FANBOX block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("FANBOX block is not supported")

    override suspend fun mute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("FANBOX mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("FANBOX mute is not supported")

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        service
            .postInfo(postId = statusKey.id)
            .body
            .toUiTimeline(
                accountKey = accountKey,
                imageHeaders = service.fanboxImageHeaders(),
            )

    override suspend fun deleteStatus(statusKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("FANBOX post deletion is not supported")
}

internal class FanboxRecommendedCreatorLoader(
    private val service: FanboxService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "fanbox_recommended_creator_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request != PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        val imageHeaders = service.fanboxImageHeaders()
        return PagingResult(
            data =
                service
                    .listRecommendedCreators(limit = pageSize.coerceAtLeast(1))
                    .body
                    .creators
                    .map {
                        it.toUiProfile(
                            accountKey = accountKey,
                            imageHeaders = imageHeaders,
                        )
                    },
            endOfPaginationReached = true,
        )
    }
}

internal class FanboxFollowingCreatorLoader(
    private val service: FanboxService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "fanbox_following_creator_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request != PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        val imageHeaders = service.fanboxImageHeaders()
        return PagingResult(
            data =
                service
                    .listFollowingCreators()
                    .body
                    .creators
                    .map {
                        it.toUiProfile(
                            accountKey = accountKey,
                            imageHeaders = imageHeaders,
                        )
                    },
            endOfPaginationReached = true,
        )
    }
}

internal class FanboxSearchCreatorLoader(
    private val service: FanboxService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "fanbox_search_creator_${query}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.searchCreatorsRaw(query = query, page = 0)
                }

                is PagingRequest.Append -> {
                    service.searchCreatorsRaw(
                        query = query,
                        page = request.nextKey.toIntOrNull() ?: 0,
                    )
                }

                is PagingRequest.Prepend -> {
                    error("Handled above")
                }
            }
        val imageHeaders = service.fanboxImageHeaders()
        return PagingResult(
            data =
                response
                    .body
                    .creators
                    .map {
                        it.toUiProfile(
                            accountKey = accountKey,
                            imageHeaders = imageHeaders,
                        )
                    },
            nextKey = response.body.nextPage?.toString(),
            endOfPaginationReached = response.body.nextPage == null,
        )
    }
}
