package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import com.moriatsushi.koject.lazyInject
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.datasource.MicroblogService
import dev.dimension.flare.data.datasource.NotificationFilter
import dev.dimension.flare.data.datasource.timelinePager
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import dev.dimension.flare.ui.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@OptIn(ExperimentalPagingApi::class)
internal class MisskeyService(
    private val account: UiAccount.Misskey,
) : MicroblogService {
    private val database: CacheDatabase by lazyInject()

    override fun homeTimeline(pageSize: Int, pagingKey: String): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            mediator = HomeTimelineRemoteMediator(
                account,
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
                    database,
                    pagingKey,
                )

                NotificationFilter.Mention -> MentionTimelineRemoteMediator(
                    account,
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
                val user = account
                    .service
                    .findUserByName(name, host)
                    ?.toDbUser(account.accountKey.host)
                    ?: throw Exception("User not found")
                database.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                database.userDao().getUserByHandleAndHost(name, host, PlatformType.Misskey)
                    .mapNotNull { it?.toUi() }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
        val userKey = MicroBlogKey(id, account.accountKey.host)
        return Cacheable(
            fetchSource = {
                val user = account
                    .service
                    .findUserById(id)
                    ?.toDbUser(account.accountKey.host)
                    ?: throw Exception("User not found")
                database.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                database.userDao().getUser(userKey)
                    .mapNotNull { it?.toUi() }
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
                pagingKey,
                statusOnly = true,
            ),
        )
    }

    fun emoji() = Cacheable(
        fetchSource = {
            val emojis = account.service.emojis().toImmutableList()
            database.emojiDao().insertAll(listOf(emojis.toDb(account.accountKey.host)))
        },
        cacheSource = {
            database.emojiDao().getEmoji(account.accountKey.host)
                .mapNotNull { it?.toUi()?.toImmutableList() }
        },
    )
}
