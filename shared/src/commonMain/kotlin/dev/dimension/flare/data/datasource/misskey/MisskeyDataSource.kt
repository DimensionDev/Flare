package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.MisskeyComposeData
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.MuteCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequestPoll
import dev.dimension.flare.data.network.misskey.api.model.NotesReactionsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
class MisskeyDataSource(
    override val account: UiAccount.Misskey,
) : MicroblogDataSource,
    KoinComponent {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val service by lazy {
        dev.dimension.flare.data.network.misskey.MisskeyService(
            baseUrl = "https://${account.credential.host}/api/",
            token = account.credential.accessToken,
        )
    }

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
                    account,
                    service,
                    database,
                    pagingKey,
                ),
        )

    fun localTimeline(
        pageSize: Int = 20,
        pagingKey: String = "local_${account.accountKey}",
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
                LocalTimelineRemoteMediator(
                    account,
                    service,
                    database,
                    pagingKey,
                ),
        )

    fun publicTimeline(
        pageSize: Int = 20,
        pagingKey: String = "public_${account.accountKey}",
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
                PublicTimelineRemoteMediator(
                    account,
                    service,
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
                            account,
                            service,
                            database,
                            pagingKey,
                        )

                    NotificationFilter.Mention ->
                        MentionTimelineRemoteMediator(
                            account,
                            service,
                            database,
                            pagingKey,
                        )

                    else -> throw IllegalStateException("Unsupported notification type")
                },
        )

    override val supportedNotificationFilter: List<NotificationFilter>
        get() =
            listOf(
                NotificationFilter.All,
                NotificationFilter.Mention,
            )

    override fun userByAcct(acct: String): CacheData<UiUser> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    service
                        .usersShow(UsersShowRequest(username = name, host = host))
                        .body()
                        ?.toDbUser(account.accountKey.host)
                        ?: throw Exception("User not found")
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
                database.dbUserQueries
                    .findByHandleAndHost(name, host, PlatformType.Misskey)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi(account.accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
        val userKey = MicroBlogKey(id, account.accountKey.host)
        return Cacheable(
            fetchSource = {
                val user =
                    service
                        .usersShow(UsersShowRequest(userId = id))
                        .body()
                        ?.toDbUser(account.accountKey.host)
                        ?: throw Exception("User not found")
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
                database.dbUserQueries
                    .findByKey(userKey)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi(account.accountKey) }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> =
        MemCacheable<UiRelation>(
            relationKeyWithUserKey(userKey),
        ) {
            service
                .usersShow(UsersShowRequest(userId = userKey.id))
                .body()!!
                .toDbUser(account.accountKey.host)
                .toUi(account.accountKey)
                .let {
                    if (it is UiUser.Misskey) {
                        it.relation
                    } else {
                        throw IllegalStateException("User is not a Misskey user")
                    }
                }
        }.toUi()

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
                    account,
                    service,
                    userKey,
                    database,
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
                    database,
                    account,
                    service,
                    pagingKey,
                    statusOnly = false,
                ),
        )

    override fun status(statusKey: MicroBlogKey): CacheData<UiStatus> {
        val pagingKey = "status_only_$statusKey"
        return Cacheable(
            fetchSource = {
                val result =
                    service
                        .notesShow(
                            IPinRequest(noteId = statusKey.id),
                        ).body()
                Misskey.save(
                    database = database,
                    accountKey = account.accountKey,
                    pagingKey = pagingKey,
                    data = listOfNotNull(result),
                )
            },
            cacheSource = {
                database.dbStatusQueries
                    .get(statusKey, account.accountKey)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .mapNotNull { it.content.toUi(account.accountKey) }
            },
        )
    }

    fun emoji() =
        Cacheable(
            fetchSource = {
                val emojis =
                    service
                        .emojis()
                        .body()
                        ?.emojis
                        .orEmpty()
                        .toImmutableList()
                database.dbEmojiQueries.insert(
                    account.accountKey.host,
                    emojis.toDb(account.accountKey.host).content,
                )
            },
            cacheSource = {
                database.dbEmojiQueries
                    .get(account.accountKey.host)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi().toImmutableList() }
            },
        )

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        require(data is MisskeyComposeData)
        val maxProgress = data.medias.size + 1
        val mediaIds =
            data.medias
                .mapIndexed { index, item ->
                    service
                        .upload(
                            item.readBytes(),
                            name = item.name ?: "unknown",
                            sensitive = data.sensitive,
                        ).also {
                            progress(ComposeProgress(index + 1, maxProgress))
                        }
                }.mapNotNull {
                    it?.id
                }
        service.notesCreate(
            NotesCreateRequest(
                text = data.content,
                visibility =
                    when (data.visibility) {
                        UiStatus.Misskey.Visibility.Public -> "public"
                        UiStatus.Misskey.Visibility.Home -> "home"
                        UiStatus.Misskey.Visibility.Followers -> "followers"
                        UiStatus.Misskey.Visibility.Specified -> "specified"
                    },
                renoteId = data.renoteId,
                replyId = data.inReplyToID,
                fileIds = mediaIds.takeIf { it.isNotEmpty() },
                cw = data.spoilerText.takeIf { it?.isNotEmpty() == true && it.isNotBlank() },
                poll =
                    data.poll?.let { poll ->
                        NotesCreateRequestPoll(
                            choices = poll.options.toSet(),
                            expiredAfter = poll.expiredAfter.toInt(),
                            multiple = poll.multiple,
                        )
                    },
                localOnly = data.localOnly,
            ),
        )
        progress(ComposeProgress(maxProgress, maxProgress))
    }

    suspend fun renote(status: UiStatus.Misskey) {
        service.notesRenotes(
            NotesChildrenRequest(
                noteId = status.statusKey.id,
            ),
        )
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        runCatching {
            service.notesDelete(
                IPinRequest(
                    noteId = statusKey.id,
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

    suspend fun react(
        status: UiStatus.Misskey,
        reaction: String,
    ) {
        val hasReacted = status.reaction.myReaction != null
        updateStatusUseCase<StatusContent.Misskey>(
            status.statusKey,
            account.accountKey,
            database,
        ) {
            it.copy(
                data =
                    it.data.copy(
                        myReaction = if (hasReacted) null else reaction,
                        reactions =
                            it.data.reactions.toMutableMap().apply {
                                if (hasReacted) {
                                    remove(reaction)
                                } else {
                                    put(reaction, it.data.reactions[reaction]?.plus(1) ?: 1)
                                }
                            },
                    ),
            )
        }
        runCatching {
            if (hasReacted) {
                service.notesReactionsDelete(
                    IPinRequest(
                        noteId = status.statusKey.id,
                    ),
                )
            } else {
                service.notesReactionsCreate(
                    NotesReactionsCreateRequest(
                        noteId = status.statusKey.id,
                        reaction = reaction,
                    ),
                )
            }
        }.onFailure {
            updateStatusUseCase<StatusContent.Misskey>(
                status.statusKey,
                account.accountKey,
                database,
            ) {
                it.copy(
                    data =
                        it.data.copy(
                            myReaction = if (hasReacted) reaction else null,
                            reactions =
                                it.data.reactions.toMutableMap().apply {
                                    if (hasReacted) {
                                        put(reaction, it.data.reactions[reaction]?.plus(1) ?: 1)
                                    } else {
                                        remove(reaction)
                                    }
                                },
                        ),
                )
            }
        }
    }

    suspend fun report(
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
    ) {
        runCatching {
            val comment =
                statusKey
                    ?.let {
                        service
                            .notesShow(
                                IPinRequest(
                                    noteId = it.id,
                                ),
                            ).body()
                            ?.url
                    }?.let {
                        "Note: $it"
                    }
            service.usersReportAbuse(
                dev.dimension.flare.data.network.misskey.api.model.UsersReportAbuseRequest(
                    userId = userKey.id,
                    comment = comment ?: "",
                ),
            )
        }
    }

    suspend fun unfollow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.Misskey>(
            key = key,
        ) {
            it.copy(
                following = false,
            )
        }
        runCatching {
            service.followingDelete(AdminAccountsDeleteRequest(userId = userKey.id))
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Misskey>(
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
        MemCacheable.updateWith<UiRelation.Misskey>(
            key = key,
        ) {
            it.copy(
                following = true,
            )
        }
        runCatching {
            service.followingCreate(AdminAccountsDeleteRequest(userId = userKey.id))
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Misskey>(
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
        MemCacheable.updateWith<UiRelation.Misskey>(
            key = key,
        ) {
            it.copy(
                blocking = true,
            )
        }
        runCatching {
            service.blockingCreate(AdminAccountsDeleteRequest(userId = userKey.id))
        }.onFailure {
            it.printStackTrace()
            MemCacheable.updateWith<UiRelation.Misskey>(
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
        MemCacheable.updateWith<UiRelation.Misskey>(
            key = key,
        ) {
            it.copy(
                blocking = false,
            )
        }
        runCatching {
            service.blockingDelete(AdminAccountsDeleteRequest(userId = userKey.id))
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Misskey>(
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
        MemCacheable.updateWith<UiRelation.Misskey>(
            key = key,
        ) {
            it.copy(
                muted = true,
            )
        }
        runCatching {
            service.muteCreate(MuteCreateRequest(userId = userKey.id))
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Misskey>(
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
        MemCacheable.updateWith<UiRelation.Misskey>(
            key = key,
        ) {
            it.copy(
                muted = false,
            )
        }
        runCatching {
            service.muteDelete(AdminAccountsDeleteRequest(userId = userKey.id))
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Misskey>(
                key = key,
            ) {
                it.copy(
                    muted = true,
                )
            }
        }
    }

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        timelinePager(
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

    override fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUser>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                service,
                account.accountKey,
                query,
            )
        }.flow.cachedIn(scope)

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUser>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                service,
                account.accountKey,
            )
        }.flow

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
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
                DiscoverStatusRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendHashtagPagingSource(
                service,
            )
        }.flow

    override fun composeConfig(statusKey: MicroBlogKey?): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(500),
            media = ComposeConfig.Media(4, true),
            poll = ComposeConfig.Poll(4),
            emoji = ComposeConfig.Emoji(emoji(), "misskey@${account.accountKey.host}"),
            contentWarning = ComposeConfig.ContentWarning,
            visibility = ComposeConfig.Visibility,
        )

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        require(relation is UiRelation.Misskey)
        when {
            relation.following -> unfollow(userKey)
            relation.blocking -> unblock(userKey)
            relation.hasPendingFollowRequestFromYou -> Unit // TODO: cancel follow request
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
                    require(relation is UiRelation.Misskey)
                    if (relation.muted) {
                        unmute(userKey)
                    } else {
                        mute(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean {
                    require(relation is UiRelation.Misskey)
                    return relation.muted
                }
            },
            object : ProfileAction.Block {
                override suspend fun invoke(
                    userKey: MicroBlogKey,
                    relation: UiRelation,
                ) {
                    require(relation is UiRelation.Misskey)
                    if (relation.blocking) {
                        unblock(userKey)
                    } else {
                        block(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean {
                    require(relation is UiRelation.Misskey)
                    return relation.blocking
                }
            },
        )
    }
}
