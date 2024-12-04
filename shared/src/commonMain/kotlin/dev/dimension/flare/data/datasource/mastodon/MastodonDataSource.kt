package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.cachedIn
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Mastodon
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
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.memoryPager
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.PostAccounts
import dev.dimension.flare.data.network.mastodon.api.model.PostList
import dev.dimension.flare.data.network.mastodon.api.model.PostPoll
import dev.dimension.flare.data.network.mastodon.api.model.PostReport
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.PostVote
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
class MastodonDataSource(
//    override val account: UiAccount.Mastodon,
    override val accountKey: MicroBlogKey,
    val credential: UiAccount.Mastodon.Credential,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    StatusEvent.Mastodon,
    ListDataSource {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val service by lazy {
        MastodonService(
            baseUrl = "https://${credential.instance}/",
            accessToken = credential.accessToken,
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
                    service,
                    database,
                    accountKey,
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
                PublicTimelineRemoteMediator(
                    service,
                    database,
                    accountKey,
                    pagingKey,
                    local = true,
                ),
        )

    fun bookmarkTimeline(
        pageSize: Int = 20,
        pagingKey: String = "bookmarked_$accountKey",
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
                BookmarkTimelineRemoteMediator(
                    service,
                    database,
                    accountKey,
                    pagingKey,
                ),
        )

    fun favouriteTimeline(
        pageSize: Int = 20,
        pagingKey: String = "favourite_$accountKey",
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
                FavouriteTimelineRemoteMediator(
                    service,
                    database,
                    accountKey,
                    pagingKey,
                ),
        )

    override fun listTimeline(
        listId: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = "list_${accountKey}_$listId",
            accountKey = accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                ListTimelineRemoteMediator(
                    listId,
                    service,
                    database,
                    accountKey,
                    "list_${accountKey}_$listId",
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
                    service,
                    database,
                    accountKey,
                    pagingKey,
                    local = false,
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
                            service,
                            database,
                            accountKey,
                            pagingKey,
                            onClearMarker = {
                                MemCacheable.update(notificationMarkerKey, 0)
                            },
                        )

                    NotificationFilter.Mention ->
                        MentionRemoteMediator(
                            service,
                            database,
                            accountKey,
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
                        .lookupUserByAcct("$name@$host")
                        ?.toDbUser(accountKey.host) ?: throw Exception("User not found")
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByHandleAndHost(name, host, PlatformType.Mastodon)
                    .distinctUntilChanged()
                    .mapNotNull { it?.render(accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiProfile> {
        val userKey = MicroBlogKey(id, accountKey.host)
        return Cacheable(
            fetchSource = {
                val user = service.lookupUser(id).toDbUser(accountKey.host)
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
            service.showFriendships(listOf(userKey.id)).first().toUi()
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
                    service,
                    database,
                    accountKey,
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
                    service,
                    database,
                    accountKey,
                    pagingKey,
                    statusOnly = false,
                ),
        )

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"
        return Cacheable(
            fetchSource = {
                val result =
                    service.lookupStatus(
                        statusKey.id,
                    )
                Mastodon.save(
                    database = database,
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    data = listOf(result),
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
                val emojis = service.emojis()
                database.emojiDao().insert(emojis.toDb(accountKey.host))
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
                        ).also {
                            progress(ComposeProgress(index + 1, maxProgress))
                        }
                }.mapNotNull {
                    it.id
                }
        service.post(
            Uuid.random().toString(),
            PostStatus(
                status = data.content,
                visibility =
                    when (data.visibility) {
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public -> Visibility.Public
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home -> Visibility.Unlisted
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers -> Visibility.Private
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified -> Visibility.Direct
                    },
                inReplyToID = inReplyToID,
                mediaIDS = mediaIds.takeIf { it.isNotEmpty() },
                sensitive = data.sensitive.takeIf { mediaIds.isNotEmpty() },
                spoilerText = data.spoilerText.takeIf { it?.isNotEmpty() == true && it.isNotBlank() },
                poll =
                    data.poll?.let { poll ->
                        PostPoll(
                            options = poll.options,
                            expiresIn = poll.expiredAfter,
                            multiple = poll.multiple,
                        )
                    },
            ),
        )
        progress(ComposeProgress(maxProgress, maxProgress))
    }

    override fun like(
        statusKey: MicroBlogKey,
        liked: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                favourited = !liked,
                                favouritesCount =
                                    if (liked) {
                                        it.data.favouritesCount?.minus(1)
                                    } else {
                                        it.data.favouritesCount?.plus(1)
                                    },
                            ),
                    )
                },
            )

            runCatching {
                if (liked) {
                    service.unfavourite(statusKey.id)
                } else {
                    service.favourite(statusKey.id)
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    favourited = liked,
                                    favouritesCount =
                                        if (!liked) {
                                            it.data.favouritesCount?.minus(1)
                                        } else {
                                            it.data.favouritesCount?.plus(1)
                                        },
                                ),
                        )
                    },
                )
            }.onSuccess { result ->
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(data = result)
                    },
                )
            }
        }
    }

    override fun reblog(
        statusKey: MicroBlogKey,
        reblogged: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                reblogged = !reblogged,
                                reblogsCount =
                                    if (reblogged) {
                                        it.data.reblogsCount?.minus(1)
                                    } else {
                                        it.data.reblogsCount?.plus(1)
                                    },
                            ),
                    )
                },
            )

            runCatching {
                if (reblogged) {
                    service.unreblog(statusKey.id)
                } else {
                    service.reblog(statusKey.id)
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    reblogged = reblogged,
                                    reblogsCount =
                                        if (!reblogged) {
                                            it.data.reblogsCount?.minus(1)
                                        } else {
                                            it.data.reblogsCount?.plus(1)
                                        },
                                ),
                        )
                    },
                )
            }.onSuccess { result ->
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        result.reblog?.let { StatusContent.Mastodon(it) } ?: it
                    },
                )
            }
        }
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        runCatching {
            service.delete(statusKey.id)
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

    override fun bookmark(
        statusKey: MicroBlogKey,
        bookmarked: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                bookmarked = !bookmarked,
                            ),
                    )
                },
            )

            runCatching {
                if (bookmarked) {
                    service.unbookmark(statusKey.id)
                } else {
                    service.bookmark(statusKey.id)
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    bookmarked = bookmarked,
                                ),
                        )
                    },
                )
            }.onSuccess { result ->
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(data = result)
                    },
                )
            }
        }
    }

    suspend fun report(
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
    ) {
        runCatching {
            service.report(
                PostReport(
                    accountId = userKey.id,
                    statusIds = statusKey?.let { listOf(it.id) },
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
            service.unfollow(userKey.id)
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
            service.follow(userKey.id)
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
            service.block(userKey.id)
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
        runCatching {
            service.unblock(userKey.id)
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
            service.muteUser(userKey.id)
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
            service.unmuteUser(userKey.id)
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

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                service = service,
                accountKey = accountKey,
                host = accountKey.host,
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
                SearchStatusPagingSource(
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
                service = service,
                accountKey = accountKey,
                query = query,
                host = accountKey.host,
            )
        }.flow.cachedIn(scope)

    fun searchFollowing(
        query: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                service = service,
                accountKey = accountKey,
                query = query,
                host = accountKey.host,
                following = true,
                resolve = false,
            )
        }.flow.cachedIn(scope)

    override fun composeConfig(statusKey: MicroBlogKey?): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(500),
            media = ComposeConfig.Media(4, true),
            poll = ComposeConfig.Poll(4),
            emoji = ComposeConfig.Emoji(emoji(), mergeTag = "mastodon@${accountKey.host}"),
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
            relation.hasPendingFollowRequestFromYou -> Unit // you can't cancel follow request on mastodon
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

    private val listKey: String
        get() = "allLists_$accountKey"

    override val myList: CacheData<ImmutableList<UiList>> =
        MemCacheable(
            key = listKey,
            fetchSource = {
                service
                    .lists()
                    .mapNotNull {
                        it.id?.let { it1 ->
                            it.render()
                        }
                    }.toImmutableList()
            },
        )

    suspend fun createList(title: String) {
        runCatching {
            service.createList(PostList(title = title))
        }.onSuccess { response ->
            if (response.id != null) {
                MemCacheable.updateWith<List<UiList>>(
                    key = listKey,
                ) {
                    it +
                        UiList(
                            id = response.id,
                            title = title,
                            platformType = PlatformType.Mastodon,
                        )
                }
            }
        }
    }

    override suspend fun deleteList(listId: String) {
        runCatching {
            service.deleteList(listId)
        }.onSuccess {
            MemCacheable.updateWith<List<UiList>>(
                key = listKey,
            ) {
                it.filter { list -> list.id != listId }
            }
        }
    }

    private suspend fun updateList(
        listId: String,
        title: String,
    ) {
        runCatching {
            service.updateList(listId, PostList(title = title))
        }.onSuccess {
            MemCacheable.updateWith<List<UiList>>(
                key = listKey,
            ) {
                it.map { list ->
                    if (list.id == listId) {
                        list.copy(title = title)
                    } else {
                        list
                    }
                }
            }
        }
    }

    override fun listInfo(listId: String): CacheData<UiList> =
        MemCacheable(
            key = "listInfo_$listId",
            fetchSource = {
                service.getList(listId).render()
            },
        )

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
                object : RemoteMediator<Int, UiUserV2>() {
                    override suspend fun load(
                        loadType: LoadType,
                        state: PagingState<Int, UiUserV2>,
                    ): MediatorResult {
                        try {
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
                                    .listMembers(listId, limit = pageSize, max_id = key)
                                    .body()
                                    ?.map {
                                        it.toDbUser(accountKey.host).render(accountKey)
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
                                endOfPaginationReached = result.isEmpty(),
                            )
                        } catch (e: Exception) {
                            return MediatorResult.Error(e)
                        }
                    }
                },
        )

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        runCatching {
            service.addMember(
                listId,
                PostAccounts(listOf(userKey.id)),
            )
            val user =
                service
                    .lookupUser(userKey.id)
                    .toDbUser(accountKey.host)
                    .render(accountKey)
            MemoryPagingSource.updateWith(
                key = listMemberKey(listId),
            ) {
                (listOf(user) + it)
                    .distinctBy {
                        it.key
                    }.toImmutableList()
            }
            val list = service.getList(listId)
            if (list.id != null) {
                MemCacheable.updateWith<List<UiList>>(
                    key = userListsKey(userKey),
                ) {
                    it + list.render()
                }
            }
        }
    }

    override suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        runCatching {
            service.removeMember(
                listId,
                PostAccounts(listOf(userKey.id)),
            )
            MemoryPagingSource.updateWith<UiUserV2>(
                key = listMemberKey(listId),
            ) {
                it
                    .filter { user -> user.key.id != userKey.id }
                    .toImmutableList()
            }
            MemCacheable.updateWith<List<UiList>>(
                key = userListsKey(userKey),
            ) {
                it
                    .filter { list -> list.id != listId }
            }
        }
    }

    override fun listMemberCache(listId: String): Flow<ImmutableList<UiUserV2>> =
        MemoryPagingSource.getFlow<UiUserV2>(listMemberKey(listId))

    private fun userListsKey(userKey: MicroBlogKey) = "userLists_${userKey.id}"

    override fun userLists(userKey: MicroBlogKey): MemCacheable<ImmutableList<UiList>> =
        MemCacheable(
            key = userListsKey(userKey),
        ) {
            service
                .accountLists(userKey.id)
                .body()
                ?.mapNotNull {
                    it.id?.let { _ ->
                        it.render()
                    }
                }.orEmpty()
                .toImmutableList()
        }

    override val supportedMetaData: ImmutableList<ListMetaDataType>
        get() = persistentListOf(ListMetaDataType.TITLE)

    override suspend fun createList(metaData: ListMetaData) {
        createList(metaData.title)
    }

    override suspend fun updateList(
        listId: String,
        metaData: ListMetaData,
    ) {
        updateList(listId, metaData.title)
    }

    private val notificationMarkerKey: String
        get() = "notificationBadgeCount_$accountKey"

    override fun notificationBadgeCount(): CacheData<Int> {
        return MemCacheable(
            key = notificationMarkerKey,
            fetchSource = {
                val marker = service.notificationMarkers().notifications?.lastReadID ?: return@MemCacheable 0
                val timeline = service.notification(min_id = marker)
                timeline.size
            },
        )
    }

    override fun vote(
        statusKey: MicroBlogKey,
        id: String,
        options: List<Int>,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = statusKey,
                accountKey = accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                poll =
                                    it.data.poll?.copy(
                                        voted = true,
                                        ownVotes = options,
                                        options =
                                            it.data.poll.options?.mapIndexed { index, option ->
                                                if (options.contains(index)) {
                                                    option.copy(votesCount = option.votesCount?.plus(1))
                                                } else {
                                                    option
                                                }
                                            } ?: emptyList(),
                                    ),
                            ),
                    )
                },
            )

            runCatching {
                service.vote(id = id, data = PostVote(choices = options.map { it.toString() }))
            }.onFailure {
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    poll =
                                        it.data.poll?.copy(
                                            voted = false,
                                            ownVotes = null,
                                            options =
                                                it.data.poll.options?.mapIndexed { index, option ->
                                                    if (options.contains(index)) {
                                                        option.copy(votesCount = option.votesCount?.minus(1))
                                                    } else {
                                                        option
                                                    }
                                                } ?: emptyList(),
                                        ),
                                ),
                        )
                    },
                )
            }.onSuccess { result ->
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    poll = result,
                                ),
                        )
                    },
                )
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
            MastodonFollowingPagingSource(
                service = service,
                host = accountKey.host,
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
            MastodonFansPagingSource(
                service = service,
                host = accountKey.host,
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
