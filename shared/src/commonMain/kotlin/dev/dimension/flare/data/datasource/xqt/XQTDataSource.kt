package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.database.cache.mapper.users
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.datasource.microblog.ListMetaData
import dev.dimension.flare.data.datasource.microblog.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.MemoryPagingSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.StatusActionResult
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.createSendingDirectMessage
import dev.dimension.flare.data.datasource.microblog.memoryPager
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.AddMemberRequest
import dev.dimension.flare.data.network.xqt.model.AddToConversationRequest
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequestVariables
import dev.dimension.flare.data.network.xqt.model.CreateListRequest
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequestVariables
import dev.dimension.flare.data.network.xqt.model.LiveVideoStreamStatusResponse
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
import dev.dimension.flare.data.network.xqt.model.PostDmNew2Request
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostMediaMetadataCreateRequest
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.RemoveListRequest
import dev.dimension.flare.data.network.xqt.model.RemoveMemberRequest
import dev.dimension.flare.data.network.xqt.model.UpdateListRequest
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.list
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.toUi
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val BULK_SIZE: Long = 512 * 1024L // 512 Kib
private const val MAX_ASYNC_UPLOAD_SIZE = 10

@OptIn(ExperimentalPagingApi::class)
internal class XQTDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    StatusEvent.XQT,
    ListDataSource,
    DirectMessageDataSource {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountRepository: AccountRepository by inject()
    private val inAppNotification: InAppNotification by inject()
    private val credentialFlow by lazy {
        accountRepository
            .credentialFlow<UiAccount.XQT.Credential>(accountKey)
            .distinctUntilChanged()
    }
    private val service by lazy {
        XQTService(
            accountKey = accountKey,
            chocolateFlow =
                credentialFlow
                    .map { it.chocolate },
        )
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            service,
            database,
            accountKey,
            inAppNotification,
        )

    fun featuredTimeline(
        pageSize: Int = 20,
        pagingKey: String = "featured_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            accountRepository = accountRepository,
            mediator = featuredTimelineLoader(),
        )

    fun featuredTimelineLoader() =
        FeaturedTimelineRemoteMediator(
            service,
            database,
            accountKey,
        )

    fun bookmarkTimeline(
        pageSize: Int = 20,
        pagingKey: String = "bookmark_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            accountRepository = accountRepository,
            mediator = bookmarkTimelineLoader(),
        )

    fun bookmarkTimelineLoader() =
        BookmarkTimelineRemoteMediator(
            service,
            database,
            accountKey,
        )

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> {
        if (type == NotificationFilter.All) {
            return Pager(
                config = pagingConfig,
            ) {
                NotificationPagingSource(
                    locale = "en",
                    service = service,
                    accountKey = accountKey,
                    event = this,
                    onClearMarker = {
                        MemCacheable.update(notificationMarkerKey, 0)
                    },
                )
            }.flow.cachedIn(scope)
        } else {
            return timelinePager(
                pageSize = pageSize,
                database = database,
                scope = scope,
                filterFlow = localFilterRepository.getFlow(forNotification = true),
                accountRepository = accountRepository,
                mediator =
                    MentionRemoteMediator(
                        service,
                        database,
                        accountKey,
                    ),
            )
        }
    }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All, NotificationFilter.Mention)

    override fun userByAcct(acct: String): CacheData<UiUserV2> {
        val (name, host) = MicroBlogKey.valueOf(acct.removePrefix("@"))
        return Cacheable(
            fetchSource = {
                val user =
                    service
                        .userByScreenName(name)
                        .body()
                        ?.data
                        ?.user
                        ?.result
                        ?.let {
                            when (it) {
                                is User -> it
                                is UserUnavailable -> null
                            }
                        }?.toDbUser(accountKey) ?: throw Exception("User not found")
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByHandleAndHost(name, host, PlatformType.xQt)
                    .distinctUntilChanged()
                    .mapNotNull { it?.render(accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiProfile> {
        val userKey = MicroBlogKey(id, accountKey.host)
        return Cacheable(
            fetchSource = {
                val user =
                    service
                        .userById(id)
                        .body()
                        ?.data
                        ?.user
                        ?.result
                        ?.let {
                            when (it) {
                                is User -> it
                                is UserUnavailable -> null
                            }
                        }?.toDbUser(accountKey) ?: throw Exception("User not found")
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByKey(userKey)
                    .distinctUntilChanged()
                    .mapNotNull { it?.render(accountKey) }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> =
        MemCacheable<UiRelation>(
            relationKeyWithUserKey(userKey),
        ) {
            val userResponse =
                service
                    .userById(userKey.id)
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
            val user = userResponse.toDbUser(accountKey)

            service
                .profileSpotlights(user.handle)
                .body()
                ?.toUi(muting = userResponse.legacy.muting) ?: throw Exception("User not found")
        }.toUi()

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = if (mediaOnly) {
        UserMediaTimelineRemoteMediator(
            userKey,
            service,
            database,
            accountKey,
        )
    } else {
        UserTimelineRemoteMediator(
            userKey,
            service,
            database,
            accountKey,
        )
    }

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey = statusKey,
            service = service,
            database = database,
            accountKey = accountKey,
            statusOnly = false,
            event = this,
        )

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"
        return Cacheable(
            fetchSource = {
                val response =
                    service
                        .getTweetDetail(
                            variables =
                                TweetDetailRequest(
                                    focalTweetID = statusKey.id,
                                    cursor = null,
                                ).encodeJson(),
                        ).body()
                        ?.data
                        ?.threadedConversationWithInjectionsV2
                        ?.instructions
                        .orEmpty()
                val tweet = response.tweets()
                val item = tweet.firstOrNull { it.id == statusKey.id }
                if (item != null) {
                    XQT.save(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        database = database,
                        tweet = listOf(item),
                    )
                } else {
                    throw Exception("Status not found")
                }
            },
            cacheSource = {
                database
                    .statusDao()
                    .get(statusKey, accountType = AccountType.Specific(accountKey))
                    .distinctUntilChanged()
                    .mapNotNull { it?.content?.render(this) }
            },
        )
    }

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        val inReplyToID =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Reply
                }?.statusKey
                ?.id
        val quoteId =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Quote
                }?.statusKey
                ?.id
        val quoteUserName =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Quote
                }?.let {
                    data.referenceStatus.data.content as? UiTimeline.ItemContent.Status
                }?.user
                ?.handle
                ?.removePrefix("@")
                ?.removeSuffix("@${accountKey.host}")
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
                                inReplyToID?.let {
                                    PostCreateTweetRequestVariablesReply(
                                        inReplyToTweetId = it,
                                        excludeReplyUserIds = emptyList(),
                                    )
                                },
                            semanticAnnotationIds = emptyList(),
                            attachmentUrl =
                                quoteId?.let {
                                    "https://${accountKey.host}/$quoteUserName/status/$it"
                                },
                        ),
                ),
        )
    }

    private fun getMeidaTypeFromName(name: String?): String =
        when {
            name == null -> "image/jpeg"
            name.endsWith(".jpg") -> "image/jpeg"
            name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mov") -> "video/quicktime"
            else -> "image/jpeg"
        }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadMedia(
        mediaType: String,
        mediaData: ByteArray,
    ): String =
        coroutineScope {
            val totalBytes = mediaData.size.toLong()
            val mediaId =
                service
                    .initUpload(
                        mediaType = mediaType,
                        totalBytes = totalBytes.toString(),
                        category = if (mediaType.contains("video")) "tweet_video" else "tweet_image",
                    ).mediaIDString ?: throw Error("init upload failed")

            var streamReadLength = 0
            val uploadChunks = mutableListOf<ByteArray>()
            var uploadTimes = 0
            var uploadBytes = 0L

            suspend fun uploadAll() {
                uploadChunks
                    .mapIndexed { index, array ->
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
        tryRun {
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
            database.connect {
                database.statusDao().delete(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                )
                database.statusReferenceDao().delete(statusKey)
                database.pagingTimelineDao().deleteStatus(
                    accountKey = accountKey,
                    statusKey = statusKey,
                )
            }
        }
    }

    override fun searchStatus(query: String) =
        SearchStatusPagingSource(
            service,
            database,
            accountKey,
            query,
        )

    override fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = pagingConfig,
        ) {
            SearchUserPagingSource(
                service = service,
                accountKey = accountKey,
                query = query,
            )
        }.flow

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> =
        Pager(
            config = pagingConfig,
        ) {
            TrendsUserPagingSource(
                service,
                accountKey,
            )
        }.flow

    override fun discoverStatuses(): BaseTimelineLoader {
        // not supported
        throw UnsupportedOperationException("XQT does not support discover statuses")
    }

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
        Pager(
            config = pagingConfig,
        ) {
            TrendHashtagPagingSource(
                service,
            )
        }.flow

    override fun composeConfig(statusKey: MicroBlogKey?): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(280),
            media =
                if (statusKey != null) {
                    null
                } else {
                    ComposeConfig.Media(4, true)
                },
        )

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        when {
            relation.following -> unfollow(userKey)
            relation.blocking -> unblock(userKey)
            else -> follow(userKey)
        }
    }

    override fun profileActions(): List<ProfileAction> =
        listOf(
            object : ProfileAction.Mute {
                override suspend fun invoke(
                    userKey: MicroBlogKey,
                    relation: UiRelation,
                ) {
                    if (relation.muted) {
                        unmute(userKey)
                    } else {
                        mute(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean = relation.muted
            },
            object : ProfileAction.Block {
                override suspend fun invoke(
                    userKey: MicroBlogKey,
                    relation: UiRelation,
                ) {
                    if (relation.blocking) {
                        unblock(userKey)
                    } else {
                        block(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean = relation.blocking
            },
        )

    override fun like(
        statusKey: MicroBlogKey,
        liked: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.XQT>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                legacy =
                                    it.data.legacy?.copy(
                                        favorited = !liked,
                                        favoriteCount =
                                            if (liked) {
                                                it.data.legacy.favoriteCount
                                                    .minus(1)
                                            } else {
                                                it.data.legacy.favoriteCount
                                                    .plus(1)
                                            },
                                    ),
                            ),
                    )
                },
            )

            tryRun {
                if (liked) {
                    service.postUnfavoriteTweet(
                        postUnfavoriteTweetRequest =
                            PostUnfavoriteTweetRequest(
                                variables = PostCreateRetweetRequestVariables(tweetId = statusKey.id),
                            ),
                    )
                } else {
                    service.postFavoriteTweet(
                        postFavoriteTweetRequest =
                            PostFavoriteTweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.XQT>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    legacy =
                                        it.data.legacy?.copy(
                                            favorited = liked,
                                            favoriteCount =
                                                if (liked) {
                                                    it.data.legacy.favoriteCount
                                                        .plus(1)
                                                } else {
                                                    it.data.legacy.favoriteCount
                                                        .minus(1)
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
    }

    override fun likeWithResult(
        statusKey: MicroBlogKey,
        shouldLike: Boolean,
    ): StatusActionResult =
        runBlocking {
            try {
                if (shouldLike) {
                    service.postFavoriteTweet(
                        postFavoriteTweetRequest =
                            PostFavoriteTweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                } else {
                    service.postUnfavoriteTweet(
                        postUnfavoriteTweetRequest =
                            PostUnfavoriteTweetRequest(
                                variables = PostCreateRetweetRequestVariables(tweetId = statusKey.id),
                            ),
                    )
                }

                updateLikeStatus(statusKey, shouldLike)

                StatusActionResult.success()
            } catch (e: Exception) {
                StatusActionResult.failure(e)
            }
        }

    override fun retweet(
        statusKey: MicroBlogKey,
        retweeted: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.XQT>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                legacy =
                                    it.data.legacy?.copy(
                                        retweeted = !retweeted,
                                        retweetCount =
                                            if (retweeted) {
                                                it.data.legacy.retweetCount
                                                    .minus(1)
                                            } else {
                                                it.data.legacy.retweetCount
                                                    .plus(1)
                                            },
                                    ),
                            ),
                    )
                },
            )

            tryRun {
                if (retweeted) {
                    service.postDeleteRetweet(
                        postDeleteRetweetRequest =
                            PostDeleteRetweetRequest(
                                variables = PostDeleteRetweetRequestVariables(sourceTweetId = statusKey.id),
                            ),
                    )
                } else {
                    service.postCreateRetweet(
                        postCreateRetweetRequest =
                            PostCreateRetweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                }
            }.onFailure {
                it.printStackTrace()
                updateStatusUseCase<StatusContent.XQT>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    legacy =
                                        it.data.legacy?.copy(
                                            retweeted = retweeted,
                                            retweetCount =
                                                if (retweeted) {
                                                    it.data.legacy.retweetCount
                                                        .plus(1)
                                                } else {
                                                    it.data.legacy.retweetCount
                                                        .minus(1)
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
    }

    override fun bookmark(
        statusKey: MicroBlogKey,
        bookmarked: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.XQT>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                legacy =
                                    it.data.legacy?.copy(
                                        bookmarked = !bookmarked,
                                        bookmarkCount =
                                            if (bookmarked) {
                                                maxOf(0, (it.data.legacy.bookmarkCount ?: 1) - 1)
                                            } else {
                                                (it.data.legacy.bookmarkCount ?: 0) + 1
                                            },
                                    ),
                            ),
                    )
                },
            )

            tryRun {
                if (bookmarked) {
                    service.postDeleteBookmark(
                        postDeleteBookmarkRequest =
                            DeleteBookmarkRequest(
                                variables =
                                    DeleteBookmarkRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                } else {
                    service.postCreateBookmark(
                        postCreateBookmarkRequest =
                            CreateBookmarkRequest(
                                variables =
                                    CreateBookmarkRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.XQT>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    legacy =
                                        it.data.legacy?.copy(
                                            bookmarked = bookmarked,
                                            bookmarkCount =
                                                if (bookmarked) {
                                                    maxOf(0, (it.data.legacy.bookmarkCount ?: 1) - 1)
                                                } else {
                                                    (it.data.legacy.bookmarkCount ?: 0) + 1
                                                },
                                        ),
                                ),
                        )
                    },
                )
            }.onSuccess {
            }
        }
    }

    override fun bookmarkWithResult(
        statusKey: MicroBlogKey,
        shouldBookmark: Boolean,
    ): StatusActionResult =
        runBlocking {
            try {
                if (shouldBookmark) {
                    service.postCreateBookmark(
                        postCreateBookmarkRequest =
                            CreateBookmarkRequest(
                                variables =
                                    CreateBookmarkRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                } else {
                    service.postDeleteBookmark(
                        postDeleteBookmarkRequest =
                            DeleteBookmarkRequest(
                                variables =
                                    DeleteBookmarkRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                }

                updateBookmarkStatus(statusKey, shouldBookmark)

                StatusActionResult.success()
            } catch (e: Exception) {
                StatusActionResult.failure(e)
            }
        }

    override fun retweetWithResult(
        statusKey: MicroBlogKey,
        shouldRetweet: Boolean,
    ): StatusActionResult =
        runBlocking {
            try {
                if (shouldRetweet) {
                    service.postCreateRetweet(
                        postCreateRetweetRequest =
                            PostCreateRetweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = statusKey.id,
                                    ),
                            ),
                    )
                } else {
                    service.postDeleteRetweet(
                        postDeleteRetweetRequest =
                            PostDeleteRetweetRequest(
                                variables =
                                    PostDeleteRetweetRequestVariables(
                                        sourceTweetId = statusKey.id,
                                    ),
                            ),
                    )
                }

                updateRetweetStatus(statusKey, shouldRetweet)

                StatusActionResult.success()
            } catch (e: Exception) {
                StatusActionResult.failure(e)
            }
        }

    private suspend fun updateLikeStatus(
        statusKey: MicroBlogKey,
        favorited: Boolean,
    ) {
        updateStatusUseCase<StatusContent.XQT>(
            statusKey = statusKey,
            accountKey = accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            legacy =
                                it.data.legacy?.copy(
                                    favorited = favorited,
                                    favoriteCount =
                                        if (favorited) {
                                            it.data.legacy.favoriteCount
                                                .plus(1)
                                        } else {
                                            it.data.legacy.favoriteCount
                                                .minus(1)
                                        },
                                ),
                        ),
                )
            },
        )
    }

    private suspend fun updateBookmarkStatus(
        statusKey: MicroBlogKey,
        bookmarked: Boolean,
    ) {
        updateStatusUseCase<StatusContent.XQT>(
            statusKey = statusKey,
            accountKey = accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            legacy =
                                it.data.legacy?.copy(
                                    bookmarked = bookmarked,
                                    bookmarkCount =
                                        if (bookmarked) {
                                            (it.data.legacy.bookmarkCount ?: 0) + 1
                                        } else {
                                            maxOf(0, (it.data.legacy.bookmarkCount ?: 1) - 1)
                                        },
                                ),
                        ),
                )
            },
        )
    }

    private suspend fun updateRetweetStatus(
        statusKey: MicroBlogKey,
        retweeted: Boolean,
    ) {
        updateStatusUseCase<StatusContent.XQT>(
            statusKey = statusKey,
            accountKey = accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            legacy =
                                it.data.legacy?.copy(
                                    retweeted = retweeted,
                                    retweetCount =
                                        if (retweeted) {
                                            it.data.legacy.retweetCount
                                                .plus(1)
                                        } else {
                                            it.data.legacy.retweetCount
                                                .minus(1)
                                        },
                                ),
                        ),
                )
            },
        )
    }

    suspend fun follow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation>(
            key = key,
        ) {
            it.copy(
                following = true,
            )
        }
        tryRun {
            service.postCreateFriendships(userId = userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation>(
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
        MemCacheable.updateWith<UiRelation>(
            key = key,
        ) {
            it.copy(
                following = false,
            )
        }
        tryRun {
            service.postDestroyFriendships(userId = userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation>(
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
        MemCacheable.updateWith<UiRelation>(
            key = key,
        ) {
            it.copy(
                muted = true,
            )
        }
        tryRun {
            service.postMutesUsersCreate(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation>(
                key = key,
            ) {
                it.copy(
                    muted = false,
                )
            }
        }
    }

    suspend fun unmute(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation>(
            key = key,
        ) {
            it.copy(
                muted = false,
            )
        }
        tryRun {
            service.postMutesUsersDestroy(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation>(
                key = key,
            ) {
                it.copy(
                    muted = true,
                )
            }
        }
    }

    suspend fun block(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation>(
            key = key,
        ) {
            it.copy(
                blocking = true,
            )
        }
        tryRun {
            service.postBlocksCreate(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation>(
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
        MemCacheable.updateWith<UiRelation>(
            key = key,
        ) {
            it.copy(
                blocking = false,
            )
        }
        tryRun {
            service.postBlocksDestroy(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation>(
                key = key,
            ) {
                it.copy(
                    blocking = true,
                )
            }
        }
    }

    private val notificationMarkerKey: String
        get() = "notificationBadgeCount_$accountKey"

    override fun notificationBadgeCount(): CacheData<Int> =
        MemCacheable(
            key = notificationMarkerKey,
            fetchSource = {
                service.getBadgeCount().ntabUnreadCount?.toInt() ?: 0
            },
        )

    override fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = pagingConfig,
        ) {
            FollowingPagingSource(
                service = service,
                userKey = userKey,
                accountKey = accountKey,
            )
        }.flow.cachedIn(scope)

    override fun fans(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = pagingConfig,
        ) {
            FansPagingSource(
                service = service,
                userKey = userKey,
                accountKey = accountKey,
            )
        }.flow.cachedIn(scope)

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        listOfNotNull(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader = userTimeline(userKey, false),
            ),
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.StatusWithReplies,
                loader =
                    UserRepliesTimelineRemoteMediator(
                        service = service,
                        accountKey = accountKey,
                        database = database,
                        userKey = userKey,
                    ),
            ),
            ProfileTab.Media,
            if (userKey == accountKey) {
                ProfileTab.Timeline(
                    type = ProfileTab.Timeline.Type.Likes,
                    loader =
                        UserLikesTimelineRemoteMediator(
                            service = service,
                            accountKey = accountKey,
                            database = database,
                            userKey = userKey,
                        ),
                )
            } else {
                null
            },
        ).toPersistentList()

    private val listKey: String
        get() = "allLists_$accountKey"

    override fun myList(scope: CoroutineScope): Flow<PagingData<UiList>> =
        memoryPager(
            pageSize = 20,
            pagingKey = listKey,
            scope = scope,
            mediator =
                object : BaseRemoteMediator<Int, UiList>() {
                    var cursor: String? = null

                    override suspend fun doLoad(
                        loadType: LoadType,
                        state: PagingState<Int, UiList>,
                    ): MediatorResult {
                        if (loadType == LoadType.PREPEND) {
                            return MediatorResult.Success(endOfPaginationReached = true)
                        }
                        if (loadType == LoadType.REFRESH) {
                            cursor = null
                        }
                        val response =
                            service
                                .getListsManagementPageTimeline(
                                    variables =
                                        buildString {
                                            append("{\"count\":20")
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

                        cursor = response?.cursor()

                        val result =
                            response
                                ?.list(accountKey = accountKey)
                                .orEmpty()
                                .toImmutableList()

                        if (loadType == LoadType.REFRESH) {
                            MemoryPagingSource.update<UiList>(
                                key = listKey,
                                value = result,
                            )
                        } else if (loadType == LoadType.APPEND) {
                            MemoryPagingSource.append<UiList>(
                                key = listKey,
                                value = result,
                            )
                        }

                        return MediatorResult.Success(
                            endOfPaginationReached = result.isEmpty(),
                        )
                    }
                },
        )

    override suspend fun createList(metaData: ListMetaData) {
        tryRun {
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
        }.onSuccess { response ->
            val data = response.body()?.data?.list
            if (data?.idStr != null) {
                MemoryPagingSource.updateWith<UiList>(
                    key = listKey,
                ) {
                    it
                        .plus(
                            UiList(
                                id = data.idStr,
                                title = metaData.title,
                                description = metaData.description,
                                platformType = PlatformType.Mastodon,
                            ),
                        ).toImmutableList()
                }
            }
        }
    }

    override suspend fun deleteList(listId: String) {
        tryRun {
            service.deleteList(
                request =
                    RemoveListRequest(
                        variables =
                            RemoveListRequest.Variables(
                                listID = listId,
                            ),
                    ),
            )
        }.onSuccess {
            MemoryPagingSource.updateWith<UiList>(
                key = listKey,
            ) {
                it
                    .filter { list -> list.id != listId }
                    .toImmutableList()
            }
        }
    }

    override suspend fun updateList(
        listId: String,
        metaData: ListMetaData,
    ) {
        tryRun {
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
        }.onSuccess {
            MemoryPagingSource.updateWith<UiList>(
                key = listKey,
            ) {
                it
                    .map { list ->
                        if (list.id == listId) {
                            list.copy(
                                title = metaData.title,
                                description = metaData.description,
                            )
                        } else {
                            list
                        }
                    }.toImmutableList()
            }
        }
    }

    override fun listInfo(listId: String): CacheData<UiList> =
        MemCacheable(
            key = "listInfo_$listId",
            fetchSource = {
                getListInfo(listId)
                    ?: throw Exception("List not found")
            },
        )

    private suspend fun getListInfo(listId: String) =
        service
            .getListByRestId(
                variables = "{\"listId\":\"${listId}\"}",
            ).body()
            ?.data
            ?.list
            ?.render(accountKey = accountKey)

    private fun listMemberKey(listId: String) = "listMembers_$listId"

    override fun listMembers(
        listId: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        memoryPager(
            pageSize = pageSize,
            pagingKey = listMemberKey(listId),
            scope = scope,
            mediator =
                object : BaseRemoteMediator<Int, UiUserV2>() {
                    var cursor: String? = null

                    override suspend fun doLoad(
                        loadType: LoadType,
                        state: PagingState<Int, UiUserV2>,
                    ): MediatorResult {
                        if (loadType == LoadType.PREPEND) {
                            return MediatorResult.Success(endOfPaginationReached = true)
                        }
                        if (loadType == LoadType.REFRESH) {
                            cursor = null
                        }
                        val response =
                            service
                                .getListMembers(
                                    variables =
                                        buildString {
                                            append("{\"listId\":\"${listId}\",\"count\":$pageSize")
                                            if (cursor != null) {
                                                append(",\"cursor\":\"${cursor}\"")
                                            }
                                            append("}")
                                        },
                                ).body()
                                ?.data
                                ?.list
                                ?.membersTimeline
                                ?.timeline
                                ?.instructions

                        cursor = response?.cursor()

                        val result =
                            response?.users().orEmpty().map {
                                it.render(accountKey = accountKey)
                            }

                        if (loadType == LoadType.REFRESH) {
                            MemoryPagingSource.update(
                                key = listMemberKey(listId),
                                value = result.toImmutableList(),
                            )
                        } else if (loadType == LoadType.APPEND) {
                            MemoryPagingSource.append(
                                key = listMemberKey(listId),
                                value = result.toImmutableList(),
                            )
                        }

                        return MediatorResult.Success(
                            endOfPaginationReached = result.isEmpty(),
                        )
                    }
                },
        )

    private fun userListsKey(userKey: MicroBlogKey) = "userLists_${userKey.id}"

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        tryRun {
            service.addMember(
                request =
                    AddMemberRequest(
                        variables =
                            AddMemberRequest.Variables(
                                listID = listId,
                                userID = userKey.id,
                            ),
                    ),
            )
            val user =
                service
                    .userById(userKey.id)
                    .body()
                    ?.data
                    ?.user
                    ?.result
                    ?.let {
                        when (it) {
                            is User -> it
                            is UserUnavailable -> null
                        }
                    }?.toDbUser(accountKey)
                    ?.render(accountKey = accountKey) ?: throw Exception("User not found")
            MemoryPagingSource.updateWith(
                key = listMemberKey(listId),
            ) {
                (listOf(user) + it)
                    .distinctBy {
                        it.key
                    }.toImmutableList()
            }
            val list = getListInfo(listId)
            if (list?.id != null) {
                MemCacheable.updateWith<ImmutableList<UiList>>(
                    key = userListsKey(userKey),
                ) {
                    it
                        .plus(list)
                        .toImmutableList()
                }
            }
        }
    }

    override suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        tryRun {
            service.removeMember(
                request =
                    RemoveMemberRequest(
                        variables =
                            RemoveMemberRequest.Variables(
                                listID = listId,
                                userID = userKey.id,
                            ),
                    ),
            )
            MemoryPagingSource.updateWith<UiUserV2>(
                key = listMemberKey(listId),
            ) {
                it
                    .filter { user -> user.key.id != userKey.id }
                    .toImmutableList()
            }
            MemCacheable.updateWith<ImmutableList<UiList>>(
                key = userListsKey(userKey),
            ) {
                it
                    .filter { list -> list.id != listId }
                    .toImmutableList()
            }
        }
    }

    override fun listMemberCache(listId: String): Flow<ImmutableList<UiUserV2>> =
        MemoryPagingSource.getFlow<UiUserV2>(listMemberKey(listId))

    override fun userLists(userKey: MicroBlogKey): MemCacheable<ImmutableList<UiList>> =
        MemCacheable(
            key = userListsKey(userKey),
        ) {
            service
                .getListsMemberships(
                    userId = userKey.id,
                ).body()
                ?.lists
                ?.mapNotNull {
                    it.render(accountKey = accountKey)
                }.orEmpty()
                .toImmutableList()
        }

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf(ListMetaDataType.TITLE, ListMetaDataType.DESCRIPTION)

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            listId,
            service,
            database,
            accountKey,
        )

    override fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> =
        Pager(
            config = pagingConfig,
            remoteMediator =
                DMListRemoteMediator(
                    service = service,
                    accountKey = accountKey,
                    database = database,
                ),
            pagingSourceFactory = {
                database.messageDao().getRoomPagingSource(
                    accountType = AccountType.Specific(accountKey),
                )
            },
        ).flow
            .cachedIn(scope)
            .combine(credentialFlow) { paging, credential ->
                paging.map {
                    it.render(accountKey = accountKey, credential = credential, statusEvent = this)
                }
            }.cachedIn(scope)

    override fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        Pager(
            config = pagingConfig,
            remoteMediator =
                DMConversationRemoteMediator(
                    service = service,
                    accountKey = accountKey,
                    database = database,
                    roomKey = roomKey,
                    clearBadge = ::clearDirectMessageBadgeCount,
                ),
            pagingSourceFactory = {
                database.messageDao().getRoomMessagesPagingSource(
                    roomKey = roomKey,
                )
            },
        ).flow
            .cachedIn(scope)
            .combine(credentialFlow) { paging, credential ->
                paging.map {
                    it.render(accountKey = accountKey, credential = credential, statusEvent = this)
                }
            }.cachedIn(scope)

    override fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        coroutineScope.launch {
            val tempMessage = createSendingDirectMessage(roomKey, message)
            database.messageDao().insertMessages(listOf(tempMessage))
            sendMessage(roomKey, message, tempMessage)
        }
    }

    private suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
        tempMessage: DbMessageItem,
    ) {
        tryRun {
            val response =
                service.getDMConversationTimeline(
                    conversationId = roomKey.id,
                    context = "FETCH_DM_CONVERSATION",
                    maxId = "0",
                )
            service.postDmNew2(
                PostDmNew2Request(
                    conversationId = roomKey.id,
                    requestId = Uuid.random().toString(),
                    text = message,
                ),
            ) to response
        }.onSuccess { (response, conversationResponse) ->
            database.messageDao().deleteMessage(tempMessage.messageKey)
            XQT.saveDM(
                accountKey = accountKey,
                database = database,
                propertyEntries = response.propertyEntries,
                users = response.users,
                conversations = conversationResponse.conversationTimeline?.conversations,
            )
        }.onFailure {
            database.messageDao().insertMessages(
                listOf(
                    tempMessage.copy(
                        content =
                            (tempMessage.content as MessageContent.Local).copy(
                                state = MessageContent.Local.State.FAILED,
                            ),
                    ),
                ),
            )
        }
    }

    override fun retrySendDirectMessage(messageKey: MicroBlogKey) {
        coroutineScope.launch {
            val current = database.messageDao().getMessage(messageKey)
            if (current != null && current.content is MessageContent.Local) {
                database.messageDao().insertMessages(
                    listOf(
                        current.copy(
                            content =
                                current.content.copy(
                                    state = MessageContent.Local.State.SENDING,
                                ),
                        ),
                    ),
                )
                sendMessage(
                    roomKey = current.roomKey,
                    message = current.content.text,
                    tempMessage = current,
                )
            }
        }
    }

    override fun deleteDirectMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        coroutineScope.launch {
            val current = database.messageDao().getMessage(messageKey)
            if (current != null && current.content is MessageContent.Local) {
                database.messageDao().deleteMessage(messageKey)
            } else {
                tryRun {
                    service.postDMMessageDeleteMutation(
                        messageId = messageKey.id,
                        requestId = Uuid.random().toString(),
                    )
                }.onSuccess {
                    database.messageDao().deleteMessage(messageKey)
                }
            }
        }
    }

    override fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> =
        Cacheable(
            fetchSource = {
                val response =
                    service.getDMConversationTimeline(
                        conversationId = roomKey.id,
                        context = "FETCH_DM_CONVERSATION",
                        maxId = "0",
                    )
                XQT.saveDM(
                    accountKey = accountKey,
                    database = database,
                    propertyEntries = response.conversationTimeline?.propertyEntries,
                    users = response.conversationTimeline?.users,
                    conversations = response.conversationTimeline?.conversations,
                )
            },
            cacheSource = {
                database
                    .messageDao()
                    .getRoomInfo(
                        roomKey = roomKey,
                        accountType = AccountType.Specific(accountKey),
                    ).distinctUntilChanged()
                    .combine(
                        credentialFlow,
                    ) { room, credential ->
                        room?.render(
                            accountKey = accountKey,
                            credential = credential,
                            statusEvent = this,
                        )
                    }.mapNotNull { it }
            },
        )

    override suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
        val response = service.getDMConversationTimeline(conversationId = roomKey.id)
        service.postDMConversationMarkRead(
            conversationId = roomKey.id,
            conversationId2 = roomKey.id,
            lastReadEventId = response.conversationTimeline?.maxEntryId.orEmpty(),
        )
        XQT.saveDM(
            accountKey = accountKey,
            database = database,
            propertyEntries = response.conversationTimeline?.propertyEntries,
            users = response.conversationTimeline?.users,
            conversations = response.conversationTimeline?.conversations,
        )
    }

    private fun clearDirectMessageBadgeCount(
        roomKey: MicroBlogKey,
        lastReadId: String,
    ) {
//        coroutineScope.launch {
//            tryRun {
//                service.postDMConversationMarkRead(roomKey.id, roomKey.id, lastReadId)
//            }
//        }
        directMessageBadgeCount.refresh()
    }

    private val dmNotificationMarkerKey: String
        get() = "dm_notificationBadgeCount_$accountKey"
    override val directMessageBadgeCount: CacheData<Int>
        get() =
            MemCacheable(
                key = dmNotificationMarkerKey,
                fetchSource = {
                    service.getBadgeCount().dmUnreadCount?.toInt() ?: 0
                },
            )

    override fun leaveDirectMessage(roomKey: MicroBlogKey) {
        coroutineScope.launch {
            tryRun {
                service.postDMConversationDelete(
                    conversationId = roomKey.id,
                )
            }.onSuccess {
                database.messageDao().deleteRoomTimeline(roomKey, accountType = AccountType.Specific(accountKey))
                database.messageDao().deleteRoom(roomKey)
                database.messageDao().deleteRoomReference(roomKey)
                database.messageDao().deleteRoomMessages(roomKey)
            }
        }
    }

    override fun createDirectMessageRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> =
        flow {
            val accountIdLong =
                accountKey.id.toLongOrNull()
                    ?: throw Exception("Invalid account key")
            val userIdLong =
                userKey.id.toLongOrNull()
                    ?: throw Exception("Invalid user key")
            val roomId =
                listOf(
                    accountIdLong,
                    userIdLong,
                ).sortedBy { it }
                    .joinToString("-")
            tryRun {
                val response =
                    service.getDMConversationTimeline(
                        conversationId = roomId,
                    )
                if (response.conversationTimeline?.propertyEntries.isNullOrEmpty()) {
                    service
                        .postDMWelcomeMessagesAddToConversation(
                            requestId = Uuid.random().toString(),
                            body =
                                AddToConversationRequest(
                                    conversationId = roomId,
                                ),
                        )
                    service.getDMConversationTimeline(
                        conversationId = roomId,
                    )
                } else {
                    response
                }
            }.onSuccess { response ->
                XQT.saveDM(
                    accountKey = accountKey,
                    database = database,
                    propertyEntries = response.conversationTimeline?.propertyEntries,
                    users = response.conversationTimeline?.users,
                    conversations = response.conversationTimeline?.conversations,
                )
            }.fold(
                onSuccess = {
                    emit(
                        UiState.Success(
                            MicroBlogKey(
                                id = roomId,
                                host = accountKey.host,
                            ),
                        ),
                    )
                },
                onFailure = {
                    emit(UiState.Error(it))
                },
            )
        }

    override suspend fun canSendDirectMessage(userKey: MicroBlogKey): Boolean =
        tryRun {
            val canDm =
                service
                    .getDMPermissions(userKey.id)
                    .body()
                    ?.permissions
                    ?.idKeys
                    ?.get(userKey.id)
                    ?.canDm == true
            if (!canDm) {
                throw Exception("Cannot send DM")
            }
        }.isSuccess

    suspend fun podcast(id: String): Result<UiPodcast> =
        tryRun {
            val data =
                service
                    .getAudioSpaceById(
                        variables =
                            """
                            {
                                "id": "$id",
                                "isMetatagsQuery": false,
                                "withReplays": true,
                                "withListeners": true
                            }
                            """.trimIndent(),
                    ).data
                    ?.audioSpace
            val mediaKey = data?.metadata?.mediaKey ?: throw Exception("Media key not found")
            val mediaData =
                runCatching {
                    // when podcast.state == ended , then podcast type is replay, so need return podcast info.
                    // if (data.metadata.state == "Ended") {
                    //     null
                    // } else {
                    service
                        .getLiveVideoStreamStatus(mediaKey = mediaKey)
                        .decodeJson<LiveVideoStreamStatusResponse>()
                        .source
                        ?.noRedirectPlaybackURL
                    // }
                }.getOrNull()
            data.render(
                accountKey = accountKey,
                url = mediaData,
            )
        }

    suspend fun getFleets(): Result<ImmutableList<UiPodcast>> {
        return tryRun {
            val fleet = service.getFleets()
            fleet.threads
                .orEmpty()
                .mapNotNull {
                    val title = it.liveContent?.audiospace?.title
                    val id = it.liveContent?.audiospace?.broadcastID ?: return@mapNotNull null
                    val ended = it.liveContent.audiospace.endedAt != null
                    val creator =
                        it.liveContent.audiospace.creatorTwitterUserID?.let {
                            service
                                .userById(it.toString())
                                .body()
                                ?.data
                                ?.user
                                ?.render(accountKey = accountKey)
                        } ?: return@mapNotNull null
                    val hosts =
                        it.liveContent.audiospace.adminTwitterUserIDS
                            .orEmpty()
                            .mapNotNull { host ->
                                service
                                    .userById(host.toString())
                                    .body()
                                    ?.data
                                    ?.user
                                    ?.render(accountKey = accountKey)
                            }.toImmutableList()
                    val speakers =
                        it.liveContent.audiospace.guests
                            .orEmpty()
                            .mapNotNull { speaker ->
                                service
                                    .userById(speaker.toString())
                                    .body()
                                    ?.data
                                    ?.user
                                    ?.render(accountKey = accountKey)
                            }.toImmutableList()
                    val listeners =
                        it.liveContent.audiospace.listeners
                            .orEmpty()
                            .mapNotNull { listener ->
                                service
                                    .userById(listener.toString())
                                    .body()
                                    ?.data
                                    ?.user
                                    ?.render(accountKey = accountKey)
                            }.toImmutableList()
                    UiPodcast(
                        id = id,
                        title = title ?: "",
                        playbackUrl = null,
                        ended = ended,
                        creator = creator,
                        hosts = hosts,
                        speakers = speakers,
                        listeners = listeners,
                    )
                }.toImmutableList()
        }
    }
}
