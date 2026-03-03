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
import app.bsky.feed.ViewerState
import app.bsky.unspecced.GetPopularFeedGeneratorsQueryParams
import chat.bsky.convo.DeleteMessageForSelfRequest
import chat.bsky.convo.DeletedMessageView
import chat.bsky.convo.GetConvoForMembersQueryParams
import chat.bsky.convo.GetConvoQueryParams
import chat.bsky.convo.GetLogQueryParams
import chat.bsky.convo.GetLogResponseLogUnion
import chat.bsky.convo.LeaveConvoRequest
import chat.bsky.convo.ListConvosQueryParams
import chat.bsky.convo.LogCreateMessageMessageUnion
import chat.bsky.convo.LogDeleteMessageMessageUnion
import chat.bsky.convo.MessageInput
import chat.bsky.convo.MessageView
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
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
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
import dev.dimension.flare.data.datasource.microblog.loader.ListLoader
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.network.bluesky.model.DidDoc
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
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
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
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
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountRepository: AccountRepository by inject()
    private val imageCompressor: ImageCompressor by inject()
    private val credentialFlow by lazy {
        accountRepository.credentialFlow<UiAccount.Bluesky.Credential>(accountKey)
    }
    private val service by lazy {
        BlueskyService(
            accountKey = accountKey,
            credentialFlow = credentialFlow,
            onCredentialRefreshed = { credential ->
                coroutineScope.launch {
                    appDatabase.accountDao().setCredential(
                        accountKey,
                        credential.encodeJson(),
                    )
                }
            },
        )
    }

    private var cachedEndpoint: String? = null

    private suspend fun pdsService(): BlueskyService {
        if (cachedEndpoint == null) {
            val didDoc: DidDoc? =
                service
                    .getSession()
                    .requireResponse()
                    .didDoc
                    ?.decodeAs()
            val entryPoint = didDoc?.service?.firstOrNull()?.serviceEndpoint
            cachedEndpoint = entryPoint
        }
        return cachedEndpoint?.let {
            service.newBaseUrlService(it)
        } ?: service
    }

    val loader by lazy {
        BlueskyLoader(
            accountKey = accountKey,
            service = service,
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
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            service,
            accountKey,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        when (type) {
            NotificationFilter.All ->
                NotificationRemoteMediator(
                    service,
                    accountKey,
                    onClearMarker = {
                        notificationHandler.clear()
                    },
                )

            else -> throw IllegalArgumentException("Unsupported notification filter")
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = UserTimelineRemoteMediator(
        service,
        accountKey,
        userKey,
        onlyMedia = mediaOnly,
    )

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey,
            service,
            accountKey,
            database,
            statusOnly = false,
        )

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
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
        val maxProgress = data.medias.size + 1
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
                            progress(ComposeProgress(index + 1, maxProgress))
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
                    repo = Did(did = data.account.accountKey.id),
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

    override fun reblog(
        statusKey: MicroBlogKey,
        cid: String,
        uri: String,
        repostUri: String?,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Bluesky>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
            ) { content ->
                val newUri =
                    if (repostUri != null) {
                        null
                    } else {
                        AtUri("")
                    }
                val count =
                    if (repostUri != null) {
                        (content.data.repostCount ?: 0) - 1
                    } else {
                        (content.data.repostCount ?: 0) + 1
                    }.coerceAtLeast(0)
                content.copy(
                    data =
                        content.data.copy(
                            viewer =
                                content.data.viewer?.copy(
                                    repost = newUri,
                                ) ?: ViewerState(
                                    repost = newUri,
                                ),
                            repostCount = count,
                        ),
                )
            }
            tryRun {
                if (repostUri != null) {
                    service.deleteRecord(
                        DeleteRecordRequest(
                            repo = Did(did = accountKey.id),
                            collection = Nsid("app.bsky.feed.repost"),
                            rkey = RKey(repostUri.substringAfterLast('/')),
                        ),
                    )
                } else {
                    val result =
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
                    updateStatusUseCase<StatusContent.Bluesky>(
                        statusKey = statusKey,
                        accountKey = accountKey,
                        cacheDatabase = database,
                    ) { content ->
                        content.copy(
                            data =
                                content.data.copy(
                                    viewer =
                                        content.data.viewer?.copy(
                                            repost = AtUri(result.uri.atUri),
                                        ) ?: ViewerState(
                                            repost = AtUri(result.uri.atUri),
                                        ),
                                ),
                        )
                    }
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Bluesky>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                ) { content ->
                    val count =
                        if (repostUri != null) {
                            (content.data.repostCount ?: 0) + 1
                        } else {
                            (content.data.repostCount ?: 0) - 1
                        }.coerceAtLeast(0)
                    content.copy(
                        data =
                            content.data.copy(
                                viewer =
                                    content.data.viewer?.copy(
                                        repost = repostUri?.let { it1 -> AtUri(it1) },
                                    ) ?: ViewerState(
                                        repost = repostUri?.let { it1 -> AtUri(it1) },
                                    ),
                                repostCount = count,
                            ),
                    )
                }
            }
        }
    }

    override fun like(
        statusKey: MicroBlogKey,
        cid: String,
        uri: String,
        likedUri: String?,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Bluesky>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
            ) { content ->
                val newUri =
                    if (likedUri != null) {
                        null
                    } else {
                        AtUri("")
                    }
                val count =
                    if (likedUri != null) {
                        (content.data.likeCount ?: 0) - 1
                    } else {
                        (content.data.likeCount ?: 0) + 1
                    }.coerceAtLeast(0)
                content.copy(
                    data =
                        content.data.copy(
                            viewer =
                                content.data.viewer?.copy(
                                    like = newUri,
                                ) ?: ViewerState(
                                    like = newUri,
                                ),
                            likeCount = count,
                        ),
                )
            }
            tryRun {
                if (likedUri != null) {
                    deleteLikeRecord(likedUri)
                } else {
                    val result =
                        createLikeRecord(cid, uri)
                    updateStatusUseCase<StatusContent.Bluesky>(
                        statusKey = statusKey,
                        accountKey = accountKey,
                        cacheDatabase = database,
                    ) { content ->
                        content.copy(
                            data =
                                content.data.copy(
                                    viewer =
                                        content.data.viewer?.copy(
                                            like = AtUri(result.uri.atUri),
                                        ) ?: ViewerState(
                                            like = AtUri(result.uri.atUri),
                                        ),
                                ),
                        )
                    }
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Bluesky>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                ) { content ->
                    val count =
                        if (likedUri != null) {
                            (content.data.likeCount ?: 0) + 1
                        } else {
                            (content.data.likeCount ?: 0) - 1
                        }.coerceAtLeast(0)
                    content.copy(
                        data =
                            content.data.copy(
                                viewer =
                                    content.data.viewer?.copy(
                                        like = likedUri?.let { it1 -> AtUri(it1) },
                                    ) ?: ViewerState(
                                        like = likedUri?.let { it1 -> AtUri(it1) },
                                    ),
                                likeCount = count,
                            ),
                    )
                }
            }
        }
    }

    private suspend fun createLikeRecord(
        cid: String,
        uri: String,
    ): CreateRecordResponse {
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
        service.deleteRecord(
            DeleteRecordRequest(
                repo = Did(did = accountKey.id),
                collection = Nsid("app.bsky.feed.like"),
                rkey = RKey(likedUri.substringAfterLast('/')),
            ),
        )

    override fun searchStatus(query: String) =
        SearchStatusRemoteMediator(
            service,
            accountKey,
            query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        SearchUserPagingSource(
            service,
            accountKey,
            query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        TrendsUserPagingSource(
            service,
            accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        throw UnsupportedOperationException("Bluesky does not support discover hashtags")

    override fun discoverStatuses() = throw UnsupportedOperationException("Bluesky does not support discover statuses")

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
            service = service,
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
                        service
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
            service = service,
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
            service = service,
            accountKey = accountKey,
            uri = listId,
        )

    private val myListKey = "my_list_$accountKey"

    val listLoader: ListLoader by lazy {
        BlueskyListLoader(
            service = service,
            accountKey = accountKey,
        )
    }

    val listMemberLoader: ListMemberLoader by lazy {
        BlueskyListMemberLoader(
            service = service,
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

    private val notificationMarkerKey: String
        get() = "notificationBadgeCount_$accountKey"

    override fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> =
        Pager(
            config = pagingConfig,
            remoteMediator =
                DMListRemoteMediator(
                    getService = this::pdsService,
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
                    getService = this::pdsService,
                    accountKey = accountKey,
                    database = database,
                    roomKey = roomKey,
                    clearBadge = this::clearDirectMessageBadgeCount,
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
                    it.render(
                        accountKey = accountKey,
                        credential = credential,
                        statusEvent = this,
                    )
                }
            }.cachedIn(scope)

    override fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> =
        Cacheable(
            fetchSource = {
                val response =
                    pdsService()
                        .getConvo(params = GetConvoQueryParams(convoId = roomKey.id))
                        .requireResponse()
                Bluesky.saveDM(
                    accountKey = accountKey,
                    database = database,
                    data = listOf(response.convo),
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

    override fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        coroutineScope.launch {
            val tempMessage = createSendingDirectMessage(roomKey, message)
            database.messageDao().insertMessages(listOf(tempMessage))
            tryRun {
                pdsService().sendMessage(
                    request =
                        SendMessageRequest(
                            convoId = roomKey.id,
                            message = MessageInput(message),
                        ),
                )
            }.onSuccess {
                database.messageDao().deleteMessage(tempMessage.messageKey)
                Bluesky.saveMessage(
                    accountKey = accountKey,
                    database = database,
                    roomKey = roomKey,
                    data = listOf(it.requireResponse()),
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
                    pdsService().deleteMessageForSelf(
                        request =
                            DeleteMessageForSelfRequest(
                                convoId = roomKey.id,
                                messageId = messageKey.id,
                            ),
                    )
                }.onSuccess {
                    database.messageDao().deleteMessage(messageKey)
                }
            }
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

                tryRun {
                    pdsService().sendMessage(
                        request =
                            SendMessageRequest(
                                convoId = current.roomKey.id,
                                message = MessageInput(current.content.text),
                            ),
                    )
                }.onSuccess {
                    database.messageDao().deleteMessage(current.messageKey)
                    Bluesky.saveMessage(
                        accountKey = accountKey,
                        database = database,
                        roomKey = current.roomKey,
                        data = listOf(it.requireResponse()),
                    )
                }.onFailure {
                    database.messageDao().insertMessages(
                        listOf(
                            current.copy(
                                content =
                                    current.content.copy(
                                        state = MessageContent.Local.State.FAILED,
                                    ),
                            ),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
        val content = database.messageDao().getLatestMessage(roomKey)?.content
        val cursor =
            if (content is MessageContent.Bluesky.Message) {
                content.data.rev
            } else {
                null
            }
        val response =
            pdsService().getLog(
                params =
                    GetLogQueryParams(
                        cursor = cursor,
                    ),
            )
        pdsService().updateRead(
            request =
                UpdateReadRequest(
                    convoId = roomKey.id,
                ),
        )
        response.requireResponse().logs.forEach {
            when (it) {
                is GetLogResponseLogUnion.CreateMessage -> {
                    when (val message = it.value.message) {
                        is LogCreateMessageMessageUnion.MessageView ->
                            handleMessage(roomKey = roomKey, message = message.value)

                        is LogCreateMessageMessageUnion.DeletedMessageView ->
                            handleMessage(roomKey = roomKey, message = message.value)

                        is LogCreateMessageMessageUnion.Unknown -> Unit
                    }
                }

                is GetLogResponseLogUnion.DeleteMessage -> {
                    when (val message = it.value.message) {
                        is LogDeleteMessageMessageUnion.MessageView ->
                            handleMessage(roomKey = roomKey, message = message.value)

                        is LogDeleteMessageMessageUnion.DeletedMessageView ->
                            handleMessage(roomKey = roomKey, message = message.value)

                        is LogDeleteMessageMessageUnion.Unknown -> Unit
                    }
                }

                else -> Unit
            }
        }
    }

    private suspend fun handleMessage(
        roomKey: MicroBlogKey,
        message: MessageView,
    ) {
        Bluesky.saveMessage(
            accountKey = accountKey,
            roomKey = roomKey,
            database = database,
            data = listOf(message),
        )
    }

    private suspend fun handleMessage(
        roomKey: MicroBlogKey,
        message: DeletedMessageView,
    ) {
        database.messageDao().deleteMessage(
            MicroBlogKey(
                id = message.id,
                host = accountKey.host,
            ),
        )
    }

    override val directMessageBadgeCount: CacheData<Int> =
        Cacheable(
            fetchSource = {
                val response =
                    pdsService()
                        .listConvos(
                            params = ListConvosQueryParams(),
                        ).requireResponse()
                Bluesky.saveDM(
                    accountKey = accountKey,
                    database = database,
                    data = response.convos,
                )
            },
            cacheSource = {
                database
                    .messageDao()
                    .getRoomTimeline(accountType = AccountType.Specific(accountKey))
                    .distinctUntilChanged()
                    .map {
                        it.sumOf { it.timeline.unreadCount.toInt() }
                    }
            },
        )

    private fun clearDirectMessageBadgeCount(roomKey: MicroBlogKey) {
        coroutineScope.launch {
            database
                .messageDao()
                .clearUnreadCount(roomKey, accountType = AccountType.Specific(accountKey))
        }
    }

    override fun leaveDirectMessage(roomKey: MicroBlogKey) {
        coroutineScope.launch {
            tryRun {
                pdsService().leaveConvo(
                    request =
                        LeaveConvoRequest(
                            convoId = roomKey.id,
                        ),
                )
            }.onSuccess {
                database.messageDao().deleteRoomTimeline(roomKey, AccountType.Specific(accountKey))
                database.messageDao().deleteRoom(roomKey)
                database.messageDao().deleteRoomReference(roomKey)
                database.messageDao().deleteRoomMessages(roomKey)
            }
        }
    }

    override fun createDirectMessageRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> =
        flow {
            tryRun {
                pdsService()
                    .getConvoForMembers(
                        params =
                            GetConvoForMembersQueryParams(
                                members = persistentListOf(Did(did = userKey.id)),
                            ),
                    ).requireResponse()
            }.onSuccess {
                Bluesky.saveDM(
                    accountKey = accountKey,
                    database = database,
                    data = listOf(it.convo),
                )
            }.fold(
                onSuccess = {
                    emit(UiState.Success(MicroBlogKey(id = it.convo.id, host = accountKey.host)))
                },
                onFailure = {
                    emit(UiState.Error(it))
                },
            )
        }

    override suspend fun canSendDirectMessage(userKey: MicroBlogKey): Boolean =
        tryRun {
            pdsService()
                .getConvoForMembers(
                    params =
                        GetConvoForMembersQueryParams(
                            members = persistentListOf(Did(did = userKey.id)),
                        ),
                ).requireResponse()
        }.isSuccess

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        FollowingPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        FansPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        listOfNotNull(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader =
                    UserTimelineRemoteMediator(
                        service = service,
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
                        service,
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
                            service,
                            accountKey,
                        ),
                )
            } else {
                null
            },
        ).toPersistentList()

    fun bookmarkTimeline(): RemoteLoader<UiTimelineV2> =
        BookmarkTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun bookmark(
        statusKey: MicroBlogKey,
        uri: String,
        cid: String,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Bluesky>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
            ) { content ->
                content.copy(
                    data =
                        content.data.copy(
                            viewer = content.data.viewer?.copy(bookmarked = true),
                        ),
                )
            }
            tryRun {
                service
                    .createBookmark(
                        CreateBookmarkRequest(
                            uri = AtUri(uri),
                            cid = Cid(cid),
                        ),
                    ).requireResponse()
            }.onFailure {
                it.printStackTrace()
                // rollback
                updateStatusUseCase<StatusContent.Bluesky>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                ) { content ->
                    content.copy(
                        data =
                            content.data.copy(
                                viewer = content.data.viewer?.copy(bookmarked = false),
                            ),
                    )
                }
            }
        }
    }

    override fun unbookmark(
        statusKey: MicroBlogKey,
        uri: String,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Bluesky>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
            ) { content ->
                content.copy(
                    data =
                        content.data.copy(
                            viewer = content.data.viewer?.copy(bookmarked = false),
                        ),
                )
            }
            tryRun {
                service
                    .deleteBookmark(
                        DeleteBookmarkRequest(
                            uri = AtUri(uri),
                        ),
                    ).requireResponse()
            }.onFailure {
                it.printStackTrace()
                // rollback
                updateStatusUseCase<StatusContent.Bluesky>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                ) { content ->
                    content.copy(
                        data =
                            content.data.copy(
                                viewer = content.data.viewer?.copy(bookmarked = true),
                            ),
                    )
                }
            }
        }
    }
}

internal inline fun <reified T : Any> T.bskyJson(): JsonContent = bskyJson.encodeAsJsonContent(this)
