package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import app.bsky.actor.GetProfileQueryParams
import app.bsky.bookmark.CreateBookmarkRequest
import app.bsky.bookmark.DeleteBookmarkRequest
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.unspecced.GetPopularFeedGeneratorsQueryParams
import chat.bsky.convo.DeleteMessageForSelfRequest
import chat.bsky.convo.GetConvoForMembersQueryParams
import chat.bsky.convo.GetConvoQueryParams
import chat.bsky.convo.GetLogQueryParams
import chat.bsky.convo.GetLogResponseLogUnion
import chat.bsky.convo.LeaveConvoRequest
import chat.bsky.convo.ListConvosQueryParams
import chat.bsky.convo.LogCreateMessageMessageUnion
import chat.bsky.convo.LogDeleteMessageMessageUnion
import chat.bsky.convo.MessageInput
import chat.bsky.convo.SendMessageRequest
import chat.bsky.convo.UpdateReadRequest
import com.atproto.moderation.CreateReportRequest
import com.atproto.moderation.CreateReportRequestSubjectUnion
import com.atproto.moderation.Token
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.CreateRecordResponse
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.mapConversationDeletedMessage
import dev.dimension.flare.data.database.cache.mapper.mapConversationMessages
import dev.dimension.flare.data.database.cache.mapper.mapDirectMessages
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.DirectMessageHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.loader.ListLoader
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.network.bluesky.model.DidDoc
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.blueskyLike
import dev.dimension.flare.ui.model.mapper.blueskyReblog
import dev.dimension.flare.ui.model.mapper.bskyJson
import dev.dimension.flare.ui.model.mapper.parseBskyFacets
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Language
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.time.Clock

private const val AT_PROTO_PERSONAL_DATA_SERVER = "AtprotoPersonalDataServer"

@OptIn(ExperimentalPagingApi::class)
internal class BlueskyDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    NotificationDataSource,
    UserDataSource,
    PostDataSource,
    KoinComponent,
    ListDataSource,
    RelationDataSource,
    DirectMessageDataSource,
    PostEventHandler.Handler {
    private val database: CacheDatabase by inject()
    private val appDatabase: AppDatabase by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountRepository: AccountRepository by inject()
    private val imageCompressor: ImageCompressor by inject()
    private val credentialFlow by lazy {
        accountRepository.credentialFlow<UiAccount.Bluesky.Credential>(accountKey)
    }
    private var cachedPdsService: BlueskyService? = null

    private val mutex = Mutex(locked = false)

    private suspend fun pdsService(): BlueskyService =
        mutex.withLock {
            cachedPdsService ?: run {
                val service =
                    BlueskyService(
                        accountKey = accountKey,
                        credentialFlow = credentialFlow,
                        onCredentialRefreshed = { credential ->
                            appDatabase.accountDao().setCredential(
                                accountKey,
                                credential.encodeJson(),
                            )
                        },
                    )
                val didDoc: DidDoc? =
                    service
                        .getSession()
                        .requireResponse()
                        .didDoc
                        ?.decodeAs()
                val entryPoint =
                    didDoc
                        ?.service
                        ?.firstOrNull { it.type == AT_PROTO_PERSONAL_DATA_SERVER }
                        ?.serviceEndpoint
                if (entryPoint.isNullOrEmpty()) {
                    service
                } else {
                    service.newBaseUrlService(entryPoint)
                }.also {
                    cachedPdsService = it
                }
            }
        }

    val loader by lazy {
        BlueskyLoader(
            accountKey = accountKey,
            getService = this::pdsService,
        )
    }

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            host = accountKey.host,
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
            accountType = AccountType.Specific(accountKey),
        )
    }

    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType>
        get() = loader.supportedTypes

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Bluesky)
        when (event) {
            is PostEvent.Bluesky.Bookmark ->
                bookmark(event, updater)

            is PostEvent.Bluesky.Like ->
                like(event, updater)

            is PostEvent.Bluesky.Reblog ->
                reblog(event, updater)
        }
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            getService = this::pdsService,
            accountKey,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        when (type) {
            NotificationFilter.All ->
                NotificationRemoteMediator(
                    getService = this::pdsService,
                    accountKey,
                    onClearMarker = {
                        notificationHandler.clear()
                    },
                )

            else -> notSupported()
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = UserTimelineRemoteMediator(
        getService = this::pdsService,
        accountKey,
        userKey,
        onlyMedia = mediaOnly,
    )

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey,
            getService = this::pdsService,
            accountKey,
            statusOnly = false,
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        val service = pdsService()
        val quoteId =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Quote
                }?.statusKey
                ?.id
        val inReplyToID =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Reply
                }?.statusKey
                ?.id
        val mediaBlob =
            data.medias
                .mapIndexedNotNull { index, (item, altText) ->
                    val bytes = item.readBytes()
                    val isImage = item.type == FileType.Image

                    val finalBytes =
                        if (isImage) {
                            imageCompressor.compress(
                                imageBytes = bytes,
                                maxSize = 1 * 1024 * 1024,
                                maxDimensions = 2000 to 2000,
                            )
                        } else {
                            bytes
                        }
                    service
                        .uploadBlob(finalBytes)
                        .also {
                            progress()
                        }.maybeResponse()
                        ?.let {
                            it.blob to altText
                        }
                }
        val facets =
            parseBskyFacets(
                data.content,
                resolveMentionDid = { userName ->
                    service
                        .getProfile(GetProfileQueryParams(actor = Handle(handle = userName)))
                        .requireResponse()
                        .did.did
                },
            )
        val post =
            Post(
                text = data.content,
                facets = facets,
                createdAt = Clock.System.now(),
                embed =
                    quoteId
                        ?.let {
                            service
                                .getPosts(GetPostsQueryParams(persistentListOf(AtUri(it))))
                                .maybeResponse()
                                ?.posts
                                ?.firstOrNull()
                        }?.let { item ->
                            PostEmbedUnion.Record(
                                Record(
                                    StrongRef(
                                        uri = item.uri,
                                        cid = item.cid,
                                    ),
                                ),
                            )
                        } ?: mediaBlob.takeIf { it.any() }?.let { blobs ->
                        PostEmbedUnion.Images(
                            Images(
                                blobs
                                    .map { blob ->
                                        ImagesImage(image = blob.first, alt = blob.second.orEmpty())
                                    }.toImmutableList(),
                            ),
                        )
                    },
                reply =
                    inReplyToID
                        ?.let {
                            service
                                .getPosts(GetPostsQueryParams(persistentListOf(AtUri(it))))
                                .maybeResponse()
                                ?.posts
                                ?.firstOrNull()
                        }?.let { item ->
                            val post: Post = item.record.decodeAs()
                            val root =
                                post.reply?.root?.let { root ->
                                    StrongRef(
                                        uri = root.uri,
                                        cid = root.cid,
                                    )
                                } ?: StrongRef(
                                    uri = item.uri,
                                    cid = item.cid,
                                )
                            PostReplyRef(
                                parent =
                                    StrongRef(
                                        uri = item.uri,
                                        cid = item.cid,
                                    ),
                                root = root,
                            )
                        },
                langs =
                    data.language.map {
                        Language(it)
                    },
            )
        service
            .createRecord(
                CreateRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.feed.post"),
                    record = post.bskyJson(),
                ),
            ).requireResponse()
    }

    suspend fun report(
        statusKey: MicroBlogKey,
        reason: BlueskyReportStatusState.ReportReason,
    ) {
        tryRun {
            val service = pdsService()
            val post =
                service
                    .getPosts(GetPostsQueryParams(persistentListOf(AtUri(statusKey.id))))
                    .maybeResponse()
                    ?.posts
                    ?.firstOrNull()
            if (post != null) {
                service.createReport(
                    CreateReportRequest(
                        reasonType =
                            when (reason) {
                                BlueskyReportStatusState.ReportReason.Spam -> Token.ReasonSpam
                                BlueskyReportStatusState.ReportReason.Violation -> Token.ReasonViolation
                                BlueskyReportStatusState.ReportReason.Misleading -> Token.ReasonMisleading
                                BlueskyReportStatusState.ReportReason.Sexual -> Token.ReasonSexual
                                BlueskyReportStatusState.ReportReason.Rude -> Token.ReasonRude
                                BlueskyReportStatusState.ReportReason.Other -> Token.ReasonOther
                            },
                        subject =
                            CreateReportRequestSubjectUnion.RepoStrongRef(
                                value =
                                    StrongRef(
                                        uri = post.uri,
                                        cid = post.cid,
                                    ),
                            ),
                    ),
                )
            }
        }
    }

    suspend fun reblog(
        event: PostEvent.Bluesky.Reblog,
        updater: DatabaseUpdater,
    ) {
        val service = pdsService()
        val cid = event.cid
        val uri = event.uri
        val repostUri = event.repostUri
        if (repostUri != null) {
            if (repostUri.isEmpty()) {
                // pending event, do nothing
            } else {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = accountKey.id),
                        collection = Nsid("app.bsky.feed.repost"),
                        rkey = RKey(repostUri.substringAfterLast('/')),
                    ),
                )
            }
        } else {
            val response =
                service
                    .createRecord(
                        CreateRecordRequest(
                            repo = Did(did = accountKey.id),
                            collection = Nsid("app.bsky.feed.repost"),
                            record =
                                app.bsky.feed
                                    .Repost(
                                        subject =
                                            StrongRef(
                                                uri = AtUri(uri),
                                                cid = Cid(cid),
                                            ),
                                        createdAt =
                                            Clock.System
                                                .now(),
                                    ).bskyJson(),
                        ),
                    ).requireResponse()
            updater.updateActionMenu(
                postKey = event.postKey,
                newActionMenu =
                    ActionMenu.blueskyReblog(
                        accountKey = accountKey,
                        postKey = event.postKey,
                        cid = cid,
                        uri = uri,
                        count = event.count + 1,
                        repostUri = response.uri.atUri,
                    ),
            )
        }
    }

    suspend fun like(
        event: PostEvent.Bluesky.Like,
        updater: DatabaseUpdater,
    ) {
        val cid = event.cid
        val uri = event.uri
        val likedUri = event.likedUri
        if (likedUri != null) {
            if (likedUri.isEmpty()) {
                // pending event, do nothing
            } else {
                deleteLikeRecord(likedUri)
            }
        } else {
            val response = createLikeRecord(cid, uri)
            updater.updateActionMenu(
                postKey = event.postKey,
                newActionMenu =
                    ActionMenu.blueskyLike(
                        accountKey = accountKey,
                        postKey = event.postKey,
                        cid = cid,
                        uri = uri,
                        count = event.count + 1,
                        likedUri = response.uri.atUri,
                    ),
            )
        }
    }

    suspend fun bookmark(
        event: PostEvent.Bluesky.Bookmark,
        updater: DatabaseUpdater,
    ) {
        val service = pdsService()
        val cid = event.cid
        val uri = event.uri
        if (event.bookmarked) {
            service
                .deleteBookmark(
                    DeleteBookmarkRequest(
                        uri = AtUri(uri),
                    ),
                ).requireResponse()
        } else {
            service
                .createBookmark(
                    CreateBookmarkRequest(
                        uri = AtUri(uri),
                        cid = Cid(cid),
                    ),
                ).requireResponse()
        }
    }

    private suspend fun createLikeRecord(
        cid: String,
        uri: String,
    ): CreateRecordResponse {
        val service = pdsService()
        val result =
            service
                .createRecord(
                    CreateRecordRequest(
                        repo = Did(did = accountKey.id),
                        collection = Nsid("app.bsky.feed.like"),
                        record =
                            app.bsky.feed
                                .Like(
                                    subject =
                                        StrongRef(
                                            uri = AtUri(uri),
                                            cid = Cid(cid),
                                        ),
                                    createdAt = Clock.System.now(),
                                ).bskyJson(),
                    ),
                ).requireResponse()
        return result
    }

    private suspend fun deleteLikeRecord(likedUri: String) =
        pdsService().deleteRecord(
            DeleteRecordRequest(
                repo = Did(did = accountKey.id),
                collection = Nsid("app.bsky.feed.like"),
                rkey = RKey(likedUri.substringAfterLast('/')),
            ),
        )

    override fun searchStatus(query: String) =
        SearchStatusRemoteMediator(
            getService = this::pdsService,
            accountKey,
            query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        SearchUserPagingSource(
            getService = this::pdsService,
            accountKey,
            query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        TrendsUserPagingSource(
            getService = this::pdsService,
            accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(300),
            media =
                ComposeConfig.Media(
                    maxCount = 4,
                    canSensitive = true,
                    altTextMaxLength = 2000,
                    allowMediaOnly = true,
                ),
            language = ComposeConfig.Language(3),
        )

    private val myFeedsKey = "my_feeds_$accountKey"

    internal val feedLoader by lazy {
        BlueskyFeedLoader(
            getService = this::pdsService,
            accountKey = accountKey,
        )
    }

    val feedHandler by lazy {
        ListHandler(
            pagingKey = myFeedsKey,
            accountKey = accountKey,
            loader = feedLoader,
        )
    }

    fun popularFeeds(
        query: String?,
        scope: CoroutineScope,
    ): Flow<PagingData<Pair<UiList.Feed, Boolean>>> =
        Pager(
            config = pagingConfig,
        ) {
            object : BasePagingSource<String, UiList.Feed>() {
                override fun getRefreshKey(state: PagingState<String, UiList.Feed>): String? = null

                override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiList.Feed> {
                    val result =
                        pdsService()
                            .getPopularFeedGeneratorsUnspecced(
                                GetPopularFeedGeneratorsQueryParams(
                                    limit = params.loadSize.toLong(),
                                    cursor = params.key,
                                    query = query,
                                ),
                            ).maybeResponse()
                    return LoadResult.Page(
                        data =
                            result
                                ?.feeds
                                ?.map {
                                    it.render(accountKey)
                                }.orEmpty(),
                        prevKey = null,
                        nextKey = result?.cursor,
                    )
                }
            }
        }.flow
            .cachedIn(scope)
            .let { feeds ->
                combine(
                    feeds,
                    feedHandler.cacheData,
                ) { popular, my ->
                    popular.map { item ->
                        item to my.any { it.id == item.id }
                    }
                }
            }.cachedIn(scope)

    fun feedTimelineLoader(uri: String) =
        FeedTimelineRemoteMediator(
            getService = this::pdsService,
            accountKey = accountKey,
            uri = uri,
        )

    suspend fun subscribeFeed(data: UiList.Feed) {
        tryRun {
            feedLoader.subscribe(data.id)
            feedHandler.insertToDatabase(data)
        }
    }

    suspend fun unsubscribeFeed(data: UiList.Feed) {
        feedHandler.delete(data.id)
    }

    suspend fun favouriteFeed(data: UiList.Feed) {
        feedHandler.withDatabase { updataCallback ->
            val newData = data.copy(liked = !data.liked)
            updataCallback(newData)
            tryRun {
                if (newData.liked) {
                    feedLoader.favourite(data.id)
                } else {
                    feedLoader.unfavourite(data.id)
                }
            }.onFailure {
                updataCallback(data)
            }
        }
    }

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            getService = this::pdsService,
            accountKey = accountKey,
            uri = listId,
        )

    private val myListKey = "my_list_$accountKey"

    val listLoader: ListLoader by lazy {
        BlueskyListLoader(
            getService = this::pdsService,
            accountKey = accountKey,
        )
    }

    val listMemberLoader: ListMemberLoader by lazy {
        BlueskyListMemberLoader(
            getService = this::pdsService,
            accountKey = accountKey,
        )
    }

    override val listHandler: ListHandler by lazy {
        ListHandler(
            pagingKey = myListKey,
            accountKey = accountKey,
            loader = listLoader,
        )
    }

    override val listMemberHandler: ListMemberHandler by lazy {
        ListMemberHandler(
            pagingKey = "list_members_$accountKey",
            accountKey = accountKey,
            loader = listMemberLoader,
        )
    }

    private val directMessageLoader: DirectMessageLoader by lazy {
        object : DirectMessageLoader {
            override suspend fun sendMessage(
                roomKey: MicroBlogKey,
                message: String,
            ) {
                pdsService().sendMessage(
                    request =
                        SendMessageRequest(
                            convoId = roomKey.id,
                            message = MessageInput(message),
                        ),
                )
            }

            override suspend fun deleteMessage(
                roomKey: MicroBlogKey,
                messageKey: MicroBlogKey,
            ) {
                pdsService().deleteMessageForSelf(
                    request =
                        DeleteMessageForSelfRequest(
                            convoId = roomKey.id,
                            messageId = messageKey.id,
                        ),
                )
            }

            override suspend fun leaveConversation(roomKey: MicroBlogKey) {
                pdsService().leaveConvo(
                    request =
                        LeaveConvoRequest(
                            convoId = roomKey.id,
                        ),
                )
            }

            override suspend fun createRoom(userKey: MicroBlogKey): MicroBlogKey {
                val response =
                    pdsService()
                        .getConvoForMembers(
                            params =
                                GetConvoForMembersQueryParams(
                                    members = persistentListOf(Did(did = userKey.id)),
                                ),
                        ).requireResponse()
                return MicroBlogKey(id = response.convo.id, host = accountKey.host)
            }

            override suspend fun canSendMessage(userKey: MicroBlogKey): Boolean {
                pdsService()
                    .getConvoForMembers(
                        params =
                            GetConvoForMembersQueryParams(
                                members = persistentListOf(Did(did = userKey.id)),
                            ),
                    ).requireResponse()
                return true
            }

            override suspend fun fetchBadgeCount(): Int {
                val response =
                    pdsService()
                        .listConvos(
                            params = ListConvosQueryParams(),
                        ).requireResponse()
                return response.convos.sumOf { it.unreadCount.toInt() }
            }

            override suspend fun loadRoomList(
                pageSize: Int,
                cursor: String?,
            ): dev.dimension.flare.data.datasource.microblog.paging.PagingResult<UiDMRoom> {
                val response =
                    pdsService()
                        .listConvos(
                            params =
                                ListConvosQueryParams(
                                    limit = pageSize.toLong(),
                                    cursor = cursor,
                                ),
                        ).requireResponse()
                return dev.dimension.flare.data.datasource.microblog.paging.PagingResult(
                    data =
                        mapDirectMessages(
                            accountKey = accountKey,
                            data = response.convos,
                        ).timeline.map { it.content },
                    nextKey = response.cursor,
                    endOfPaginationReached = response.cursor == null,
                )
            }

            override suspend fun loadConversation(
                roomKey: MicroBlogKey,
                pageSize: Int,
                cursor: String?,
            ): dev.dimension.flare.data.datasource.microblog.paging.PagingResult<UiDMItem> {
                val service = pdsService()
                if (cursor == null) {
                    service.updateRead(
                        request =
                            UpdateReadRequest(
                                convoId = roomKey.id,
                            ),
                    )
                    clearDirectMessageBadgeCount(roomKey)
                }
                val response =
                    service
                        .getMessages(
                            params =
                                chat.bsky.convo.GetMessagesQueryParams(
                                    convoId = roomKey.id,
                                    limit = pageSize.toLong(),
                                    cursor = cursor,
                                ),
                        ).requireResponse()
                return dev.dimension.flare.data.datasource.microblog.paging.PagingResult(
                    data =
                        mapConversationMessages(
                            accountKey = accountKey,
                            roomKey = roomKey,
                            data =
                                response.messages.filterIsInstance<chat.bsky.convo.GetMessagesResponseMessageUnion.MessageView>().map {
                                    it.value
                                },
                        ).map { it.content },
                    nextKey = response.cursor,
                    endOfPaginationReached = response.cursor == null,
                )
            }

            override suspend fun loadConversationInfo(roomKey: MicroBlogKey): UiDMRoom {
                val response =
                    pdsService()
                        .getConvo(params = GetConvoQueryParams(convoId = roomKey.id))
                        .requireResponse()
                return mapDirectMessages(
                    accountKey = accountKey,
                    data = listOf(response.convo),
                ).timeline.first().content
            }

            override suspend fun fetchNewMessages(roomKey: MicroBlogKey): List<UiDMItem> {
                val response =
                    pdsService()
                        .getLog(
                            params =
                                GetLogQueryParams(
                                    cursor = null,
                                ),
                        ).requireResponse()
                pdsService().updateRead(
                    request =
                        UpdateReadRequest(
                            convoId = roomKey.id,
                        ),
                )
                clearDirectMessageBadgeCount(roomKey)
                return response.logs.mapNotNull {
                    when (it) {
                        is GetLogResponseLogUnion.CreateMessage ->
                            when (val message = it.value.message) {
                                is LogCreateMessageMessageUnion.MessageView ->
                                    mapConversationMessages(
                                        accountKey = accountKey,
                                        roomKey = roomKey,
                                        data = listOf(message.value),
                                    ).first().content

                                is LogCreateMessageMessageUnion.DeletedMessageView ->
                                    mapConversationDeletedMessage(
                                        accountKey = accountKey,
                                        roomKey = roomKey,
                                        data = message.value,
                                    ).content

                                is LogCreateMessageMessageUnion.Unknown -> null
                            }

                        is GetLogResponseLogUnion.DeleteMessage ->
                            when (val message = it.value.message) {
                                is LogDeleteMessageMessageUnion.MessageView ->
                                    mapConversationMessages(
                                        accountKey = accountKey,
                                        roomKey = roomKey,
                                        data = listOf(message.value),
                                    ).first().content

                                is LogDeleteMessageMessageUnion.DeletedMessageView ->
                                    mapConversationDeletedMessage(
                                        accountKey = accountKey,
                                        roomKey = roomKey,
                                        data = message.value,
                                    ).content

                                is LogDeleteMessageMessageUnion.Unknown -> null
                            }

                        else -> null
                    }
                }
            }
        }
    }

    override val directMessageHandler: DirectMessageHandler by lazy {
        DirectMessageHandler(
            accountKey = accountKey,
            loader = directMessageLoader,
        )
    }

    override fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> = directMessageHandler.directMessageList(scope)

    override fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> = directMessageHandler.directMessageConversation(roomKey, scope)

    override fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> =
        directMessageHandler.getDirectMessageConversationInfo(roomKey)

    override suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
        directMessageHandler.fetchNewDirectMessageForConversation(roomKey)
    }

    private fun clearDirectMessageBadgeCount(roomKey: MicroBlogKey) {
        coroutineScope.launch {
            database
                .messageDao()
                .clearUnreadCount(roomKey, accountType = AccountType.Specific(accountKey))
        }
    }

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        FollowingPagingSource(
            getService = this::pdsService,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        FansPagingSource(
            getService = this::pdsService,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        listOfNotNull(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader =
                    UserTimelineRemoteMediator(
                        getService = this::pdsService,
                        accountKey = accountKey,
                        userKey = userKey,
                        onlyMedia = false,
                        withReplies = false,
                    ),
            ),
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.StatusWithReplies,
                loader =
                    UserTimelineRemoteMediator(
                        getService = this::pdsService,
                        accountKey,
                        userKey,
                        withReplies = true,
                    ),
            ),
            ProfileTab.Media,
            if (userKey == accountKey) {
                ProfileTab.Timeline(
                    type = ProfileTab.Timeline.Type.Likes,
                    loader =
                        UserLikesTimelineRemoteMediator(
                            getService = this::pdsService,
                            accountKey,
                        ),
                )
            } else {
                null
            },
        ).toPersistentList()

    fun bookmarkTimeline(): RemoteLoader<UiTimelineV2> =
        BookmarkTimelineRemoteMediator(
            getService = this::pdsService,
            accountKey = accountKey,
        )
}

internal inline fun <reified T : Any> T.bskyJson(): JsonContent = bskyJson.encodeAsJsonContent(this)
