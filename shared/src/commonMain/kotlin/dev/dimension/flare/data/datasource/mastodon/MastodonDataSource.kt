package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import com.benasher44.uuid.uuid4
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.*
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.PostPoll
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.*
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
class MastodonDataSource(
    private val account: UiAccount.Mastodon,
) : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()
    private val service by lazy {
        MastodonService(
            baseUrl = "https://${account.credential.instance}/",
            accessToken = account.credential.accessToken,
        )
    }

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator =
                HomeTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )
    }

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
                    service.lookupUserByAcct("$name@$host")
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
                database.dbUserQueries.findByHandleAndHost(name, host).asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi() }
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
                database.dbUserQueries.findByKey(userKey).asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi() }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
        return flow {
            if (userKey == account.accountKey) {
                emit(UiState.Error(Exception("Cannot follow self")))
            } else {
                try {
                    emit(
                        service.showFriendships(listOf(userKey.id)).first().toUi()
                            .let { UiState.Success(it) },
                    )
                } catch (e: Exception) {
                    emit(UiState.Error(e))
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
                    service,
                    database,
                    account.accountKey,
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
                    service,
                    database,
                    account.accountKey,
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
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                    statusOnly = true,
                ),
        )

    fun emoji() =
        Cacheable(
            fetchSource = {
                val emojis = service.emojis()
                database.dbEmojiQueries.insert(account.accountKey.host, emojis.toDb(account.accountKey.host).content)
            },
            cacheSource = {
                database.dbEmojiQueries.get(account.accountKey.host).asFlow()
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
            data.medias.mapIndexed { index, item ->
                service.upload(
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

    data class MastodonComposeData(
        val account: UiAccount.Mastodon,
        val content: String,
        val visibility: UiStatus.Mastodon.Visibility = UiStatus.Mastodon.Visibility.Public,
        val inReplyToID: String? = null,
        val medias: List<FileItem> = emptyList(),
        val sensitive: Boolean = false,
        val spoilerText: String? = null,
        val poll: Poll? = null,
    ) : ComposeData {
        data class Poll(
            val options: List<String>,
            val expiresIn: Long,
            val multiple: Boolean,
        )
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
                service.favourite(status.statusKey.id)
            } else {
                service.unfavourite(status.statusKey.id)
            }
        }.onFailure {
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                favourited = status.reaction.liked,
                                favouritesCount =
                                    if (status.reaction.liked) {
                                        it.data.favouritesCount?.plus(1)
                                    } else {
                                        it.data.favouritesCount?.minus(1)
                                    },
                            ),
                    )
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
                    it.copy(
                        data =
                            it.data.copy(
                                reblogged = status.reaction.reblogged,
                                reblogsCount =
                                    if (status.reaction.reblogged) {
                                        it.data.reblogsCount?.plus(1)
                                    } else {
                                        it.data.reblogsCount?.minus(1)
                                    },
                            ),
                    )
                },
            )
        }
    }
}
