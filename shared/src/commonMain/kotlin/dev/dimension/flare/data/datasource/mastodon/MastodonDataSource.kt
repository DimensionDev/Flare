package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.cachedIn
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import com.benasher44.uuid.uuid4
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Mastodon
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MastodonComposeData
import dev.dimension.flare.data.datasource.microblog.MemoryPagingSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
class MastodonDataSource(
    override val account: UiAccount.Mastodon,
) : MicroblogDataSource,
    KoinComponent,
    StatusEvent.Mastodon {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val service by lazy {
        MastodonService(
            baseUrl = "https://${account.credential.instance}/",
            accessToken = account.credential.accessToken,
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
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                HomeTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )

    fun localTimeline(
        pageSize: Int = 20,
        pagingKey: String = "local_${account.accountKey}",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                PublicTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                    local = true,
                ),
        )

    fun bookmarkTimeline(
        pageSize: Int = 20,
        pagingKey: String = "bookmarked_${account.accountKey}",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                BookmarkTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )

    fun favouriteTimeline(
        pageSize: Int = 20,
        pagingKey: String = "favourite_${account.accountKey}",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                FavouriteTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )

    fun listTimeline(
        listId: String,
        pageSize: Int = 20,
        pagingKey: String = "list_${account.accountKey}_$listId",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                ListTimelineRemoteMediator(
                    listId,
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )

    fun publicTimeline(
        pageSize: Int = 20,
        pagingKey: String = "public_${account.accountKey}",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                PublicTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
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
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forNotification = true),
            scope = scope,
            mediator =
                when (type) {
                    NotificationFilter.All ->
                        NotificationRemoteMediator(
                            service,
                            database,
                            account.accountKey,
                            pagingKey,
                        )

                    NotificationFilter.Mention ->
                        MentionRemoteMediator(
                            service,
                            database,
                            account.accountKey,
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
                        ?.toDbUser(account.accountKey.host) ?: throw Exception("User not found")
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
                    .findByHandleAndHost(name, host, PlatformType.Mastodon)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.render(account.accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiProfile> {
        val userKey = MicroBlogKey(id, account.accountKey.host)
        return Cacheable(
            fetchSource = {
                val user = service.lookupUser(id).toDbUser(account.accountKey.host)
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
                    .map { it.render(account.accountKey) }
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
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                UserTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
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
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                StatusDetailRemoteMediator(
                    statusKey,
                    service,
                    database,
                    account.accountKey,
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
                    accountKey = account.accountKey,
                    pagingKey = pagingKey,
                    data = listOf(result),
                )
            },
            cacheSource = {
                database.dbStatusQueries
                    .get(statusKey, account.accountKey)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .mapNotNull { it.content.render(account.accountKey, this) }
            },
        )
    }

    fun emoji() =
        Cacheable(
            fetchSource = {
                val emojis = service.emojis()
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
        require(data is MastodonComposeData)
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
            uuid4().toString(),
            PostStatus(
                status = data.content,
                visibility =
                    when (data.visibility) {
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public -> Visibility.Public
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home -> Visibility.Unlisted
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers -> Visibility.Private
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified -> Visibility.Direct
                    },
                inReplyToID = data.inReplyToID,
                mediaIDS = mediaIds.takeIf { it.isNotEmpty() },
                sensitive = data.sensitive.takeIf { mediaIds.isNotEmpty() },
                spoilerText = data.spoilerText.takeIf { it?.isNotEmpty() == true && it.isNotBlank() },
                poll =
                    data.poll?.let { poll ->
                        PostPoll(
                            options = poll.options,
                            expiresIn = poll.expiresIn,
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
                accountKey = account.accountKey,
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
                    accountKey = account.accountKey,
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
                    accountKey = account.accountKey,
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
                accountKey = account.accountKey,
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
                    accountKey = account.accountKey,
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
                    accountKey = account.accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(data = result)
                    },
                )
            }
        }
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        runCatching {
            service.delete(statusKey.id)
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

    override fun bookmark(
        statusKey: MicroBlogKey,
        bookmarked: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = statusKey,
                accountKey = account.accountKey,
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
                    accountKey = account.accountKey,
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
                    accountKey = account.accountKey,
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
                service,
                account.accountKey,
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

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forSearch = true),
            scope = scope,
            mediator =
                SearchStatusPagingSource(
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
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                service,
                account.accountKey,
                query,
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
                service,
                account.accountKey,
                query,
                following = true,
                resolve = false,
            )
        }.flow.cachedIn(scope)

    override fun composeConfig(statusKey: MicroBlogKey?): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(500),
            media = ComposeConfig.Media(4, true),
            poll = ComposeConfig.Poll(4),
            emoji = ComposeConfig.Emoji(emoji(), mergeTag = "mastodon@${account.accountKey.host}"),
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
        get() = "allLists_${account.accountKey}"

    fun allLists(): MemCacheable<List<UiList>> =
        MemCacheable(
            key = listKey,
        ) {
            service
                .lists()
                .mapNotNull {
                    it.id?.let { it1 ->
                        UiList(
                            id = it1,
                            title = it.title.orEmpty(),
                        )
                    }
                }
        }

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
                        )
                }
            }
        }
    }

    suspend fun deleteList(listId: String) {
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

    suspend fun updateList(
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

    fun listInfo(listId: String): CacheData<UiList> =
        MemCacheable(
            key = "listInfo_$listId",
            fetchSource = {
                service.getList(listId).let {
                    UiList(
                        id = it.id ?: "",
                        title = it.title.orEmpty(),
                    )
                }
            },
        )

    private fun listMemberKey(listId: String) = "listMembers_$listId"

    fun listMembers(
        listId: String,
        pageSize: Int = 20,
        scope: CoroutineScope,
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
                                        it.toDbUser(account.accountKey.host).render(account.accountKey)
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

    suspend fun addMember(
        listId: String,
        userId: String,
    ) {
        runCatching {
            service.addMember(
                listId,
                PostAccounts(listOf(userId)),
            )
            val user = service.lookupUser(userId).toDbUser(account.accountKey.host).render(account.accountKey)
            MemoryPagingSource.updateWith(
                key = listMemberKey(listId),
            ) {
                (listOf(user) + it)
                    .distinctBy {
                        it.key
                    }.toImmutableList()
            }
        }
    }

    suspend fun removeMember(
        listId: String,
        userId: String,
    ) {
        runCatching {
            service.removeMember(
                listId,
                PostAccounts(listOf(userId)),
            )
            MemoryPagingSource.updateWith<UiUserV2>(
                key = listMemberKey(listId),
            ) {
                it
                    .filter { user -> user.key.id != userId }
                    .toImmutableList()
            }
        }
    }

    fun listMemberCache(listId: String) = MemoryPagingSource.getFlow<UiUserV2>(listMemberKey(listId))
}
