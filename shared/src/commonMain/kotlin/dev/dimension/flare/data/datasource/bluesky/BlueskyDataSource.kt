package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.PreferencesUnion
import app.bsky.bookmark.CreateBookmarkRequest
import app.bsky.bookmark.DeleteBookmarkRequest
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.feed.ViewerState
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
import com.atproto.identity.ResolveHandleQueryParams
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
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.createSendingDirectMessage
import dev.dimension.flare.data.datasource.microblog.list.ListDataSource
import dev.dimension.flare.data.datasource.microblog.list.ListHandler
import dev.dimension.flare.data.datasource.microblog.list.ListLoader
import dev.dimension.flare.data.datasource.microblog.list.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.list.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineLoader
import dev.dimension.flare.data.datasource.microblog.pagingConfig
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
import dev.dimension.flare.shared.image.ImageCompressor
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
import dev.dimension.flare.ui.model.mapper.parseBskyFacets
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusState
import kotlin.time.Clock
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

@OptIn(ExperimentalPagingApi::class)
internal class BlueskyDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    StatusEvent.Bluesky,
    ListDataSource,
    DirectMessageDataSource,
    RelationDataSource {
    private val database: CacheDatabase by inject()
    private val appDatabase: AppDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountRepository: AccountRepository by inject()
    private val inAppNotification: InAppNotification by inject()
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
                val isDid = statusKey.id.startsWith("at://did:")
                if (isDid) {
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
                    database.connect {
                        Bluesky.savePost(
                            accountKey,
                            pagingKey,
                            database,
                            result,
                        )
                    }
                } else {
                    // "at://${handle}/app.bsky.feed.post/${id}"
                    val handle = statusKey.id.substringAfter("at://").substringBefore("/")
                    val id = statusKey.id.substringAfterLast('/')
                    val did = service.resolveHandle(ResolveHandleQueryParams(Handle(handle))).requireResponse().did
                    val actualAtUri = AtUri("at://${did.did}/app.bsky.feed.post/$id")
                    val result =
                        service
                            .getPosts(
                                GetPostsQueryParams(
                                    persistentListOf(actualAtUri),
                                ),
                            ).requireResponse()
                            .posts
                            .firstOrNull()
                            .let {
                                listOfNotNull(it)
                            }
                    database.connect {
                        Bluesky.savePost(
                            accountKey,
                            pagingKey,
                            database,
                            result,
                        )
                    }
                }
            },
            cacheSource = {
                database
                    .pagingTimelineDao()
                    .get(pagingKey, accountType = AccountType.Specific(accountKey))
                    .mapNotNull { it?.render(this) }
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
                                createdAt = Clock.System.now(),
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

    override suspend fun block(userKey: MicroBlogKey) {
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
                                createdAt = Clock.System.now(),
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

    override suspend fun mute(userKey: MicroBlogKey) {
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
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = pagingConfig,
        ) {
            SearchUserPagingSource(
                service,
                accountKey,
                query,
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

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
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
                .getPreferencesForActor()
                .maybeResponse()
                ?.preferences
                .orEmpty()
        }
    }
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
                    database.listDao().getListKeysFlow(myFeedsKey),
                ) { popular, my ->
                    popular.map { item ->
                        item to my.any { it == item.key }
                    }
                }
            }.cachedIn(scope)

    private fun feedInfoKey(uri: String) = "feed_info_$uri"

    fun feedInfo(uri: String): MemCacheable<UiList.Feed> =
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

    suspend fun subscribeFeed(data: UiList.Feed) {
        tryRun {
            feedLoader.subscribe(data.key)
            feedHandler.insertToDatabase(data)
        }
    }

    suspend fun unsubscribeFeed(data: UiList.Feed) {
        feedHandler.delete(data.key)
    }

    suspend fun favouriteFeed(data: UiList.Feed) {
        feedHandler.withDatabase {  updataCallback ->
            val newData = data.copy(liked = !data.liked)
            updataCallback(newData)
            tryRun {
                if (newData.liked) {
                    feedLoader.favourite(data.key)
                } else {
                    feedLoader.unfavourite(data.key)
                }
            }.onFailure {
                updataCallback(data)
            }
        }
    }

    override fun listTimeline(listKey: MicroBlogKey) =
        ListTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
            database = database,
            uri = listKey.id,
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

    fun bookmarkTimeline(): BaseTimelineLoader =
        BookmarkTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
            database = database,
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
