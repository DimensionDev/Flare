package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.MuteCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequestPoll
import dev.dimension.flare.data.network.misskey.api.model.NotesPollsVoteRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesReactionsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.toUi
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
class MisskeyDataSource(
    override val accountKey: MicroBlogKey,
    val credential: UiAccount.Misskey.Credential,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    StatusEvent.Misskey {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val service by lazy {
        dev.dimension.flare.data.network.misskey.MisskeyService(
            baseUrl = "https://${credential.host}/api/",
            token = credential.accessToken,
        )
    }

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                HomeTimelineRemoteMediator(
                    accountKey,
                    service,
                    database,
                    pagingKey,
                ),
        )

    fun localTimeline(
        pageSize: Int = 20,
        pagingKey: String = "local_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                LocalTimelineRemoteMediator(
                    accountKey,
                    service,
                    database,
                    pagingKey,
                ),
        )

    fun publicTimeline(
        pageSize: Int = 20,
        pagingKey: String = "public_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                PublicTimelineRemoteMediator(
                    accountKey,
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
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forNotification = true),
            scope = scope,
            mediator =
                when (type) {
                    NotificationFilter.All ->
                        NotificationRemoteMediator(
                            accountKey,
                            service,
                            database,
                            pagingKey,
                        )

                    NotificationFilter.Mention ->
                        MentionTimelineRemoteMediator(
                            accountKey,
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

    override fun userByAcct(acct: String): CacheData<UiUserV2> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    service
                        .usersShow(UsersShowRequest(username = name, host = host))
                        .body()
                        ?.toDbUser(accountKey.host)
                        ?: throw Exception("User not found")
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByHandleAndHost(name, host, PlatformType.Misskey)
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
                        .usersShow(UsersShowRequest(userId = id))
                        .body()
                        ?.toDbUser(accountKey.host)
                        ?: throw Exception("User not found")
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
            val user =
                service
                    .usersShow(UsersShowRequest(userId = userKey.id))
                    .body()!!
            UiRelation(
                following = user.isFollowing ?: false,
                isFans = user.isFollowed ?: false,
                blocking = user.isBlocking ?: false,
                muted = user.isMuted ?: false,
                hasPendingFollowRequestFromYou = user.hasPendingFollowRequestFromYou ?: false,
                hasPendingFollowRequestToYou = user.hasPendingFollowRequestToYou ?: false,
            )
        }.toUi()

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                UserTimelineRemoteMediator(
                    accountKey,
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
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                StatusDetailRemoteMediator(
                    statusKey,
                    database,
                    accountKey,
                    service,
                    pagingKey,
                    statusOnly = false,
                ),
        )

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
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
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    data = listOfNotNull(result),
                )
            },
            cacheSource = {
                database
                    .statusDao()
                    .get(statusKey, accountKey)
                    .distinctUntilChanged()
                    .mapNotNull { it?.content?.render(accountKey, this) }
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
                database.emojiDao().insert(
                    emojis.toDb(accountKey.host),
                )
            },
            cacheSource = {
                database
                    .emojiDao()
                    .get(accountKey.host)
                    .distinctUntilChanged()
                    .mapNotNull { it?.toUi()?.toImmutableList() }
            },
        )

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        val renoteId =
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
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public -> "public"
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home -> "home"
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers -> "followers"
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified -> "specified"
                    },
                renoteId = renoteId,
                replyId = inReplyToID,
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

    override fun renote(statusKey: MicroBlogKey) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Misskey>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                renoteCount = it.data.renoteCount + 1,
                            ),
                    )
                },
            )
            runCatching {
                service.notesCreate(
                    NotesCreateRequest(
                        renoteId = statusKey.id,
                    ),
                )
            }.onFailure {
                updateStatusUseCase<StatusContent.Misskey>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    renoteCount = it.data.renoteCount - 1,
                                ),
                        )
                    },
                )
            }
        }
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        runCatching {
            service.notesDelete(
                IPinRequest(
                    noteId = statusKey.id,
                ),
            )

            // delete status from cache
            database.statusDao().delete(
                statusKey = statusKey,
                accountKey = accountKey,
            )
            database.pagingTimelineDao().deleteStatus(
                accountKey = accountKey,
                statusKey = statusKey,
            )
        }
    }

    override fun react(
        statusKey: MicroBlogKey,
        hasReacted: Boolean,
        reaction: String,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Misskey>(
                statusKey,
                accountKey,
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
                            noteId = statusKey.id,
                        ),
                    )
                } else {
                    service.notesReactionsCreate(
                        NotesReactionsCreateRequest(
                            noteId = statusKey.id,
                            reaction = reaction,
                        ),
                    )
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Misskey>(
                    statusKey,
                    accountKey,
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
        MemCacheable.updateWith<UiRelation>(
            key = key,
        ) {
            it.copy(
                following = false,
            )
        }
        runCatching {
            service.followingDelete(AdminAccountsDeleteRequest(userId = userKey.id))
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
        runCatching {
            service.followingCreate(AdminAccountsDeleteRequest(userId = userKey.id))
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
        runCatching {
            service.blockingCreate(AdminAccountsDeleteRequest(userId = userKey.id))
        }.onFailure {
            it.printStackTrace()
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
        runCatching {
            service.blockingDelete(AdminAccountsDeleteRequest(userId = userKey.id))
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
        runCatching {
            service.muteCreate(MuteCreateRequest(userId = userKey.id))
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
        runCatching {
            service.muteDelete(AdminAccountsDeleteRequest(userId = userKey.id))
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

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forSearch = true),
            scope = scope,
            mediator =
                SearchStatusRemoteMediator(
                    service,
                    database,
                    accountKey,
                    pagingKey,
                    query,
                ),
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

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                DiscoverStatusRemoteMediator(
                    service,
                    database,
                    accountKey,
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
            emoji = ComposeConfig.Emoji(emoji(), "misskey@${accountKey.host}"),
            contentWarning = ComposeConfig.ContentWarning,
            visibility = ComposeConfig.Visibility,
        )

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        when {
            relation.following -> unfollow(userKey)
            relation.blocking -> unblock(userKey)
            relation.hasPendingFollowRequestFromYou -> Unit // TODO: cancel follow request
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

    override fun vote(
        statusKey: MicroBlogKey,
        options: List<Int>,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Misskey>(
                statusKey,
                accountKey,
                database,
            ) {
                it.copy(
                    data =
                        it.data.copy(
                            poll =
                                it.data.poll?.copy(
                                    choices =
                                        it.data.poll.choices.mapIndexed { index, choice ->
                                            if (options.contains(index)) {
                                                choice.copy(votes = choice.votes + 1, isVoted = true)
                                            } else {
                                                choice
                                            }
                                        },
                                ),
                        ),
                )
            }
            runCatching {
                options.forEach {
                    service.notesPollsVote(
                        notesPollsVoteRequest =
                            NotesPollsVoteRequest(
                                noteId = statusKey.id,
                                choice = it,
                            ),
                    )
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Misskey>(
                    statusKey,
                    accountKey,
                    database,
                ) {
                    it.copy(
                        data =
                            it.data.copy(
                                poll =
                                    it.data.poll?.copy(
                                        choices =
                                            it.data.poll.choices.mapIndexed { index, choice ->
                                                if (options.contains(index)) {
                                                    choice.copy(votes = choice.votes - 1, isVoted = false)
                                                } else {
                                                    choice
                                                }
                                            },
                                    ),
                            ),
                    )
                }
            }
        }
    }

    override fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
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
        pagingKey: String,
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

    override fun profileTabs(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pagingSize: Int,
    ): ImmutableList<ProfileTab> =
        listOfNotNull(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                flow = userTimeline(userKey, scope, pagingSize),
            ),
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.StatusWithReplies,
                flow =
                    timelinePager(
                        pageSize = pagingSize,
                        pagingKey = "user_timeline_replies_$userKey",
                        accountKey = accountKey,
                        database = database,
                        filterFlow = localFilterRepository.getFlow(forTimeline = true),
                        scope = scope,
                        mediator =
                            UserTimelineRemoteMediator(
                                service = service,
                                accountKey = accountKey,
                                database = database,
                                userKey = userKey,
                                pagingKey = "user_timeline_replies_$userKey",
                                withReplies = true,
                            ),
                    ),
            ),
            ProfileTab.Media,
        ).toPersistentList()
}
