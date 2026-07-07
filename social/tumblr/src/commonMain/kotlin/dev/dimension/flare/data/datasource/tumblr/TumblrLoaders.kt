package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.tumblr.TumblrBlog
import dev.dimension.flare.data.network.tumblr.TumblrPost
import dev.dimension.flare.data.network.tumblr.TumblrService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

private const val DEFAULT_PAGE_SIZE = 20

internal class TumblrHomeTimelineLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "tumblr_home_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull()
        val response =
            service.dashboard(
                limit = pageSize.coercePageSize(),
                offset = offset,
            )
        return response.posts.toTimelineResult(
            accountKey = accountKey,
            nextOffset = (offset ?: 0) + response.posts.size,
            pageSize = pageSize,
        )
    }
}

internal class TumblrBlogTimelineLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
    private val blogKey: MicroBlogKey,
    private val mediaOnly: Boolean = false,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "tumblr_blog_${blogKey.id}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull()
        val response =
            service.blogPosts(
                blogIdentifier = blogKey.toTumblrBlogIdentifier(),
                limit = pageSize.coercePageSize(),
                offset = offset,
            )
        val posts =
            if (mediaOnly) {
                response.posts.filter { it.hasMedia() }
            } else {
                response.posts
            }
        return posts.toTimelineResult(
            accountKey = accountKey,
            nextOffset = (offset ?: 0) + response.posts.size,
            pageSize = pageSize,
        )
    }
}

internal class TumblrTaggedTimelineLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
    private val tag: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "tumblr_tag_${tag}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val before = (request as? PagingRequest.Append)?.nextKey?.toLongOrNull()
        val posts =
            service.tagged(
                tag = tag,
                limit = pageSize.coercePageSize(),
                beforeTimestampSeconds = before,
            )
        val nextKey =
            posts
                .mapNotNull { it.timestampEpochSeconds }
                .minOrNull()
                ?.takeIf { posts.size >= pageSize.coercePageSize() }
                ?.toString()
        return PagingResult(
            data = posts.map { it.toUiTimeline(accountKey) },
            nextKey = nextKey,
            endOfPaginationReached = nextKey == null,
        )
    }
}

internal class TumblrStatusDetailLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "tumblr_status_${statusKey.id}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
            return PagingResult(endOfPaginationReached = true)
        }
        val parts = statusKey.toTumblrPostKeyParts()
        val post =
            service.post(
                blogIdentifier = parts.blogName,
                postId = parts.postId,
            )
        return PagingResult(
            data = listOfNotNull(post?.toUiTimeline(accountKey)),
            endOfPaginationReached = true,
        )
    }
}

internal class TumblrBlogProfileLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "tumblr_profile_${query}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
            return PagingResult(endOfPaginationReached = true)
        }
        val blog =
            runCatching {
                service.blogInfo(query.normalizedTumblrBlogName())
            }.getOrNull()
        return PagingResult(
            data = listOfNotNull(blog?.toUiProfile(accountKey)),
            endOfPaginationReached = true,
        )
    }
}

internal class TumblrFollowingLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "tumblr_following_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull()
        val response = service.following(pageSize.coercePageSize(), offset)
        return response.blogs.toProfileResult(accountKey, offset, pageSize)
    }
}

internal class TumblrFollowersLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
    private val blogKey: MicroBlogKey,
) : CacheableRemoteLoader<UiProfile> {
    override val pagingKey: String = "tumblr_followers_${blogKey.id}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val offset = (request as? PagingRequest.Append)?.nextKey?.toIntOrNull()
        val response =
            service.followers(
                blogIdentifier = blogKey.toTumblrBlogIdentifier(),
                limit = pageSize.coercePageSize(),
                offset = offset,
            )
        return response.users.toProfileResult(accountKey, offset, pageSize)
    }
}

private fun List<TumblrPost>.toTimelineResult(
    accountKey: MicroBlogKey,
    nextOffset: Int,
    pageSize: Int,
): PagingResult<UiTimelineV2> {
    val nextKey = nextOffset.takeIf { size >= pageSize.coercePageSize() }?.toString()
    return PagingResult(
        data = map { it.toUiTimeline(accountKey) },
        nextKey = nextKey,
        endOfPaginationReached = nextKey == null,
    )
}

private fun List<TumblrBlog>.toProfileResult(
    accountKey: MicroBlogKey,
    offset: Int?,
    pageSize: Int,
): PagingResult<UiProfile> {
    val nextOffset = (offset ?: 0) + size
    val nextKey = nextOffset.takeIf { size >= pageSize.coercePageSize() }?.toString()
    return PagingResult(
        data = map { it.toUiProfile(accountKey) },
        nextKey = nextKey,
        endOfPaginationReached = nextKey == null,
    )
}

private fun TumblrPost.hasMedia(): Boolean =
    photos.isNotEmpty() ||
        content.any { block ->
            when (block.type) {
                "image", "video", "audio" -> {
                    block.media.isNotEmpty() ||
                        block.url != null ||
                        block.poster.isNotEmpty() ||
                        block.thumbnailUrl != null
                }

                else -> {
                    block.media.isNotEmpty()
                }
            }
        }

private fun Int.coercePageSize(): Int = coerceIn(1, DEFAULT_PAGE_SIZE)
