package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.createSendingDirectMessage
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.AddToConversationRequest
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequestVariables
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
import dev.dimension.flare.data.network.xqt.model.PostDmNew2Request
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostMediaMetadataCreateRequest
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweetRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
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
    NotificationDataSource,
    UserDataSource,
    PostDataSource,
    KoinComponent,
    ListDataSource,
    DirectMessageDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountRepository: AccountRepository by inject()
    private val inAppNotification: InAppNotification by inject()
    private val imageCompressor: ImageCompressor by inject()
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
    private val loader by lazy {
        XQTLoader(
            accountKey = accountKey,
            service = service,
        )
    }

    private val listLoader = XQTListLoader(service, accountKey)

    private val listMemberLoader = XQTListMemberLoader(service, accountKey)

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }

    override val postHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }

    override val relationHandler by lazy {
        RelationHandler(
            dataSource = loader,
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountKey = accountKey,
            handler = this,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.XQT)
        when (event) {
            is PostEvent.XQT.Retweet -> {
                if (event.retweeted) {
                    service.postDeleteRetweet(
                        postDeleteRetweetRequest =
                            PostDeleteRetweetRequest(
                                variables = PostDeleteRetweetRequestVariables(sourceTweetId = event.postKey.id),
                            ),
                    )
                } else {
                    service.postCreateRetweet(
                        postCreateRetweetRequest =
                            PostCreateRetweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                }
            }

            is PostEvent.XQT.Like -> {
                if (event.liked) {
                    service.postUnfavoriteTweet(
                        postUnfavoriteTweetRequest =
                            PostUnfavoriteTweetRequest(
                                variables = PostCreateRetweetRequestVariables(tweetId = event.postKey.id),
                            ),
                    )
                } else {
                    service.postFavoriteTweet(
                        postFavoriteTweetRequest =
                            PostFavoriteTweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                }
            }

            is PostEvent.XQT.Bookmark -> {
                if (event.bookmarked) {
                    service.postDeleteBookmark(
                        postDeleteBookmarkRequest =
                            DeleteBookmarkRequest(
                                variables =
                                    DeleteBookmarkRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                } else {
                    service.postCreateBookmark(
                        postCreateBookmarkRequest =
                            CreateBookmarkRequest(
                                variables =
                                    CreateBookmarkRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                }
            }
        }
    }

    override val listHandler =
        ListHandler(
            pagingKey = "list_$accountKey",
            accountKey = accountKey,
            loader = listLoader,
        )

    override val listMemberHandler =
        ListMemberHandler(
            pagingKey = "list_member_$accountKey",
            accountKey = accountKey,
            loader = listMemberLoader,
        )

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            service,
            accountKey,
            inAppNotification,
        )

    fun featuredTimeline(
        pageSize: Int = 20,
        pagingKey: String = "featured_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimelineV2>> =
        Pager(
            config = pagingConfig,
        ) {
            featuredTimelineLoader().toPagingSource()
        }.flow.cachedIn(scope)

    fun featuredTimelineLoader() =
        FeaturedTimelineRemoteMediator(
            service,
            accountKey,
        )

    fun bookmarkTimeline(
        pageSize: Int = 20,
        pagingKey: String = "bookmark_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimelineV2>> =
        Pager(
            config = pagingConfig,
        ) {
            bookmarkTimelineLoader().toPagingSource()
        }.flow.cachedIn(scope)

    fun bookmarkTimelineLoader() =
        BookmarkTimelineRemoteMediator(
            service,
            accountKey,
        )

    fun deviceFollowTimelineLoader() =
        DeviceFollowRemoteMediator(
            service,
            accountKey,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        if (type == NotificationFilter.All) {
            NotificationPagingSource(
                locale = "en",
                service = service,
                accountKey = accountKey,
                onClearMarker = {
                    notificationHandler.clear()
                },
            )
        } else {
            MentionRemoteMediator(
                service,
                accountKey,
            )
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All, NotificationFilter.Mention)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = if (mediaOnly) {
        UserMediaTimelineRemoteMediator(
            userKey,
            service,
            accountKey,
        )
    } else {
        UserTimelineRemoteMediator(
            userKey,
            service,
            accountKey,
        )
    }

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey = statusKey,
            service = service,
            accountKey = accountKey,
            statusOnly = false,
        )

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
                    data.referenceStatus.data as? UiTimelineV2.Post
                }?.user
                ?.handle
                ?.removePrefix("@")
                ?.removeSuffix("@${accountKey.host}")
        val maxProgress = data.medias.size + 1
        val mediaIds =
            data.medias.mapIndexed { index, (item, altText) ->
                val bytes = item.readBytes()
                val isImage = item.type == FileType.Image

                val finalBytes =
                    if (isImage) {
                        imageCompressor.compress(
                            imageBytes = bytes,
                            maxSize = 5 * 1024 * 1024,
                            maxDimensions = 4096 to 4096,
                        )
                    } else {
                        bytes
                    }

                uploadMedia(
                    mediaType = getMeidaTypeFromName(item.name),
                    mediaData = finalBytes,
                ).also {
                    if (data.sensitive || !altText.isNullOrEmpty()) {
                        service.postMediaMetadataCreate(
                            body =
                                PostMediaMetadataCreateRequest(
                                    mediaId = it,
                                    sensitiveMediaWarning =
                                        if (data.sensitive) {
                                            listOf(
                                                PostMediaMetadataCreateRequest.SensitiveMediaWarning.Other,
                                            )
                                        } else {
                                            null
                                        },
                                    altText =
                                        if (!altText.isNullOrEmpty()) {
                                            PostMediaMetadataCreateRequest.AltText(altText)
                                        } else {
                                            null
                                        },
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

    override fun searchStatus(query: String) =
        SearchStatusPagingSource(
            service,
            accountKey,
            query,
        )

    override fun searchUser(query: String) =
        SearchUserPagingSource(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun discoverUsers() =
        TrendsUserPagingSource(
            service,
            accountKey,
        )

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> {
        // not supported
        throw UnsupportedOperationException("XQT does not support discover statuses")
    }

    override fun discoverHashtags() =
        TrendHashtagPagingSource(
            service,
        )

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(280),
            media =
                ComposeConfig.Media(
                    maxCount = 4,
                    canSensitive = true,
                    altTextMaxLength = 1000,
                    allowMediaOnly = true,
                ),
        )

    override fun following(userKey: MicroBlogKey) =
        FollowingPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun fans(userKey: MicroBlogKey) =
        FansPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

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
                            userKey = userKey,
                        ),
                )
            } else {
                null
            },
        ).toPersistentList()

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            listId,
            service,
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
                    it.render(accountKey = accountKey, credential = credential)
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
                    it.render(accountKey = accountKey, credential = credential)
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
                database
                    .messageDao()
                    .deleteRoomTimeline(roomKey, accountType = AccountType.Specific(accountKey))
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
