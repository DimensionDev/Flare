package dev.dimension.flare.data.datasource

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.coroutines.flow.Flow

internal interface MicroblogService {
    fun homeTimeline(
        pageSize: Int = 20,
        pagingKey: String = "home",
    ): Flow<PagingData<UiStatus>>

    fun notification(
        type: NotificationFilter = NotificationFilter.All,
        pageSize: Int = 20,
        pagingKey: String = "notification",
    ): Flow<PagingData<UiStatus>>

    val supportedNotificationFilter: List<NotificationFilter>
    fun userByAcct(
        acct: String,
    ): CacheData<UiUser>

    fun userById(
        id: String,
    ): CacheData<UiUser>

    fun relation(
        userKey: MicroBlogKey,
    ): Flow<UiState<UiRelation>>

    fun userTimeline(
        userKey: MicroBlogKey,
        pageSize: Int = 20,
        pagingKey: String = "user_$userKey",
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
}

enum class NotificationFilter {
    All,
    Mention,
}
