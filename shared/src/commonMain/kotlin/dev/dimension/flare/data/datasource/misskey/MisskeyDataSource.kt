package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.cachedIn
import dev.dimension.flare.common.BaseRemoteMediator
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
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.datasource.microblog.ListMetaData
import dev.dimension.flare.data.datasource.microblog.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.MemoryPagingSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.ReactionDataSource
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.memoryPager
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.MuteCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequestPoll
import dev.dimension.flare.data.network.misskey.api.model.NotesPollsVoteRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesReactionsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsListRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsMembershipRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsPullRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsShowRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiList
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
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
internal class MisskeyDataSource(
    override val accountKey: MicroBlogKey,
    val credential: UiAccount.Misskey.Credential,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    StatusEvent.Misskey,
    ListDataSource,
    ReactionDataSource {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountRepository: AccountRepository by inject()
    private val service by lazy {
        dev.dimension.flare.data.network.misskey.MisskeyService(
            baseUrl = "https://${credential.host}/api/",
            token = credential.accessToken,
        )
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            accountKey,
            service,
            database,
        )

    fun localTimeline(
        pageSize: Int = 20,
        pagingKey: String = "local_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            accountRepository = accountRepository,
            mediator = localTimelineLoader(),
        )

    fun localTimelineLoader() =
        LocalTimelineRemoteMediator(
            accountKey,
            service,
            database,
        )

    fun publicTimeline(
        pageSize: Int = 20,
        pagingKey: String = "public_$accountKey",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            accountRepository = accountRepository,
            mediator = publicTimelineLoader(),
        )

    fun publicTimelineLoader() =
        PublicTimelineRemoteMediator(
            accountKey,
            service,
            database,
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
                            accountKey,
                            service,
                            database,
                        )

                    NotificationFilter.Mention ->
                        MentionTimelineRemoteMediator(
                            accountKey,
                            service,
                            database,
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
        mediaOnly: Boolean,
    ) = UserTimelineRemoteMediator(
        accountKey,
        service,
        userKey,
        database,
        onlyMedia = mediaOnly,
    )

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey,
            database,
            accountKey,
            service,
            statusOnly = false,
        )

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"
        return Cacheable(
            fetchSource = {
                val result =
                    service
                        .notesShow(
                            IPinRequest(noteId = statusKey.id),
                        )
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
                    .get(statusKey, AccountType.Specific(accountKey))
                    .distinctUntilChanged()
                    .mapNotNull { it?.content?.render(this) }
            },
        )
    }

    override fun emoji(): Cacheable<ImmutableMap<String, ImmutableList<UiEmoji>>> =
        Cacheable(
            fetchSource = {
                val emojis =
                    service
                        .emojis()

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
                    .mapNotNull {
                        it
                            ?.toUi()
                            ?.groupBy { it.category }
                            ?.map { it.key to it.value.toImmutableList() }
                            ?.toMap()
                            ?.toImmutableMap()
                    }
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
            tryRun {
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
        tryRun {
            service.notesDelete(
                IPinRequest(
                    noteId = statusKey.id,
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
                                        val current = it.data.reactions[reaction] ?: 0
                                        if (current > 1) {
                                            put(reaction, current - 1)
                                        } else {
                                            remove(reaction)
                                        }
                                    } else {
                                        put(reaction, it.data.reactions[reaction]?.plus(1) ?: 1)
                                    }
                                },
                        ),
                )
            }
            tryRun {
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
                                            val current = it.data.reactions[reaction] ?: 0
                                            if (current > 1) {
                                                put(reaction, current - 1)
                                            } else {
                                                remove(reaction)
                                            }
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
        comment: String,
    ) {
        tryRun {
            val status =
                statusKey
                    ?.let {
                        service
                            .notesShow(
                                IPinRequest(
                                    noteId = it.id,
                                ),
                            )
                    }

            val actualComment =
                buildString {
                    if (status != null) {
                        if (status.uri != null) {
                            appendLine("Note: ${status.uri}")
                        }
                        append("Local Note: https://${userKey.host}/note/${status.id}")
                        append("-----")
                    }
                    append(comment)
                }

            service.usersReportAbuse(
                dev.dimension.flare.data.network.misskey.api.model.UsersReportAbuseRequest(
                    userId = userKey.id,
                    comment = actualComment,
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
        tryRun {
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
        tryRun {
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
        tryRun {
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
        tryRun {
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
        tryRun {
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
        tryRun {
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

    override fun discoverStatuses() =
        DiscoverStatusRemoteMediator(
            service,
            database,
            accountKey,
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
            tryRun {
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

    override fun favourite(
        statusKey: MicroBlogKey,
        favourited: Boolean,
    ) {
        coroutineScope.launch {
            tryRun {
                if (favourited) {
                    service.notesFavoritesDelete(
                        IPinRequest(
                            noteId = statusKey.id,
                        ),
                    )
                } else {
                    service.notesFavoritesCreate(
                        IPinRequest(
                            noteId = statusKey.id,
                        ),
                    )
                }
            }
        }
    }

    override fun favouriteState(statusKey: MicroBlogKey): Flow<Boolean> =
        flow {
            tryRun {
                service.notesState(
                    IPinRequest(
                        noteId = statusKey.id,
                    ),
                )
            }.fold(
                onSuccess = {
                    emit(it?.isFavorited == true)
                },
                onFailure = {
                    emit(false)
                },
            )
        }

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
                        accountKey = accountKey,
                        service = service,
                        userKey = userKey,
                        database = database,
                        withPinned = true,
                    ),
            ),
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.StatusWithReplies,
                loader =
                    UserTimelineRemoteMediator(
                        service = service,
                        accountKey = accountKey,
                        database = database,
                        userKey = userKey,
                        withReplies = true,
                    ),
            ),
            ProfileTab.Media,
        ).toPersistentList()

    fun favouriteTimeline(
        pageSize: Int = 20,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            accountRepository = accountRepository,
            mediator = favouriteTimelineLoader(),
        )

    fun favouriteTimelineLoader() =
        FavouriteTimelineRemoteMediator(
            service = service,
            database = database,
            accountKey = accountKey,
        )

    private val listKey: String
        get() = "allLists_$accountKey"

    override fun myList(scope: CoroutineScope): Flow<PagingData<UiList>> =
        memoryPager(
            pageSize = 20,
            pagingKey = listKey,
            scope = scope,
            mediator =
                object : BaseRemoteMediator<Int, UiList>() {
                    override suspend fun doLoad(
                        loadType: LoadType,
                        state: PagingState<Int, UiList>,
                    ): MediatorResult {
                        if (loadType == LoadType.PREPEND) {
                            return MediatorResult.Success(endOfPaginationReached = true)
                        }
                        val result =
                            service
                                .usersListsList(
                                    UsersListsListRequest(),
                                ).orEmpty()
                                .map {
                                    it.render()
                                }.toImmutableList()

                        MemoryPagingSource.update<UiList>(
                            key = listKey,
                            value = result.toImmutableList(),
                        )

                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }
                },
        )

    override suspend fun createList(metaData: ListMetaData) {
        tryRun {
            service
                .usersListsCreate(
                    UsersListsCreateRequest(
                        name = metaData.title,
                    ),
                )
        }.onSuccess { response ->
            if (response?.id != null) {
                MemoryPagingSource.updateWith<UiList>(
                    key = listKey,
                ) {
                    it
                        .plus(
                            UiList(
                                id = response.id,
                                title = metaData.title,
                                platformType = PlatformType.Mastodon,
                            ),
                        ).toImmutableList()
                }
            }
        }
    }

    override suspend fun deleteList(listId: String) {
        tryRun {
            service.usersListsDelete(
                UsersListsDeleteRequest(listId = listId),
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
            service.usersListsUpdate(
                UsersListsUpdateRequest(
                    listId = listId,
                    name = metaData.title,
                ),
            )
        }.onSuccess {
            MemoryPagingSource.updateWith<UiList>(
                key = listKey,
            ) {
                it
                    .map { list ->
                        if (list.id == listId) {
                            list.copy(title = metaData.title)
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
                service
                    .usersListsShow(
                        UsersListsShowRequest(
                            listId = listId,
                        ),
                    )?.render() ?: throw Exception("List not found")
            },
        )

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
                    override suspend fun doLoad(
                        loadType: LoadType,
                        state: PagingState<Int, UiUserV2>,
                    ): MediatorResult {
                        if (loadType == LoadType.PREPEND) {
                            return MediatorResult.Success(endOfPaginationReached = true)
                        }
                        val key =
                            if (loadType == LoadType.REFRESH) {
                                null
                            } else {
                                MemoryPagingSource
                                    .get<UiUserV2>(key = listMemberKey(listId))
                                    ?.lastOrNull()
                                    ?.key
                                    ?.id
                            }
                        val result =
                            service
                                .usersListsGetMemberships(
                                    UsersListsMembershipRequest(
                                        listId = listId,
                                        untilId = key,
                                        limit = state.config.pageSize,
                                    ),
                                ).orEmpty()
                                .map {
                                    it.user.render(accountKey)
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

    private fun listMemberKey(listId: String) = "listMembers_$listId"

    private fun userListsKey(userKey: MicroBlogKey) = "userLists_${userKey.id}"

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        tryRun {
            service.usersListsPush(
                UsersListsPullRequest(
                    listId = listId,
                    userId = userKey.id,
                ),
            )
            val user =
                service
                    .usersShow(
                        UsersShowRequest(
                            userId = userKey.id,
                        ),
                    )?.toDbUser(accountKey.host)
                    ?.render(accountKey)
            MemoryPagingSource.updateWith(
                key = listMemberKey(listId),
            ) {
                (listOfNotNull(user) + it)
                    .distinctBy {
                        it.key
                    }.toImmutableList()
            }
            val list =
                service
                    .usersListsShow(
                        UsersListsShowRequest(
                            listId = listId,
                        ),
                    )
            if (list?.id != null) {
                MemCacheable.updateWith<ImmutableList<UiList>>(
                    key = userListsKey(userKey),
                ) {
                    it
                        .plus(list.render())
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
            service.usersListsPull(
                UsersListsPullRequest(
                    listId = listId,
                    userId = userKey.id,
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

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            listId,
            service,
            database,
            accountKey,
        )

    override fun listMemberCache(listId: String): Flow<ImmutableList<UiUserV2>> =
        MemoryPagingSource.getFlow<UiUserV2>(listMemberKey(listId))

    override fun userLists(userKey: MicroBlogKey): MemCacheable<ImmutableList<UiList>> =
        MemCacheable(
            key = userListsKey(userKey),
        ) {
            service
                .usersListsList(
                    UsersListsListRequest(),
                ).orEmpty()
                .filter {
                    it.userIds?.contains(userKey.id) == true
                }.map {
                    it.render()
                }.toImmutableList()
        }

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf(ListMetaDataType.TITLE)

    override fun acceptFollowRequest(
        userKey: MicroBlogKey,
        notificationStatusKey: MicroBlogKey,
    ) {
        coroutineScope.launch {
            tryRun {
                MemCacheable.updateWith<UiRelation>(
                    key = relationKeyWithUserKey(userKey),
                ) {
                    it.copy(
                        hasPendingFollowRequestToYou = false,
                        isFans = true,
                    )
                }
                service.followingRequestsAccept(
                    adminAccountsDeleteRequest =
                        AdminAccountsDeleteRequest(
                            userId = userKey.id,
                        ),
                )
            }.onFailure {
                MemCacheable.updateWith<UiRelation>(
                    key = relationKeyWithUserKey(userKey),
                ) {
                    it.copy(
                        hasPendingFollowRequestToYou = true,
                        isFans = false,
                    )
                }
            }.onSuccess {
                database.pagingTimelineDao().deleteStatus(
                    accountKey = accountKey,
                    statusKey = notificationStatusKey,
                )
            }
        }
    }

    override fun rejectFollowRequest(
        userKey: MicroBlogKey,
        notificationStatusKey: MicroBlogKey,
    ) {
        coroutineScope.launch {
            tryRun {
                MemCacheable.updateWith<UiRelation>(
                    key = relationKeyWithUserKey(userKey),
                ) {
                    it.copy(
                        hasPendingFollowRequestToYou = false,
                        isFans = false,
                    )
                }
                service.followingRequestsReject(
                    adminAccountsDeleteRequest =
                        AdminAccountsDeleteRequest(
                            userId = userKey.id,
                        ),
                )
            }.onFailure {
                MemCacheable.updateWith<UiRelation>(
                    key = relationKeyWithUserKey(userKey),
                ) {
                    it.copy(
                        hasPendingFollowRequestToYou = true,
                        isFans = false,
                    )
                }
            }.onSuccess {
                database.pagingTimelineDao().deleteStatus(
                    accountKey = accountKey,
                    statusKey = notificationStatusKey,
                )
            }
        }
    }

    fun antennasList(
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiList>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            AntennasListPagingSource(
                service = service,
            )
        }.flow.cachedIn(scope)

    fun antennasTimeline(
        id: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            database = database,
            scope = scope,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            accountRepository = accountRepository,
            mediator = antennasTimelineLoader(id),
        )

    fun antennasTimelineLoader(id: String) =
        AntennasTimelineRemoteMediator(
            service = service,
            database = database,
            accountKey = accountKey,
            id = id,
        )
}
