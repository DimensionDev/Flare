package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.datasource.*
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequestPoll
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.*
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
class MisskeyDataSource(
    private val account: UiAccount.Misskey,
) : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()
    private val service by lazy {
        dev.dimension.flare.data.network.misskey.MisskeyService(
            baseUrl = "https://${account.credential.host}/api/",
            token = account.credential.accessToken,
        )
    }
    override fun homeTimeline(pageSize: Int, pagingKey: String): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator = HomeTimelineRemoteMediator(
                account,
                service,
                database,
                pagingKey,
            ),
        )
    }

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator = when (type) {
                NotificationFilter.All -> NotificationRemoteMediator(
                    account,
                    service,
                    database,
                    pagingKey,
                )

                NotificationFilter.Mention -> MentionTimelineRemoteMediator(
                    account,
                    service,
                    database,
                    pagingKey,
                )
            },
        )
    }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(
            NotificationFilter.All,
            NotificationFilter.Mention,
        )

    override fun userByAcct(acct: String): CacheData<UiUser> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user = service
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
                val user = service
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
                database.dbUserQueries.findByKey(userKey).asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi() }
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
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator = UserTimelineRemoteMediator(
                account,
                service,
                userKey,
                database,
                pagingKey,
            ),
        )
    }

    override fun context(
        statusKey: MicroBlogKey,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator = StatusDetailRemoteMediator(
                statusKey,
                database,
                account,
                service,
                pagingKey,
                statusOnly = false,
            ),
        )
    }

    override fun status(statusKey: MicroBlogKey, pagingKey: String): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = 1,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator = StatusDetailRemoteMediator(
                statusKey,
                database,
                account,
                service,
                pagingKey,
                statusOnly = true,
            ),
        )
    }

    fun emoji() = Cacheable(
        fetchSource = {
            val emojis = service.emojis().body()?.emojis.orEmpty().toImmutableList()
            database.dbEmojiQueries.insert(
                account.accountKey.host,
                emojis.toDb(account.accountKey.host).content
            )
        },
        cacheSource = {
            database.dbEmojiQueries.get(account.accountKey.host).asFlow()
                .mapToOneNotNull(Dispatchers.IO)
                .map { it.toUi().toImmutableList() }
        },
    )

    override suspend fun compose(data: ComposeData, progress: (ComposeProgress) -> Unit) {
        require(data is MissKeyComposeData)
        val maxProgress = data.medias.size + 1
        val mediaIds = data.medias.mapIndexed { index, item ->
            service.upload(
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
                visibility = when (data.visibility) {
                    UiStatus.Misskey.Visibility.Public -> "public"
                    UiStatus.Misskey.Visibility.Home -> "home"
                    UiStatus.Misskey.Visibility.Followers -> "followers"
                    UiStatus.Misskey.Visibility.Specified -> "specified"
                },
                renoteId = data.renoteId,
                replyId = data.inReplyToID,
                fileIds = mediaIds.takeIf { it.isNotEmpty() },
                cw = data.spoilerText.takeIf { it?.isNotEmpty() == true && it.isNotBlank() },
                poll = data.poll?.let { poll ->
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

    data class MissKeyComposeData(
        val account: UiAccount.Misskey,
        val content: String,
        val visibility: UiStatus.Misskey.Visibility = UiStatus.Misskey.Visibility.Public,
        val inReplyToID: String? = null,
        val renoteId: String? = null,
        val medias: List<FileItem> = emptyList(),
        val sensitive: Boolean = false,
        val spoilerText: String? = null,
        val poll: Poll? = null,
        val localOnly: Boolean = false,
    ) : ComposeData {
        data class Poll(
            val options: List<String>,
            val expiredAfter: Long,
            val multiple: Boolean,
        )
    }

    suspend fun renote(
        status: UiStatus.Misskey,
    ) {
        service.notesRenotes(
            NotesChildrenRequest(
                noteId = status.statusKey.id,
            ),
        )
    }
}
