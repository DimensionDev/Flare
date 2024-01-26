package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.SupportedComposeEvent
import dev.dimension.flare.data.datasource.microblog.XQTComposeData
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequestVariables
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestFeatures
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariablesMedia
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariablesMediaMediaEntitiesInner
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariablesReply
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostDeleteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostMediaMetadataCreateRequest
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import dev.dimension.flare.ui.model.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

private const val BULK_SIZE: Long = 512 * 1024L // 512 Kib
private const val MAX_ASYNC_UPLOAD_SIZE = 10

@OptIn(ExperimentalPagingApi::class)
class XQTDataSource(
    override val account: UiAccount.XQT,
) : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()
    private val service by lazy {
        XQTService(chocolate = account.credential.chocolate)
    }

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                HomeTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )
    }

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                MentionRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )
    }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.Mention)

    override fun userByAcct(acct: String): CacheData<UiUser> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    service.userByScreenName(name)
                        .body()
                        ?.data
                        ?.user
                        ?.result
                        ?.let {
                            when (it) {
                                is User -> it
                                is UserUnavailable -> null
                            }
                        }
                        ?.toDbUser() ?: throw Exception("User not found")
                database.dbUserQueries.insert(
                    user_key = user.user_key,
                    platform_type = user.platform_type,
                    name = user.name,
                    handle = user.handle,
                    host = user.host,
                    content = user.content,
                )
            },
            cacheSource = {
                database.dbUserQueries.findByHandleAndHost(name, host, PlatformType.xQt).asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi() }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
        val userKey = MicroBlogKey(id, account.accountKey.host)
        return Cacheable(
            fetchSource = {
                val user =
                    service.userById(id)
                        .body()
                        ?.data
                        ?.user
                        ?.result
                        ?.let {
                            when (it) {
                                is User -> it
                                is UserUnavailable -> null
                            }
                        }
                        ?.toDbUser() ?: throw Exception("User not found")
                database.dbUserQueries.insert(
                    user_key = user.user_key,
                    platform_type = user.platform_type,
                    name = user.name,
                    handle = user.handle,
                    host = user.host,
                    content = user.content,
                )
            },
            cacheSource = {
                database.dbUserQueries.findByKey(userKey).asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi() }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
        return MemCacheable<UiRelation>(
            relationKeyWithUserKey(userKey),
        ) {
            val userResponse =
                service.userById(userKey.id)
                    .body()
                    ?.data
                    ?.user
                    ?.result
                    ?.let {
                        when (it) {
                            is User -> it
                            is UserUnavailable -> null
                        }
                    } ?: throw Exception("User not found")
            val user = userResponse.toDbUser()

            service.profileSpotlights(user.handle)
                .body()
                ?.toUi(muting = userResponse.legacy.muting) ?: throw Exception("User not found")
        }.toUi()
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                if (mediaOnly) {
                    UserMediaTimelineRemoteMediator(
                        userKey,
                        service,
                        database,
                        account.accountKey,
                        pagingKey,
                    )
                } else {
                    UserTimelineRemoteMediator(
                        userKey,
                        service,
                        database,
                        account.accountKey,
                        pagingKey,
                    )
                },
        )
    }

    override fun context(
        statusKey: MicroBlogKey,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = 1,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                StatusDetailRemoteMediator(
                    statusKey,
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                    statusOnly = false,
                ),
        )

    override fun status(
        statusKey: MicroBlogKey,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = 1,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                StatusDetailRemoteMediator(
                    statusKey,
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                    statusOnly = true,
                ),
        )

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        require(data is XQTComposeData)
        val maxProgress = data.medias.size + 1
        val mediaIds =
            data.medias.mapIndexed { index, item ->
                uploadMedia(
                    mediaType = getMeidaTypeFromName(item.name),
                    mediaData = item.readBytes(),
                ).also {
                    if (data.sensitive) {
                        service.postMediaMetadataCreate(
                            body =
                                PostMediaMetadataCreateRequest(
                                    mediaId = it,
                                    sensitiveMediaWarning =
                                        listOf(
                                            PostMediaMetadataCreateRequest.SensitiveMediaWarning.Other,
                                        ),
                                ),
                        )
                    }
                    progress(ComposeProgress(index + 1, maxProgress))
                }
            }
        service.postCreateTweet(
            postCreateTweetRequest =
                PostCreateTweetRequest(
                    features = PostCreateTweetRequestFeatures(),
                    variables =
                        PostCreateTweetRequestVariables(
                            media =
                                PostCreateTweetRequestVariablesMedia(
                                    mediaEntities =
                                        mediaIds.map {
                                            PostCreateTweetRequestVariablesMediaMediaEntitiesInner(
                                                mediaId = it,
                                                taggedUsers = emptyList(),
                                            )
                                        },
                                ),
                            tweetText = data.content,
                            reply =
                                data.inReplyToID?.let {
                                    PostCreateTweetRequestVariablesReply(
                                        inReplyToTweetId = it,
                                        excludeReplyUserIds = emptyList(),
                                    )
                                },
                            semanticAnnotationIds = emptyList(),
                            attachmentUrl =
                                data.quoteId?.let {
                                    "https://twitter.com/${data.quoteUsername}/status/$it"
                                },
                        ),
                ),
        )
    }

    private fun getMeidaTypeFromName(name: String?): String {
        return when {
            name == null -> "image/jpeg"
            name.endsWith(".jpg") -> "image/jpeg"
            name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mov") -> "video/quicktime"
            else -> "image/jpeg"
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadMedia(
        mediaType: String,
        mediaData: ByteArray,
    ): String =
        coroutineScope {
            val totalBytes = mediaData.size.toLong()
            val mediaId =
                service.initUpload(
                    mediaType = mediaType,
                    totalBytes = totalBytes.toString(),
                    category = if (mediaType.contains("video")) "tweet_video" else "tweet_image",
                ).mediaIDString ?: throw Error("init upload failed")

            var streamReadLength = 0
            val uploadChunks = mutableListOf<ByteArray>()
            var uploadTimes = 0
            var uploadBytes = 0L

            suspend fun uploadAll() {
                uploadChunks.mapIndexed { index, array ->
                    async {
                        service.appendUpload(
                            mediaId = mediaId,
                            segmentIndex = (uploadTimes * MAX_ASYNC_UPLOAD_SIZE + index.toLong()).toString(),
                            mediaData = Base64.encode(array),
                        )
                        uploadBytes += array.size
                    }
                }.awaitAll()
                uploadTimes++
                uploadChunks.clear()
            }

            while (streamReadLength < totalBytes) {
                val currentBulkSize = BULK_SIZE.coerceAtMost(totalBytes - streamReadLength).toInt()
                val chunk =
                    mediaData.slice(streamReadLength until streamReadLength + currentBulkSize)
                uploadChunks.add(chunk.toByteArray())
                if (uploadChunks.size >= MAX_ASYNC_UPLOAD_SIZE) {
                    uploadAll()
                }
                streamReadLength += currentBulkSize
            }
            if (uploadChunks.isNotEmpty()) {
                uploadAll()
            }

            var checkCount = 0
            var response = service.finalizeUpload(mediaId)
            var awaitTime = response.processingInfo?.checkAfterSecs
            while (awaitTime != null) {
                delay(awaitTime.seconds)
                checkCount += 1
                response = service.uploadStatus(mediaId)
                awaitTime = response.processingInfo?.checkAfterSecs
            }

            val mediaIdString = checkNotNull(response.mediaIDString) { "upload failed" }
            mediaIdString
        }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        runCatching {
            service.postDeleteTweet(
                postDeleteTweetRequest =
                    PostDeleteTweetRequest(
                        variables =
                            PostCreateRetweetRequestVariables(
                                tweetId = statusKey.id,
                            ),
                    ),
            )
            // delete status from cache
            database.dbStatusQueries.delete(
                status_key = statusKey,
                account_key = account.accountKey,
            )
            database.dbPagingTimelineQueries.deleteStatus(
                account_key = account.accountKey,
                status_key = statusKey,
            )
        }
    }

    override fun searchStatus(
        query: String,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                SearchStatusPagingSource(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                    query,
                ),
        )
    }

    override fun searchUser(
        query: String,
        pageSize: Int,
    ): Flow<PagingData<UiUser>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                service,
                query,
            )
        }.flow
    }

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUser>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                service,
                account.accountKey,
            )
        }.flow
    }

    override fun discoverStatuses(
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        // not supported
        throw UnsupportedOperationException("Bluesky does not support discover statuses")
    }

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendHashtagPagingSource(
                service,
            )
        }.flow
    }

    override fun supportedComposeEvent(statusKey: MicroBlogKey?): List<SupportedComposeEvent> {
        return if (statusKey == null) {
            listOf(
//                SupportedComposeEvent.Poll,
                SupportedComposeEvent.Media,
            )
        } else {
            listOf()
        }
    }

    suspend fun like(status: UiStatus.XQT) {
        updateStatusUseCase<StatusContent.XQT>(
            statusKey = status.statusKey,
            accountKey = status.accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            legacy =
                                it.data.legacy?.copy(
                                    favorited = !status.reaction.liked,
                                    favoriteCount =
                                        if (status.reaction.liked) {
                                            it.data.legacy.favoriteCount.minus(1)
                                        } else {
                                            it.data.legacy.favoriteCount.plus(1)
                                        },
                                    // TODO: update if like retweeted
                                ),
                        ),
                )
            },
        )

        runCatching {
            if (status.reaction.liked) {
                service.postUnfavoriteTweet(
                    postUnfavoriteTweetRequest =
                        PostUnfavoriteTweetRequest(
                            variables = PostCreateRetweetRequestVariables(tweetId = status.statusKey.id),
                        ),
                )
            } else {
                service.postFavoriteTweet(
                    postFavoriteTweetRequest =
                        PostFavoriteTweetRequest(
                            variables =
                                PostCreateRetweetRequestVariables(
                                    tweetId = status.statusKey.id,
                                ),
                        ),
                )
            }
        }.onFailure {
            it.printStackTrace()
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                favourited = status.reaction.liked,
                                favouritesCount =
                                    if (status.reaction.liked) {
                                        it.data.favouritesCount?.plus(1)
                                    } else {
                                        it.data.favouritesCount?.minus(1)
                                    },
                            ),
                    )
                },
            )
        }.onSuccess {
//            updateStatusUseCase<StatusContent.XQT>(
//                statusKey = status.statusKey,
//                accountKey = status.accountKey,
//                cacheDatabase = database,
//                update = {
//                    it.copy(
//                        data = result,
//                    )
//                },
//            )
        }
    }

    suspend fun retweet(status: UiStatus.XQT) {
        updateStatusUseCase<StatusContent.XQT>(
            statusKey = status.statusKey,
            accountKey = status.accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            legacy =
                                it.data.legacy?.copy(
                                    retweeted = !status.reaction.retweeted,
                                    retweetCount =
                                        if (status.reaction.retweeted) {
                                            it.data.legacy.retweetCount.minus(1)
                                        } else {
                                            it.data.legacy.retweetCount.plus(1)
                                        },
                                    // TODO: update if retweet inner retweeted
                                ),
                        ),
                )
            },
        )

        runCatching {
            if (status.reaction.retweeted) {
                service.postDeleteRetweet(
                    postDeleteRetweetRequest =
                        PostDeleteRetweetRequest(
                            variables = PostDeleteRetweetRequestVariables(sourceTweetId = status.statusKey.id),
                        ),
                )
            } else {
                service.postCreateRetweet(
                    postCreateRetweetRequest =
                        PostCreateRetweetRequest(
                            variables =
                                PostCreateRetweetRequestVariables(
                                    tweetId = status.statusKey.id,
                                ),
                        ),
                )
            }
        }.onFailure {
            it.printStackTrace()
            updateStatusUseCase<StatusContent.XQT>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                legacy =
                                    it.data.legacy?.copy(
                                        retweeted = status.reaction.retweeted,
                                        retweetCount =
                                            if (status.reaction.retweeted) {
                                                it.data.legacy.retweetCount.minus(1)
                                            } else {
                                                it.data.legacy.retweetCount.plus(1)
                                            },
                                    ),
                            ),
                    )
                },
            )
        }.onSuccess {
//            updateStatusUseCase<StatusContent.XQT>(
//                statusKey = status.statusKey,
//                accountKey = status.accountKey,
//                cacheDatabase = database,
//                update = {
//                    it.copy(
//                        data = result,
//                    )
//                },
//            )
        }
    }

    suspend fun bookmark(status: UiStatus.XQT) {
        updateStatusUseCase<StatusContent.XQT>(
            statusKey = status.statusKey,
            accountKey = status.accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            legacy =
                                it.data.legacy?.copy(
                                    bookmarked = !status.reaction.bookmarked,
                                ),
                        ),
                )
            },
        )

        runCatching {
            if (status.reaction.bookmarked) {
                service.postDeleteBookmark(
                    postDeleteBookmarkRequest =
                        DeleteBookmarkRequest(
                            variables =
                                DeleteBookmarkRequestVariables(
                                    tweetId = status.statusKey.id,
                                ),
                        ),
                )
            } else {
                service.postCreateBookmark(
                    postCreateBookmarkRequest =
                        CreateBookmarkRequest(
                            variables =
                                CreateBookmarkRequestVariables(
                                    tweetId = status.statusKey.id,
                                ),
                        ),
                )
            }
        }.onFailure {
            it.printStackTrace()
            updateStatusUseCase<StatusContent.XQT>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                legacy =
                                    it.data.legacy?.copy(
                                        bookmarked = status.reaction.bookmarked,
                                    ),
                            ),
                    )
                },
            )
        }.onSuccess {
        }
    }

    suspend fun follow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.XQT>(
            key = key,
        ) {
            it.copy(
                following = true,
            )
        }
        runCatching {
            service.postCreateFriendships(userId = userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.XQT>(
                key = key,
            ) {
                it.copy(
                    following = false,
                )
            }
        }
    }

    suspend fun unfollow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.XQT>(
            key = key,
        ) {
            it.copy(
                following = false,
            )
        }
        runCatching {
            service.postDestroyFriendships(userId = userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.XQT>(
                key = key,
            ) {
                it.copy(
                    following = true,
                )
            }
        }
    }

    suspend fun mute(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.XQT>(
            key = key,
        ) {
            it.copy(
                muting = true,
            )
        }
        runCatching {
            service.postMutesUsersCreate(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.XQT>(
                key = key,
            ) {
                it.copy(
                    muting = false,
                )
            }
        }
    }

    suspend fun unmute(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.XQT>(
            key = key,
        ) {
            it.copy(
                muting = false,
            )
        }
        runCatching {
            service.postMutesUsersDestroy(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.XQT>(
                key = key,
            ) {
                it.copy(
                    muting = true,
                )
            }
        }
    }

    suspend fun block(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.XQT>(
            key = key,
        ) {
            it.copy(
                blocking = true,
            )
        }
        runCatching {
            service.postBlocksCreate(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.XQT>(
                key = key,
            ) {
                it.copy(
                    blocking = false,
                )
            }
        }
    }

    suspend fun unblock(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.XQT>(
            key = key,
        ) {
            it.copy(
                blocking = false,
            )
        }
        runCatching {
            service.postBlocksDestroy(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.XQT>(
                key = key,
            ) {
                it.copy(
                    blocking = true,
                )
            }
        }
    }
}
