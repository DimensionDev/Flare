package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.PreferencesUnion
import app.bsky.actor.PutPreferencesRequest
import app.bsky.actor.SavedFeed
import app.bsky.actor.Type
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.feed.GetFeedGeneratorsQueryParams
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.feed.ViewerState
import app.bsky.graph.GetListQueryParams
import app.bsky.graph.GetListsQueryParams
import app.bsky.graph.Listitem
import app.bsky.graph.MuteActorRequest
import app.bsky.graph.UnmuteActorRequest
import app.bsky.notification.ListNotificationsQueryParams
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
import com.atproto.repo.ApplyWritesDelete
import com.atproto.repo.ApplyWritesRequest
import com.atproto.repo.ApplyWritesRequestWriteUnion
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.CreateRecordResponse
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.ListRecordsQueryParams
import com.atproto.repo.ListRecordsRecord
import com.atproto.repo.PutRecordRequest
import com.atproto.repo.StrongRef
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.mapper.toDbUser
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
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.createSendingDirectMessage
import dev.dimension.flare.data.datasource.microblog.memoryPager
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.network.bluesky.model.DidDoc
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
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.bskyJson
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.toUi
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
import kotlinx.datetime.toDeprecatedInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal class BlueskyDataSource(
    override val accountKey: MicroBlogKey,
    val credential: UiAccount.Bluesky.Credential,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    StatusEvent.Bluesky,
    ListDataSource,
    DirectMessageDataSource {
    private val database: CacheDatabase by inject()
    private val appDatabase: AppDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountRepository: AccountRepository by inject()
    private val inAppNotification: InAppNotification by inject()
    private val service by lazy {
        BlueskyService(
            baseUrl = credential.baseUrl,
            accountKey = accountKey,
            accessToken = credential.accessToken,
            refreshToken = credential.refreshToken,
            onTokenRefreshed = { accessToken, refreshToken ->
                coroutineScope.launch {
                    appDatabase.accountDao().setCredential(
                        accountKey,
                        credential
                            .copy(
                                accessToken = accessToken,
                                refreshToken = refreshToken,
                            ).encodeJson(),
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
            service.copy(baseUrl = it)
        } ?: service
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            service,
            accountKey,
            database,
            inAppNotification = inAppNotification,
        )

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forNotification = true),
            accountRepository = accountRepository,
            mediator =
                when (type) {
                    NotificationFilter.All ->
                        NotificationRemoteMediator(
                            service,
                            accountKey,
                            database,
                            onClearMarker = {
                                MemCacheable.update(notificationMarkerKey, 0)
                            },
                        )

                    else -> throw IllegalArgumentException("Unsupported notification filter")
                },
        )

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All)

    override fun userByAcct(acct: String): CacheData<UiUserV2> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    service
                        .getProfile(GetProfileQueryParams(actor = Handle(handle = name)))
                        .requireResponse()
                        .toDbUser(accountKey.host)
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByHandleAndHost(name, host, PlatformType.Bluesky)
                    .distinctUntilChanged()
                    .mapNotNull { it?.render(accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiProfile> =
        Cacheable(
            fetchSource = {
                val user =
                    service
                        .getProfile(GetProfileQueryParams(actor = Did(did = id)))
                        .requireResponse()
                        .toDbUser(accountKey.host)
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByKey(MicroBlogKey(id, accountKey.host))
                    .distinctUntilChanged()
                    .mapNotNull { it?.render(accountKey) }
            },
        )

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> =
        MemCacheable(
            relationKeyWithUserKey(userKey),
        ) {
            val user =
                service
                    .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                    .requireResponse()
            UiRelation(
                following = user.viewer?.following?.atUri != null,
                isFans = user.viewer?.followedBy?.atUri != null,
                blocking = user.viewer?.blockedBy ?: false,
                muted = user.viewer?.muted ?: false,
            )
        }.toUi()

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = UserTimelineRemoteMediator(
        service,
        accountKey,
        database,
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

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"

        return Cacheable(
            fetchSource = {
                val result =
                    service
                        .getPosts(
                            GetPostsQueryParams(
                                persistentListOf(AtUri(statusKey.id)),
                            ),
                        ).requireResponse()
                        .posts
                        .firstOrNull()
                        .let {
                            listOfNotNull(it)
                        }
                Bluesky.savePost(
                    accountKey,
                    pagingKey,
                    database,
                    result,
                )
            },
            cacheSource = {
                database
                    .statusDao()
                    .get(statusKey, accountType = AccountType.Specific(accountKey))
                    .mapNotNull { it?.content?.render(this) }
            },
        )
    }

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
                .mapIndexedNotNull { index, item ->
                    service
                        .uploadBlob(item.readBytes())
                        .also {
                            progress(ComposeProgress(index + 1, maxProgress))
                        }.maybeResponse()
                }.map {
                    it.blob
                }
        val post =
            Post(
                text = data.content,
                createdAt = Clock.System.now().toDeprecatedInstant(),
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
                                        ImagesImage(image = blob, alt = "")
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
            )
        service.createRecord(
            CreateRecordRequest(
                repo = Did(did = data.account.accountKey.id),
                collection = Nsid("app.bsky.feed.post"),
                record = post.bskyJson(),
            ),
        )
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
                                                createdAt = Clock.System.now().toDeprecatedInstant(),
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
                                    createdAt = Clock.System.now().toDeprecatedInstant(),
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

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        tryRun {
            service.deleteRecord(
                DeleteRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.feed.post"),
                    rkey = RKey(statusKey.id.substringAfterLast('/')),
                ),
            )
            // delete status from cache
            database.statusDao().delete(
                statusKey = statusKey,
                accountType = AccountType.Specific(accountKey),
            )
            database.pagingTimelineDao().deleteStatus(
                accountKey = accountKey,
                statusKey = statusKey,
            )
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
            val user =
                service
                    .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                    .requireResponse()

            val followRepo = user.viewer?.following?.atUri
            if (followRepo != null) {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = accountKey.id),
                        collection = Nsid("app.bsky.graph.follow"),
                        rkey = RKey(followRepo.substringAfterLast('/')),
                    ),
                )
            }
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
            service.createRecord(
                CreateRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.graph.follow"),
                    record =
                        app.bsky.graph
                            .Follow(
                                subject = Did(userKey.id),
                                createdAt = Clock.System.now().toDeprecatedInstant(),
                            ).bskyJson(),
                ),
            )
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
            service.createRecord(
                CreateRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.graph.block"),
                    record =
                        app.bsky.graph
                            .Block(
                                subject = Did(userKey.id),
                                createdAt = Clock.System.now().toDeprecatedInstant(),
                            ).bskyJson(),
                ),
            )
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
            val user =
                service
                    .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                    .requireResponse()

            val blockRepo = user.viewer?.blocking?.atUri
            if (blockRepo != null) {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = accountKey.id),
                        collection = Nsid("app.bsky.graph.block"),
                        rkey = RKey(blockRepo.substringAfterLast('/')),
                    ),
                )
            }
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
            service.muteActor(MuteActorRequest(actor = Did(did = userKey.id)))
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
            service.unmuteActor(UnmuteActorRequest(actor = Did(did = userKey.id)))
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

    override fun searchStatus(query: String) =
        SearchStatusRemoteMediator(
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
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                service,
                accountKey,
                query,
            )
        }.flow.cachedIn(scope)

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                service,
                accountKey,
            )
        }.flow

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
        throw UnsupportedOperationException("Bluesky does not support discover hashtags")

    override fun discoverStatuses() = throw UnsupportedOperationException("Bluesky does not support discover statuses")

    override fun composeConfig(statusKey: MicroBlogKey?): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(300),
            media = ComposeConfig.Media(4, true),
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

    private val preferences: MemCacheable<List<PreferencesUnion>> by lazy {
        MemCacheable(
            key = "preferences_$accountKey",
        ) {
            service
                .getPreferences()
                .maybeResponse()
                ?.preferences
                .orEmpty()
        }
    }

    private val myFeedsKey = "my_feeds_$accountKey"

    val myFeeds: CacheData<ImmutableList<UiList>> by lazy {
        MemCacheable(
            key = myFeedsKey,
        ) {
            val preferences =
                service
                    .getPreferences()
                    .maybeResponse()
                    ?.preferences
                    .orEmpty()
            val items =
                preferences
                    .filterIsInstance<PreferencesUnion.SavedFeedsPrefV2>()
                    .firstOrNull()
                    ?.value
                    ?.items
                    ?.filter {
                        it.type == Type.Feed
                    }.orEmpty()
            service
                .getFeedGenerators(
                    GetFeedGeneratorsQueryParams(
                        feeds =
                            items
                                .map { AtUri(it.value) }
                                .toImmutableList(),
                    ),
                ).maybeResponse()
                ?.feeds
                ?.map {
                    it.render(accountKey)
                }.orEmpty()
                .toImmutableList()
        }
    }

    fun popularFeeds(
        query: String?,
        scope: CoroutineScope,
    ): Flow<PagingData<Pair<UiList, Boolean>>> =
        Pager(
            config = PagingConfig(pageSize = 20),
        ) {
            object : BasePagingSource<String, UiList>() {
                override fun getRefreshKey(state: PagingState<String, UiList>): String? = null

                override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiList> {
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
                    MemCacheable.subscribe<ImmutableList<UiList>>(myFeedsKey),
                ) { popular, my ->
                    popular.map { item ->
                        item to my.any { it.id == item.id }
                    }
                }
            }.cachedIn(scope)

    private fun feedInfoKey(uri: String) = "feed_info_$uri"

    fun feedInfo(uri: String): MemCacheable<UiList> =
        MemCacheable(
            key = feedInfoKey(uri),
        ) {
            service
                .getFeedGenerator(
                    GetFeedGeneratorQueryParams(
                        feed = AtUri(uri),
                    ),
                ).requireResponse()
                .view
                .render(accountKey)
        }

    fun feedTimeline(
        uri: String,
        pageSize: Int = 20,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            accountRepository = accountRepository,
            mediator = feedTimelineLoader(uri),
        )

    fun feedTimelineLoader(uri: String) =
        FeedTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
            database = database,
            uri = uri,
        )

    suspend fun subscribeFeed(data: UiList) {
        MemCacheable.updateWith<ImmutableList<UiList>>(
            key = myFeedsKey,
        ) {
            (it + data).toImmutableList()
        }
        tryRun {
            val currentPreferences = service.getPreferences().requireResponse()
            val feedInfo =
                service
                    .getFeedGenerator(GetFeedGeneratorQueryParams(feed = AtUri(data.id)))
                    .requireResponse()
            val newPreferences = currentPreferences.preferences.toMutableList()
            val prefIndex = newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPref }
            if (prefIndex != -1) {
                val pref = newPreferences[prefIndex] as PreferencesUnion.SavedFeedsPref
                val newPref =
                    pref.value.copy(
                        saved = (pref.value.saved + feedInfo.view.uri).toImmutableList(),
                        pinned = (pref.value.pinned + feedInfo.view.uri).toImmutableList(),
                    )
                newPreferences[prefIndex] = PreferencesUnion.SavedFeedsPref(newPref)
            }
            val prefV2Index =
                newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPrefV2 }
            if (prefV2Index != -1) {
                val pref = newPreferences[prefV2Index] as PreferencesUnion.SavedFeedsPrefV2
                val newPref =
                    pref.value.copy(
                        items =
                            (
                                pref.value.items +
                                    SavedFeed(
                                        type = Type.Feed,
                                        value = feedInfo.view.uri.atUri,
                                        pinned = true,
                                        id = Uuid.random().toString(),
                                    )
                            ).toImmutableList(),
                    )
                newPreferences[prefV2Index] = PreferencesUnion.SavedFeedsPrefV2(newPref)
            }

            service.putPreferences(
                request =
                    PutPreferencesRequest(
                        preferences = newPreferences.toImmutableList(),
                    ),
            )
            myFeeds.refresh()
        }.onFailure {
            MemCacheable.updateWith<ImmutableList<UiList>>(
                key = myFeedsKey,
            ) {
                it.filterNot { item -> item.id == data.id }.toImmutableList()
            }
        }
    }

    suspend fun unsubscribeFeed(data: UiList) {
        MemCacheable.updateWith<ImmutableList<UiList>>(
            key = myFeedsKey,
        ) {
            it.filterNot { item -> item.id == data.id }.toImmutableList()
        }
        tryRun {
            val currentPreferences = service.getPreferences().requireResponse()
            val feedInfo =
                service
                    .getFeedGenerator(GetFeedGeneratorQueryParams(feed = AtUri(data.id)))
                    .requireResponse()
            val newPreferences = currentPreferences.preferences.toMutableList()
            val prefIndex = newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPref }
            if (prefIndex != -1) {
                val pref = newPreferences[prefIndex] as PreferencesUnion.SavedFeedsPref
                val newPref =
                    pref.value.copy(
                        saved =
                            pref.value.saved
                                .filterNot { it == feedInfo.view.uri }
                                .toImmutableList(),
                        pinned =
                            pref.value.pinned
                                .filterNot { it == feedInfo.view.uri }
                                .toImmutableList(),
                    )
                newPreferences[prefIndex] = PreferencesUnion.SavedFeedsPref(newPref)
            }
            val prefV2Index =
                newPreferences.indexOfFirst { it is PreferencesUnion.SavedFeedsPrefV2 }
            if (prefV2Index != -1) {
                val pref = newPreferences[prefV2Index] as PreferencesUnion.SavedFeedsPrefV2
                val newPref =
                    pref.value.copy(
                        items =
                            pref.value.items
                                .filterNot { it.value == feedInfo.view.uri.atUri }
                                .toImmutableList(),
                    )
                newPreferences[prefV2Index] = PreferencesUnion.SavedFeedsPrefV2(newPref)
            }
            service.putPreferences(
                request =
                    PutPreferencesRequest(
                        preferences = newPreferences.toImmutableList(),
                    ),
            )
            myFeeds.refresh()
        }.onFailure {
            MemCacheable.updateWith<ImmutableList<UiList>>(
                key = myFeedsKey,
            ) {
                (it + data).toImmutableList()
            }
        }
    }

    suspend fun favouriteFeed(data: UiList) {
        MemCacheable.update(
            key = feedInfoKey(data.id),
            value =
                data.copy(
                    liked = !data.liked,
                ),
        )

        tryRun {
            val feedInfo =
                service
                    .getFeedGenerator(GetFeedGeneratorQueryParams(feed = AtUri(data.id)))
                    .requireResponse()
            val likedUri =
                feedInfo.view.viewer
                    ?.like
                    ?.atUri
            if (likedUri != null) {
                deleteLikeRecord(likedUri)
            } else {
                createLikeRecord(cid = feedInfo.view.cid.cid, uri = feedInfo.view.uri.atUri)
            }
        }.onFailure {
            MemCacheable.update(
                key = feedInfoKey(data.id),
                value =
                    data.copy(
                        liked = data.liked,
                    ),
            )
        }
    }

    private val myListKey = "my_list_$accountKey"

    override fun myList(scope: CoroutineScope): Flow<PagingData<UiList>> =
        memoryPager(
            pageSize = 20,
            pagingKey = myListKey,
            scope = scope,
            mediator =
                object : BaseRemoteMediator<Int, UiList>() {
                    var cursor: String? = null

                    override suspend fun doLoad(
                        loadType: LoadType,
                        state: PagingState<Int, UiList>,
                    ): MediatorResult {
                        val result =
                            service
                                .getLists(
                                    params =
                                        GetListsQueryParams(
                                            actor = Did(did = accountKey.id),
                                            cursor = cursor,
                                        ),
                                ).requireResponse()
                        val items =
                            result
                                .lists
                                .map {
                                    it.render(accountKey)
                                }.toImmutableList()
                        cursor = result.cursor
                        MemoryPagingSource.update<UiList>(
                            key = myListKey,
                            value = items,
                        )
                        return MediatorResult.Success(
                            endOfPaginationReached = cursor == null,
                        )
                    }
                },
        )

    private fun listInfoKey(uri: String) = "list_info_$uri"

    override fun listInfo(listId: String): MemCacheable<UiList> =
        MemCacheable(
            key = listInfoKey(listId),
        ) {
            service
                .getList(
                    GetListQueryParams(
                        list = AtUri(listId),
                    ),
                ).requireResponse()
                .list
                .render(accountKey)
        }

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
            database = database,
            uri = listId,
        )

    private suspend fun createList(
        title: String,
        description: String?,
        icon: FileItem?,
    ) {
        tryRun {
            val iconInfo =
                if (icon != null) {
                    service.uploadBlob(icon.readBytes()).maybeResponse()
                } else {
                    null
                }
            val record =
                app.bsky.graph.List(
                    purpose = app.bsky.graph.Token.Curatelist,
                    name = title,
                    description = description,
                    avatar = iconInfo?.blob,
                    createdAt = Clock.System.now().toDeprecatedInstant(),
                )
            service.createRecord(
                request =
                    CreateRecordRequest(
                        repo = Did(did = accountKey.id),
                        collection = Nsid("app.bsky.graph.list"),
                        record = record.bskyJson(),
                    ),
            )
        }.onFailure {
            it.printStackTrace()
        }.onSuccess {
            val uri = it.requireResponse().uri
            service
                .getList(
                    params =
                        GetListQueryParams(
                            list = uri,
                        ),
                ).requireResponse()
                .list
                .render(accountKey)
                .let { list ->
                    MemoryPagingSource.updateWith<UiList>(
                        key = myListKey,
                    ) {
                        (listOf(list) + it).toImmutableList()
                    }
                }
        }
    }

    override suspend fun deleteList(listId: String) {
        tryRun {
            val id = listId.substringAfterLast('/')
            service.applyWrites(
                request =
                    ApplyWritesRequest(
                        repo = Did(did = accountKey.id),
                        writes =
                            persistentListOf(
                                ApplyWritesRequestWriteUnion.Delete(
                                    value =
                                        ApplyWritesDelete(
                                            collection = Nsid("app.bsky.graph.list"),
                                            rkey = RKey(id),
                                        ),
                                ),
                            ),
                    ),
            )
        }.onFailure {
            it.printStackTrace()
        }.onSuccess {
            MemoryPagingSource.updateWith<UiList>(
                key = myListKey,
            ) {
                it.filterNot { item -> item.id == listId }.toImmutableList()
            }
        }
    }

    private suspend fun updateList(
        uri: String,
        title: String,
        description: String?,
        icon: FileItem?,
    ) {
        tryRun {
            val currentInfo: app.bsky.graph.List =
                service
                    .getRecord(
                        params =
                            com.atproto.repo.GetRecordQueryParams(
                                collection = Nsid("app.bsky.graph.list"),
                                repo = Did(did = accountKey.id),
                                rkey = RKey(uri.substringAfterLast('/')),
                            ),
                    ).requireResponse()
                    .value
                    .decodeAs()

            val iconInfo =
                if (icon != null) {
                    service.uploadBlob(icon.readBytes()).maybeResponse()
                } else {
                    null
                }
            val newRecord =
                currentInfo
                    .copy(
                        name = title,
                        description = description,
                    ).let {
                        if (iconInfo != null) {
                            it.copy(avatar = iconInfo.blob)
                        } else {
                            it
                        }
                    }
            service.putRecord(
                request =
                    PutRecordRequest(
                        repo = Did(did = accountKey.id),
                        collection = Nsid("app.bsky.graph.list"),
                        rkey = RKey(uri.substringAfterLast('/')),
                        record = newRecord.bskyJson(),
                    ),
            )
        }.onFailure {
            it.printStackTrace()
        }.onSuccess {
            MemoryPagingSource.updateWith<UiList>(
                key = myListKey,
            ) {
                it
                    .map { item ->
                        if (item.id == uri) {
                            item.copy(
                                title = title,
                                description = description,
                            )
                        } else {
                            item
                        }
                    }.toImmutableList()
            }
        }
    }

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
                    private var cursor: String? = null

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
                                .getList(
                                    params =
                                        GetListQueryParams(
                                            list = AtUri(listId),
                                            cursor = cursor,
                                            limit = state.config.pageSize.toLong(),
                                        ),
                                ).maybeResponse()
                        cursor = response?.cursor
                        val result =
                            response
                                ?.items
                                ?.map {
                                    it.subject.render(accountKey)
                                } ?: emptyList()

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
                            endOfPaginationReached = cursor == null,
                        )
                    }
                },
        )

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        tryRun {
            val user =
                service
                    .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                    .requireResponse()
                    .render(accountKey)

            MemoryPagingSource.updateWith(
                key = listMemberKey(listId),
            ) {
                (listOf(user) + it)
                    .distinctBy {
                        it.key
                    }.toImmutableList()
            }
            val list =
                service
                    .getList(
                        params =
                            GetListQueryParams(
                                list = AtUri(listId),
                            ),
                    ).requireResponse()
                    .list
                    .render(accountKey)
            MemCacheable.updateWith<ImmutableList<UiList>>(userListsKey(userKey)) {
                (it + list).toImmutableList()
            }
            service.createRecord(
                CreateRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.graph.listitem"),
                    record =
                        app.bsky.graph
                            .Listitem(
                                list = AtUri(listId),
                                subject = Did(userKey.id),
                                createdAt = Clock.System.now().toDeprecatedInstant(),
                            ).bskyJson(),
                ),
            )
        }
    }

    override suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        tryRun {
            MemoryPagingSource.updateWith<UiUserV2>(
                key = listMemberKey(listId),
            ) {
                it
                    .filter { user -> user.key.id != userKey.id }
                    .toImmutableList()
            }
            MemCacheable.updateWith<ImmutableList<UiList>>(userListsKey(userKey)) {
                it
                    .filter { list -> list.id != listId }
                    .toImmutableList()
            }
            var record: ListRecordsRecord? = null
            var cursor: String? = null
            while (record == null) {
                val response =
                    service
                        .listRecords(
                            params =
                                ListRecordsQueryParams(
                                    repo = Did(did = accountKey.id),
                                    collection = Nsid("app.bsky.graph.listitem"),
                                    limit = 100,
                                    cursor = cursor,
                                ),
                        ).requireResponse()
                if (response.cursor == null || response.records.isEmpty()) {
                    break
                }
                cursor = response.cursor
                record =
                    response.records
                        .firstOrNull {
                            val item: app.bsky.graph.Listitem = it.value.decodeAs()
                            item.list.atUri == listId && item.subject.did == userKey.id
                        }
            }
            if (record != null) {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = accountKey.id),
                        collection = Nsid("app.bsky.graph.listitem"),
                        rkey = RKey(record.uri.atUri.substringAfterLast('/')),
                    ),
                )
            }
        }
    }

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() =
            persistentListOf(
                ListMetaDataType.TITLE,
                ListMetaDataType.DESCRIPTION,
                ListMetaDataType.AVATAR,
            )

    override suspend fun updateList(
        listId: String,
        metaData: ListMetaData,
    ) {
        updateList(
            uri = listId,
            title = metaData.title,
            description = metaData.description,
            icon = metaData.avatar,
        )
    }

    override suspend fun createList(metaData: ListMetaData) {
        createList(
            title = metaData.title,
            description = metaData.description,
            icon = metaData.avatar,
        )
    }

    override fun listMemberCache(listId: String): Flow<ImmutableList<UiUserV2>> =
        MemoryPagingSource.getFlow<UiUserV2>(listMemberKey(listId))

    private fun userListsKey(userKey: MicroBlogKey) = "userLists_${userKey.id}"

    override fun userLists(userKey: MicroBlogKey): MemCacheable<ImmutableList<UiList>> =
        MemCacheable(
            key = userListsKey(userKey),
        ) {
            var cursor: String? = null
            val lists = mutableListOf<UiList>()
            val allLists =
                service
                    .getLists(
                        params =
                            GetListsQueryParams(
                                actor = Did(did = accountKey.id),
                                limit = 100,
                            ),
                    ).requireResponse()
                    .lists
                    .map {
                        it.render(accountKey)
                    }
            while (true) {
                val response =
                    service
                        .listRecords(
                            params =
                                ListRecordsQueryParams(
                                    repo = Did(did = accountKey.id),
                                    collection = Nsid("app.bsky.graph.listitem"),
                                    limit = 100,
                                    cursor = cursor,
                                ),
                        ).requireResponse()
                lists.addAll(
                    response.records
                        .filter {
                            val item: Listitem = it.value.decodeAs()
                            item.subject.did == userKey.id
                        }.mapNotNull {
                            val item: Listitem = it.value.decodeAs()
                            allLists.firstOrNull { it.id == item.list.atUri }
                        },
                )
                cursor = response.cursor
                if (cursor == null) {
                    break
                }
            }
            lists.toImmutableList()
        }

    private val notificationMarkerKey: String
        get() = "notificationBadgeCount_$accountKey"

    override fun notificationBadgeCount(): CacheData<Int> =
        MemCacheable(
            key = notificationMarkerKey,
            fetchSource = {
                val notifications =
                    service
                        .listNotifications(
                            params = ListNotificationsQueryParams(limit = 40),
                        ).requireResponse()
                        .notifications
                notifications.count { !it.isRead }
            },
        )

    override fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> =
        Pager(
            config = PagingConfig(pageSize = 20),
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
            .map {
                it.map {
                    it.render(accountKey = accountKey, credential = credential, statusEvent = this)
                }
            }.cachedIn(scope)

    override fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        Pager(
            config = PagingConfig(pageSize = 20),
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
            .map {
                it.map {
                    it.render(accountKey = accountKey, credential = credential, statusEvent = this)
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
                    .mapNotNull {
                        it?.render(
                            accountKey = accountKey,
                            credential = credential,
                            statusEvent = this,
                        )
                    }
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

    override fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
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
            config = PagingConfig(pageSize = pageSize),
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
                loader =
                    UserTimelineRemoteMediator(
                        service = service,
                        accountKey = accountKey,
                        database = database,
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
                        database,
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
                            database,
                        ),
                )
            } else {
                null
            },
        ).toPersistentList()
}

internal inline fun <reified T : Any> T.bskyJson(): JsonContent = bskyJson.encodeAsJsonContent(this)
