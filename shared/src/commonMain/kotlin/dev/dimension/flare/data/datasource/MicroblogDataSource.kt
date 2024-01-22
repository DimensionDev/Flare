package dev.dimension.flare.data.datasource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.RemoteMediator
import androidx.paging.map
import app.cash.sqldelight.paging3.QueryPagingSource
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MicroblogDataSource {
    val account: UiAccount

    fun homeTimeline(
        pageSize: Int = 20,
        pagingKey: String = "home",
    ): Flow<PagingData<UiStatus>>

    fun notification(
        type: NotificationFilter = NotificationFilter.All,
        pageSize: Int = 20,
        pagingKey: String = "notification_$type",
    ): Flow<PagingData<UiStatus>>

    val supportedNotificationFilter: List<NotificationFilter>

    fun userByAcct(acct: String): CacheData<UiUser>

    fun userById(id: String): CacheData<UiUser>

    fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>>

    fun userTimeline(
        userKey: MicroBlogKey,
        pageSize: Int = 20,
        mediaOnly: Boolean = false,
        pagingKey: String = "user_${userKey}_${if (mediaOnly) "media" else "all"}",
    ): Flow<PagingData<UiStatus>>

    fun context(
        statusKey: MicroBlogKey,
        pageSize: Int = 20,
        pagingKey: String = "status_$statusKey",
    ): Flow<PagingData<UiStatus>>

    fun status(
        statusKey: MicroBlogKey,
        pagingKey: String = "status_only_$statusKey",
    ): Flow<PagingData<UiStatus>>

    suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    )

    suspend fun deleteStatus(statusKey: MicroBlogKey)

    fun searchStatus(
        query: String,
        pageSize: Int = 20,
        pagingKey: String = "search_$query",
    ): Flow<PagingData<UiStatus>>

    fun searchUser(
        query: String,
        pageSize: Int = 20,
    ): Flow<PagingData<UiUser>>
}

data class ComposeProgress(
    val progress: Int,
    val total: Int,
) {
    val percent: Double
        get() = progress.toDouble() / total.toDouble()
}

interface ComposeData

enum class NotificationFilter {
    All,
    Mention,
}

@OptIn(ExperimentalPagingApi::class)
internal fun timelinePager(
    pageSize: Int,
    pagingKey: String,
    accountKey: MicroBlogKey,
    database: CacheDatabase,
    mediator: RemoteMediator<Int, DbPagingTimelineWithStatusView>,
): Flow<PagingData<UiStatus>> {
    return Pager(
        config = PagingConfig(pageSize = pageSize),
        remoteMediator = mediator,
        pagingSourceFactory = {
            QueryPagingSource(
                countQuery =
                    database.dbPagingTimelineQueries.pageCount(
                        account_key = accountKey,
                        paging_key = pagingKey,
                    ),
                transacter = database.dbPagingTimelineQueries,
                context = Dispatchers.IO,
                queryProvider = { limit, offset ->
                    database.dbPagingTimelineQueries.getPage(
                        account_key = accountKey,
                        paging_key = pagingKey,
                        offset = offset,
                        limit = limit,
                    )
                },
            )
        },
    ).flow.map {
        it.map {
            it.toUi()
        }
    }
}

fun MicroblogDataSource.relationKeyWithUserKey(userKey: MicroBlogKey) = "relation:${account.accountKey}:$userKey"