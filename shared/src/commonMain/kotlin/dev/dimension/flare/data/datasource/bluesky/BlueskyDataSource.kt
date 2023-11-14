package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import app.bsky.actor.GetProfileQueryParams
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.ViewerState
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
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.ComposeData
import dev.dimension.flare.data.datasource.ComposeProgress
import dev.dimension.flare.data.datasource.MicroblogDataSource
import dev.dimension.flare.data.datasource.NotificationFilter
import dev.dimension.flare.data.datasource.timelinePager
import dev.dimension.flare.data.network.bluesky.getService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.mapper.toUi
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sh.christian.ozone.api.AtIdentifier
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Nsid

@OptIn(ExperimentalPagingApi::class)
class BlueskyDataSource(
    private val account: UiAccount.Bluesky,
) : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()
    private val appDatabase: AppDatabase by inject()

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
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
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
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
                        .getProfile(GetProfileQueryParams(actor = AtIdentifier(atIdentifier = name)))
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
                database.dbUserQueries.findByHandleAndHost(name, host)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .mapNotNull { it.toUi() }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
        return Cacheable(
            fetchSource = {
                val user =
                    account.getService(appDatabase)
                        .getProfile(GetProfileQueryParams(actor = AtIdentifier(atIdentifier = id)))
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
                    .mapNotNull { it.toUi() }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
        return userById(userKey.id).toUi().map {
            it.flatMap {
                if (it is UiUser.Misskey) {
                    UiState.Success(it.relation)
                } else {
                    UiState.Error(IllegalStateException("User is not a Misskey user"))
                }
            }
        }
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                UserTimelineRemoteMediator(
                    account.getService(appDatabase),
                    account.accountKey,
                    database,
                    userKey,
                    pagingKey,
                ),
        )

    override fun context(
        statusKey: MicroBlogKey,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
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
                    account.getService(appDatabase),
                    account.accountKey,
                    database,
                    pagingKey,
                    statusOnly = true,
                ),
        )

    data class BlueskyComposeData(
        val account: UiAccount.Bluesky,
        val content: String,
        val visibility: UiStatus.Misskey.Visibility = UiStatus.Misskey.Visibility.Public,
        val inReplyToID: String? = null,
        val quoteId: String? = null,
        val language: List<String> = listOf("en"),
        val medias: List<FileItem> = emptyList(),
    ) : ComposeData

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
        service.createRecord(
            CreateRecordRequest(
                repo = AtIdentifier(data.account.accountKey.id),
                collection = Nsid("app.bsky.feed.post"),
                record =
                    buildJsonObject {
                        put("\$type", "app.bsky.feed.post")
                        put("createdAt", Clock.System.now().toString())
                        put("text", data.content)
                        if (data.quoteId != null) {
                            val item =
                                service.getPosts(GetPostsQueryParams(persistentListOf(AtUri(data.quoteId))))
                                    .maybeResponse()
                                    ?.posts
                                    ?.firstOrNull()
                            if (item != null) {
                                put(
                                    "embed",
                                    buildJsonObject {
                                        put("\$type", "app.bsky.embed.record")
                                        put(
                                            "record",
                                            buildJsonObject {
                                                put("cid", item.cid.cid)
                                                put("uri", item.uri.atUri)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        if (data.inReplyToID != null) {
                            val item =
                                service.getPosts(GetPostsQueryParams(persistentListOf(AtUri(data.inReplyToID))))
                                    .maybeResponse()
                                    ?.posts
                                    ?.firstOrNull()
                            if (item != null) {
                                put(
                                    "reply",
                                    buildJsonObject {
                                        put(
                                            "parent",
                                            buildJsonObject {
                                                put("cid", item.cid.cid)
                                                put("uri", item.uri.atUri)
                                            },
                                        )
                                        put(
                                            "root",
                                            buildJsonObject {
                                                item.record.jsonObjectOrNull?.get("reply")?.jsonObjectOrNull?.get("root")
                                                    ?.jsonObjectOrNull?.let { root ->
                                                        put("cid", root["cid"]?.jsonPrimitive?.content)
                                                        put("uri", root["uri"]?.jsonPrimitive?.content)
                                                    } ?: run {
                                                    put("cid", item.cid.cid)
                                                    put("uri", item.uri.atUri)
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        if (mediaBlob.any()) {
                            put(
                                "embed",
                                buildJsonObject {
                                    put("\$type", "app.bsky.embed.images")
                                    put(
                                        "images",
                                        buildJsonArray {
                                            mediaBlob.forEach { blob ->
                                                add(
                                                    buildJsonObject {
                                                        put("image", blob.encodeJson().decodeJson())
                                                        put("alt", "")
                                                    },
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                        }
                        put(
                            "langs",
                            buildJsonArray {
                                data.language.forEach { lang ->
                                    add(lang)
                                }
                            },
                        )
                    },
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
                        repo = AtIdentifier(account.accountKey.id),
                        collection = Nsid("app.bsky.feed.repost"),
                        rkey = data.reaction.repostUri.substringAfterLast('/'),
                    ),
                )
            } else {
                val result =
                    service.createRecord(
                        CreateRecordRequest(
                            repo = AtIdentifier(account.accountKey.id),
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
                                },
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
                        repo = AtIdentifier(account.accountKey.id),
                        collection = Nsid("app.bsky.feed.like"),
                        rkey = data.reaction.likedUri.substringAfterLast('/'),
                    ),
                )
            } else {
                val result =
                    service.createRecord(
                        CreateRecordRequest(
                            repo = AtIdentifier(account.accountKey.id),
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
                                },
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
                    repo = AtIdentifier(account.accountKey.id),
                    collection = Nsid("app.bsky.feed.post"),
                    rkey = statusKey.id.substringAfterLast('/'),
                ),
            )
            // delete status from cache
            database.dbStatusQueries.delete(status_key = statusKey, account_key = account.accountKey)
            database.dbPagingTimelineQueries.deleteStatus(account_key = account.accountKey, status_key = statusKey)
        }
    }
}
