package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
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
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.PostPoll
import dev.dimension.flare.data.network.mastodon.api.model.PostReport
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
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
class MastodonDataSource(
    override val account: UiAccount.Mastodon,
) : MicroblogDataSource,
    KoinComponent {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
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
    ): Flow<PagingData<UiStatus>> =
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
    ): Flow<PagingData<UiStatus>> =
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

    override fun userByAcct(acct: String): CacheData<UiUser> {
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
                    .map { it.toUi(account.accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
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
                    .map { it.toUi(account.accountKey) }
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
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                    statusOnly = false,
                ),
        )

    override fun status(statusKey: MicroBlogKey): CacheData<UiStatus> {
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
                    .mapNotNull { it.content.toUi(account.accountKey) }
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
                        UiStatus.Mastodon.Visibility.Public -> Visibility.Public
                        UiStatus.Mastodon.Visibility.Unlisted -> Visibility.Unlisted
                        UiStatus.Mastodon.Visibility.Private -> Visibility.Private
                        UiStatus.Mastodon.Visibility.Direct -> Visibility.Direct
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

    suspend fun like(status: UiStatus.Mastodon) {
        updateStatusUseCase<StatusContent.Mastodon>(
            statusKey = status.statusKey,
            accountKey = status.accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            favourited = !status.reaction.liked,
                            favouritesCount =
                                if (status.reaction.liked) {
                                    it.data.favouritesCount?.minus(1)
                                } else {
                                    it.data.favouritesCount?.plus(1)
                                },
                        ),
                )
            },
        )

        runCatching {
            if (status.reaction.liked) {
                service.unfavourite(status.statusKey.id)
            } else {
                service.favourite(status.statusKey.id)
            }
        }.onFailure {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(data = status.raw)
                },
            )
        }.onSuccess { result ->
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(data = result)
                },
            )
        }
    }

    suspend fun reblog(status: UiStatus.Mastodon) {
        updateStatusUseCase<StatusContent.Mastodon>(
            statusKey = status.statusKey,
            accountKey = status.accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            reblogged = !status.reaction.reblogged,
                            reblogsCount =
                                if (status.reaction.reblogged) {
                                    it.data.reblogsCount?.minus(1)
                                } else {
                                    it.data.reblogsCount?.plus(1)
                                },
                        ),
                )
            },
        )

        runCatching {
            if (status.reaction.reblogged) {
                service.unreblog(status.statusKey.id)
            } else {
                service.reblog(status.statusKey.id)
            }
        }.onFailure {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(data = status.raw)
                },
            )
        }.onSuccess { result ->
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(data = result)
                },
            )
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

    suspend fun bookmark(status: UiStatus.Mastodon) {
        updateStatusUseCase<StatusContent.Mastodon>(
            statusKey = status.statusKey,
            accountKey = status.accountKey,
            cacheDatabase = database,
            update = {
                it.copy(
                    data =
                        it.data.copy(
                            bookmarked = !status.reaction.bookmarked,
                        ),
                )
            },
        )

        runCatching {
            if (status.reaction.bookmarked) {
                service.unbookmark(status.statusKey.id)
            } else {
                service.bookmark(status.statusKey.id)
            }
        }.onFailure {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(data = status.raw)
                },
            )
        }.onSuccess { result ->
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(data = result)
                },
            )
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
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                following = false,
            )
        }
        runCatching {
            service.unfollow(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
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
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                following = true,
            )
        }
        runCatching {
            service.follow(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
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
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                blocking = true,
            )
        }
        runCatching {
            service.block(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
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
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                blocking = false,
            )
        }
        runCatching {
            service.unblock(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
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
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                muting = true,
            )
        }
        runCatching {
            service.muteUser(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
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
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                muting = false,
            )
        }
        runCatching {
            service.unmuteUser(userKey.id)
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
                key = key,
            ) {
                it.copy(
                    muting = true,
                )
            }
        }
    }

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUser>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                service,
                account.accountKey.host,
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
    ): Flow<PagingData<UiUser>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                service,
                account.accountKey.host,
                query,
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
        require(relation is UiRelation.Mastodon)
        when {
            relation.following -> unfollow(userKey)
            relation.blocking -> unblock(userKey)
            relation.requested -> Unit // you can't cancel follow request on mastodon
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
                    require(relation is UiRelation.Mastodon)
                    if (relation.muting) {
                        unmute(userKey)
                    } else {
                        mute(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean {
                    require(relation is UiRelation.Mastodon)
                    return relation.muting
                }
            },
            object : ProfileAction.Block {
                override suspend fun invoke(
                    userKey: MicroBlogKey,
                    relation: UiRelation,
                ) {
                    require(relation is UiRelation.Mastodon)
                    if (relation.blocking) {
                        unblock(userKey)
                    } else {
                        block(userKey)
                    }
                }

                override fun relationState(relation: UiRelation): Boolean {
                    require(relation is UiRelation.Mastodon)
                    return relation.blocking
                }
            },
        )
    }
}
