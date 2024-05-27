package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.bsky.actor.GetProfileQueryParams
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.feed.ViewerState
import app.bsky.graph.MuteActorRequest
import app.bsky.graph.UnmuteActorRequest
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import com.atproto.moderation.CreateReportRequest
import com.atproto.moderation.CreateReportRequestSubjectUnion
import com.atproto.moderation.Token
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.BlueskyComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.SupportedComposeEvent
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.bluesky.getService
import dev.dimension.flare.data.repository.LocalFilterRepository
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
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.model.JsonContent

@OptIn(ExperimentalPagingApi::class)
class BlueskyDataSource(
    override val account: UiAccount.Bluesky,
) : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()
    private val appDatabase: AppDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                HomeTimelineRemoteMediator(
                    account.getService(appDatabase),
                    account.accountKey,
                    database,
                    pagingKey,
                ),
        )

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forNotification = true),
            scope = scope,
            mediator =
                when (type) {
                    NotificationFilter.All ->
                        NotificationRemoteMediator(
                            account.getService(appDatabase),
                            account.accountKey,
                            database,
                            pagingKey,
                        )

                    else -> throw IllegalArgumentException("Unsupported notification filter")
                },
        )

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All)

    override fun userByAcct(acct: String): CacheData<UiUser> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    account.getService(appDatabase)
                        .getProfile(GetProfileQueryParams(actor = Handle(handle = name)))
                        .requireResponse()
                        .toDbUser(account.accountKey.host)
                database.dbUserQueries.insert(
                    user_key = user.user_key,
                    platform_type = user.platform_type,
                    name = user.name,
                    handle = user.handle,
                    content = user.content,
                    host = user.host,
                )
            },
            cacheSource = {
                database.dbUserQueries.findByHandleAndHost(name, host, PlatformType.Bluesky)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .mapNotNull { it.toUi(account.accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
        return Cacheable(
            fetchSource = {
                val user =
                    account.getService(appDatabase)
                        .getProfile(GetProfileQueryParams(actor = Did(did = id)))
                        .requireResponse()
                        .toDbUser(account.accountKey.host)
                database.dbUserQueries.insert(
                    user_key = user.user_key,
                    platform_type = user.platform_type,
                    name = user.name,
                    handle = user.handle,
                    content = user.content,
                    host = user.host,
                )
            },
            cacheSource = {
                database.dbUserQueries.findByKey(MicroBlogKey(id, account.accountKey.host))
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .mapNotNull { it.toUi(account.accountKey) }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
        return MemCacheable<UiRelation>(
            relationKeyWithUserKey(userKey),
        ) {
            account.getService(appDatabase)
                .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                .requireResponse()
                .toDbUser(account.accountKey.host)
                .toUi(account.accountKey)
                .let {
                    if (it is UiUser.Bluesky) {
                        it.relation
                    } else {
                        throw IllegalStateException("User is not a Bluesky user")
                    }
                }
        }.toUi()
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                UserTimelineRemoteMediator(
                    account.getService(appDatabase),
                    account.accountKey,
                    database,
                    userKey,
                    pagingKey,
                    onlyMedia = mediaOnly,
                ),
        )

    override fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                StatusDetailRemoteMediator(
                    statusKey,
                    account.getService(appDatabase),
                    account.accountKey,
                    database,
                    pagingKey,
                    statusOnly = false,
                ),
        )

    override fun status(statusKey: MicroBlogKey): CacheData<UiStatus> {
        val pagingKey = "status_only_$statusKey"
        val service = account.getService(appDatabase)
        return Cacheable(
            fetchSource = {
                val result =
                    service.getPosts(
                        GetPostsQueryParams(
                            persistentListOf(AtUri(statusKey.id)),
                        ),
                    ).requireResponse().posts.firstOrNull().let {
                        listOfNotNull(it)
                    }
                Bluesky.savePost(
                    account.accountKey,
                    pagingKey,
                    database,
                    result,
                )
            },
            cacheSource = {
                database.dbStatusQueries.get(statusKey, account.accountKey)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .mapNotNull { it.content.toUi(account.accountKey) }
            },
        )
    }

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        require(data is BlueskyComposeData)
        val maxProgress = data.medias.size + 1
        val service = data.account.getService(appDatabase)
        val mediaBlob =
            data.medias.mapIndexedNotNull { index, item ->
                service.uploadBlob(item.readBytes()).also {
                    progress(ComposeProgress(index + 1, maxProgress))
                }.maybeResponse()
            }.map {
                it.blob
            }
        val post =
            Post(
                text = data.content,
                createdAt = Clock.System.now(),
                embed =
                    data.quoteId?.let { quoteId ->
                        service.getPosts(GetPostsQueryParams(persistentListOf(AtUri(quoteId))))
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
                                blobs.map { blob ->
                                    ImagesImage(image = blob, alt = "")
                                }.toImmutableList(),
                            ),
                        )
                    },
                reply =
                    data.inReplyToID?.let { inReplyToID ->
                        service.getPosts(GetPostsQueryParams(persistentListOf(AtUri(inReplyToID))))
                            .maybeResponse()
                            ?.posts
                            ?.firstOrNull()
                    }?.let { item ->
                        val root =
                            item.record.jsonElement().jsonObjectOrNull?.get("reply")?.jsonObjectOrNull?.get("root")
                                ?.jsonObjectOrNull?.let { root ->
                                    StrongRef(
                                        uri = AtUri(root["uri"]?.jsonPrimitive?.content ?: item.uri.atUri),
                                        cid = Cid(root["cid"]?.jsonPrimitive?.content ?: item.cid.cid),
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
        val json =
            Json {
                ignoreUnknownKeys = true
                classDiscriminator = "${'$'}type"
            }
        service.createRecord(
            CreateRecordRequest(
                repo = Did(did = data.account.accountKey.id),
                collection = Nsid("app.bsky.feed.post"),
                record = json.encodeToJsonElement(post).jsonContent(),
            ),
        )
    }

    suspend fun report(
        data: UiStatus.Bluesky,
        reason: BlueskyReportStatusState.ReportReason,
    ) {
        runCatching {
            val service = account.getService(appDatabase)
            service.createReport(
                CreateReportRequest(
                    reasonType =
                        when (reason) {
                            BlueskyReportStatusState.ReportReason.Spam -> Token.REASON_SPAM
                            BlueskyReportStatusState.ReportReason.Violation -> Token.REASON_VIOLATION
                            BlueskyReportStatusState.ReportReason.Misleading -> Token.REASON_MISLEADING
                            BlueskyReportStatusState.ReportReason.Sexual -> Token.REASON_SEXUAL
                            BlueskyReportStatusState.ReportReason.Rude -> Token.REASON_RUDE
                            BlueskyReportStatusState.ReportReason.Other -> Token.REASON_OTHER
                        },
                    subject =
                        CreateReportRequestSubjectUnion.RepoStrongRef(
                            value =
                                StrongRef(
                                    uri = AtUri(data.uri),
                                    cid = Cid(data.cid),
                                ),
                        ),
                ),
            )
        }
    }

    suspend fun reblog(data: UiStatus.Bluesky) {
        updateStatusUseCase<StatusContent.Bluesky>(
            statusKey = data.statusKey,
            accountKey = account.accountKey,
            cacheDatabase = database,
        ) { content ->
            val uri =
                if (data.reaction.reposted) {
                    null
                } else {
                    AtUri("")
                }
            val count =
                if (data.reaction.reposted) {
                    (content.data.repostCount ?: 0) - 1
                } else {
                    (content.data.repostCount ?: 0) + 1
                }.coerceAtLeast(0)
            content.copy(
                data =
                    content.data.copy(
                        viewer =
                            content.data.viewer?.copy(
                                repost = uri,
                            ) ?: ViewerState(
                                repost = uri,
                            ),
                        repostCount = count,
                    ),
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            if (data.reaction.reposted && data.reaction.repostUri != null) {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = account.accountKey.id),
                        collection = Nsid("app.bsky.feed.repost"),
                        rkey = data.reaction.repostUri.substringAfterLast('/'),
                    ),
                )
            } else {
                val result =
                    service.createRecord(
                        CreateRecordRequest(
                            repo = Did(did = account.accountKey.id),
                            collection = Nsid("app.bsky.feed.repost"),
                            record =
                                buildJsonObject {
                                    put("\$type", "app.bsky.feed.repost")
                                    put("createdAt", Clock.System.now().toString())
                                    put(
                                        "subject",
                                        buildJsonObject {
                                            put("cid", data.cid)
                                            put("uri", data.uri)
                                        },
                                    )
                                }.jsonContent(),
                        ),
                    ).requireResponse()
                updateStatusUseCase<StatusContent.Bluesky>(
                    statusKey = data.statusKey,
                    accountKey = account.accountKey,
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
                statusKey = data.statusKey,
                accountKey = account.accountKey,
                cacheDatabase = database,
            ) { content ->
                val uri =
                    if (data.reaction.reposted) {
                        AtUri(data.reaction.repostUri ?: "")
                    } else {
                        null
                    }
                val count =
                    if (data.reaction.reposted) {
                        (content.data.repostCount ?: 0) + 1
                    } else {
                        (content.data.repostCount ?: 0) - 1
                    }.coerceAtLeast(0)
                content.copy(
                    data =
                        content.data.copy(
                            viewer =
                                content.data.viewer?.copy(
                                    repost = uri,
                                ) ?: ViewerState(
                                    repost = uri,
                                ),
                            repostCount = count,
                        ),
                )
            }
        }
    }

    suspend fun like(data: UiStatus.Bluesky) {
        updateStatusUseCase<StatusContent.Bluesky>(
            statusKey = data.statusKey,
            accountKey = account.accountKey,
            cacheDatabase = database,
        ) { content ->
            val uri =
                if (data.reaction.liked) {
                    null
                } else {
                    AtUri("")
                }
            val count =
                if (data.reaction.liked) {
                    (content.data.likeCount ?: 0) - 1
                } else {
                    (content.data.likeCount ?: 0) + 1
                }.coerceAtLeast(0)
            content.copy(
                data =
                    content.data.copy(
                        viewer =
                            content.data.viewer?.copy(
                                like = uri,
                            ) ?: ViewerState(
                                like = uri,
                            ),
                        likeCount = count,
                    ),
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            if (data.reaction.liked && data.reaction.likedUri != null) {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = account.accountKey.id),
                        collection = Nsid("app.bsky.feed.like"),
                        rkey = data.reaction.likedUri.substringAfterLast('/'),
                    ),
                )
            } else {
                val result =
                    service.createRecord(
                        CreateRecordRequest(
                            repo = Did(did = account.accountKey.id),
                            collection = Nsid("app.bsky.feed.like"),
                            record =
                                buildJsonObject {
                                    put("\$type", "app.bsky.feed.like")
                                    put("createdAt", Clock.System.now().toString())
                                    put(
                                        "subject",
                                        buildJsonObject {
                                            put("cid", data.cid)
                                            put("uri", data.uri)
                                        },
                                    )
                                }.jsonContent(),
                        ),
                    ).requireResponse()
                updateStatusUseCase<StatusContent.Bluesky>(
                    statusKey = data.statusKey,
                    accountKey = account.accountKey,
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
                statusKey = data.statusKey,
                accountKey = account.accountKey,
                cacheDatabase = database,
            ) { content ->
                val uri =
                    if (data.reaction.liked) {
                        AtUri(data.reaction.likedUri ?: "")
                    } else {
                        null
                    }
                val count =
                    if (data.reaction.liked) {
                        (content.data.likeCount ?: 0) + 1
                    } else {
                        (content.data.likeCount ?: 0) - 1
                    }.coerceAtLeast(0)
                content.copy(
                    data =
                        content.data.copy(
                            viewer =
                                content.data.viewer?.copy(
                                    like = uri,
                                ) ?: ViewerState(
                                    like = uri,
                                ),
                            likeCount = count,
                        ),
                )
            }
        }
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        runCatching {
            val service = account.getService(appDatabase)
            service.deleteRecord(
                DeleteRecordRequest(
                    repo = Did(did = account.accountKey.id),
                    collection = Nsid("app.bsky.feed.post"),
                    rkey = statusKey.id.substringAfterLast('/'),
                ),
            )
            // delete status from cache
            database.dbStatusQueries.delete(status_key = statusKey, account_key = account.accountKey)
            database.dbPagingTimelineQueries.deleteStatus(account_key = account.accountKey, status_key = statusKey)
        }
    }

    suspend fun unfollow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.Bluesky>(
            key = key,
        ) {
            it.copy(
                following = false,
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            val user =
                service.getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                    .requireResponse()

            val followRepo = user.viewer?.following?.atUri
            if (followRepo != null) {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = account.accountKey.id),
                        collection = Nsid("app.bsky.graph.follow"),
                        rkey = followRepo.substringAfterLast('/'),
                    ),
                )
            }
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Bluesky>(
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
        MemCacheable.updateWith<UiRelation.Bluesky>(
            key = key,
        ) {
            it.copy(
                following = true,
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            service.createRecord(
                CreateRecordRequest(
                    repo = Did(did = account.accountKey.id),
                    collection = Nsid("app.bsky.graph.follow"),
                    record =
                        buildJsonObject {
                            put("\$type", "app.bsky.graph.follow")
                            put("createdAt", Clock.System.now().toString())
                            put("subject", userKey.id)
                        }.jsonContent(),
                ),
            )
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Bluesky>(
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
        MemCacheable.updateWith<UiRelation.Bluesky>(
            key = key,
        ) {
            it.copy(
                blocking = true,
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            service.createRecord(
                CreateRecordRequest(
                    repo = Did(did = account.accountKey.id),
                    collection = Nsid("app.bsky.graph.block"),
                    record =
                        buildJsonObject {
                            put("\$type", "app.bsky.graph.block")
                            put("createdAt", Clock.System.now().toString())
                            put("subject", userKey.id)
                        }.jsonContent(),
                ),
            )
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Bluesky>(
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
        MemCacheable.updateWith<UiRelation.Bluesky>(
            key = key,
        ) {
            it.copy(
                blocking = false,
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            val user =
                service.getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                    .requireResponse()

            val blockRepo = user.viewer?.blocking?.atUri
            if (blockRepo != null) {
                service.deleteRecord(
                    DeleteRecordRequest(
                        repo = Did(did = account.accountKey.id),
                        collection = Nsid("app.bsky.graph.block"),
                        rkey = blockRepo.substringAfterLast('/'),
                    ),
                )
            }
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Bluesky>(
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
        MemCacheable.updateWith<UiRelation.Bluesky>(
            key = key,
        ) {
            it.copy(
                muting = true,
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            service.muteActor(MuteActorRequest(actor = Did(did = userKey.id)))
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Bluesky>(
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
        MemCacheable.updateWith<UiRelation.Bluesky>(
            key = key,
        ) {
            it.copy(
                muting = false,
            )
        }
        runCatching {
            val service = account.getService(appDatabase)
            service.unmuteActor(UnmuteActorRequest(actor = Did(did = userKey.id)))
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Bluesky>(
                key = key,
            ) {
                it.copy(
                    muting = true,
                )
            }
        }
    }

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        val service = account.getService(appDatabase)
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forSearch = true),
            scope = scope,
            mediator =
                SearchStatusRemoteMediator(
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
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUser>> {
        val service = account.getService(appDatabase)
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                service,
                account.accountKey,
                query,
            )
        }.flow.cachedIn(scope)
    }

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUser>> {
        val service = account.getService(appDatabase)
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                service,
                account.accountKey,
            )
        }.flow
    }

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> {
        throw UnsupportedOperationException("Bluesky does not support discover hashtags")
    }

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        throw UnsupportedOperationException("Bluesky does not support discover statuses")
    }

    override fun supportedComposeEvent(statusKey: MicroBlogKey?): List<SupportedComposeEvent> {
        return listOf(
            SupportedComposeEvent.Media,
        )
    }

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        require(relation is UiRelation.Bluesky)
        when {
            relation.following -> unfollow(userKey)
            relation.blocking -> unblock(userKey)
            else -> follow(userKey)
        }
    }

    override fun profileActions(): List<ProfileAction> {
        return listOf(
            object : ProfileAction.Mute {
                override suspend fun invoke(
                    userKey: MicroBlogKey,
                    relation: UiRelation,
                ) {
                    require(relation is UiRelation.Bluesky)
                    if (relation.muting) {
                        unmute(userKey)
                    } else {
                        mute(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean {
                    require(relation is UiRelation.Bluesky)
                    return relation.muting
                }
            },
            object : ProfileAction.Block {
                override suspend fun invoke(
                    userKey: MicroBlogKey,
                    relation: UiRelation,
                ) {
                    require(relation is UiRelation.Bluesky)
                    if (relation.blocking) {
                        unblock(userKey)
                    } else {
                        block(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean {
                    require(relation is UiRelation.Bluesky)
                    return relation.blocking
                }
            },
        )
    }
}

fun JsonElement.jsonContent(): JsonContent = JSON.decodeFromJsonElement(this)

fun JsonContent.jsonElement(): JsonElement = JSON.encodeToJsonElement(this)
