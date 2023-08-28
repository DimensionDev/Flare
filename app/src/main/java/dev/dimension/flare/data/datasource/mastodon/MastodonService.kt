package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.moriatsushi.koject.lazyInject
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.datasource.MicroblogService
import dev.dimension.flare.data.datasource.NotificationFilter
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@OptIn(ExperimentalPagingApi::class)
internal class MastodonService(
    private val account: UiAccount.Mastodon,
) : MicroblogService {
    private val database: CacheDatabase by lazyInject()
    override fun homeTimeline(pageSize: Int, pagingKey: String): Flow<PagingData<UiStatus>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = HomeTimelineRemoteMediator(
                account.service,
                database,
                account.accountKey,
                pagingKey,
            ),
        ) {
            database.pagingTimelineDao().getPagingSource(pagingKey, account.accountKey)
        }.flow.map {
            it.map {
                it.toUi()
            }
        }
    }

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> = Pager(
        config = PagingConfig(pageSize = pageSize),
        remoteMediator = when (type) {
            NotificationFilter.All -> NotificationRemoteMediator(
                account.service,
                database,
                account.accountKey,
                pagingKey,
            )

            NotificationFilter.Mention -> MentionRemoteMediator(
                account.service,
                database,
                account.accountKey,
                pagingKey,
            )
        },
    ) {
        database.pagingTimelineDao().getPagingSource(pagingKey, account.accountKey)
    }.flow.map {
        it.map {
            it.toUi()
        }
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
                val user = account.service.lookupUserByAcct("$name@$host")
                    ?.toDbUser(account.accountKey.host) ?: throw Exception("User not found")
                database.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                database.userDao().getUserByHandleAndHost(name, host, PlatformType.Mastodon)
                    .mapNotNull { it?.toUi() }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
        val userKey = MicroBlogKey(id, account.accountKey.host)
        return Cacheable(
            fetchSource = {
                val user = account.service.lookupUser(id).toDbUser(account.accountKey.host)
                database.userDao().insertAll(listOf(user))
            },
            cacheSource = {
                database.userDao().getUser(userKey)
                    .mapNotNull { it?.toUi() }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
        return flow {
            try {
                emit(
                    account.service.showRelationship(userKey.id).first().toUi()
                        .let { UiState.Success(it) },
                )
            } catch (e: Exception) {
                emit(UiState.Error(e))
            }
        }
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = UserTimelineRemoteMediator(
                account.service,
                database,
                account.accountKey,
                userKey,
                pagingKey,
            ),
        ) {
            database.pagingTimelineDao().getPagingSource(pagingKey, account.accountKey)
        }.flow.map {
            it.map {
                it.toUi()
            }
        }

    override fun context(
        statusKey: MicroBlogKey,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = StatusDetailRemoteMediator(
                statusKey,
                account.service,
                database,
                account.accountKey,
                pagingKey,
                statusOnly = false,
            ),
        ) {
            database.pagingTimelineDao().getPagingSource(pagingKey, account.accountKey)
        }.flow.map {
            it.map {
                it.toUi()
            }
        }

    override fun status(statusKey: MicroBlogKey, pagingKey: String): Flow<PagingData<UiStatus>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            remoteMediator = StatusDetailRemoteMediator(
                statusKey,
                account.service,
                database,
                account.accountKey,
                pagingKey,
                statusOnly = true,
            ),
        ) {
            database.pagingTimelineDao().getPagingSource(pagingKey, account.accountKey)
        }.flow.map {
            it.map {
                it.toUi()
            }
        }

    fun emoji() = Cacheable(
        fetchSource = {
            val emojis = account.service.emojis()
            database.emojiDao().insertAll(listOf(emojis.toDb(account.accountKey.host)))
        },
        cacheSource = {
            database.emojiDao().getEmoji(account.accountKey.host)
                .mapNotNull { it?.toUi()?.toImmutableList() }
        },
    )
}
