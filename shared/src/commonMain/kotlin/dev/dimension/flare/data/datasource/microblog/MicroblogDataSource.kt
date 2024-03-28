package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface MicroblogDataSource {
    val account: UiAccount

    fun homeTimeline(
        pageSize: Int = 20,
        pagingKey: String = "home",
        scope: CoroutineScope,
    ): Flow<PagingData<UiStatus>>

    fun notification(
        type: NotificationFilter = NotificationFilter.All,
        pageSize: Int = 20,
        pagingKey: String = "notification_$type",
        scope: CoroutineScope,
    ): Flow<PagingData<UiStatus>>

    val supportedNotificationFilter: List<NotificationFilter>

    fun userByAcct(acct: String): CacheData<UiUser>

    fun userById(id: String): CacheData<UiUser>

    fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>>

    fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        mediaOnly: Boolean = false,
        pagingKey: String = "user_${userKey}_${if (mediaOnly) "media" else "all"}",
    ): Flow<PagingData<UiStatus>>

    fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        pagingKey: String = "status_$statusKey",
    ): Flow<PagingData<UiStatus>>

    fun status(statusKey: MicroBlogKey): CacheData<UiStatus>

    suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    )

    suspend fun deleteStatus(statusKey: MicroBlogKey)

    fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
        pagingKey: String = "search_$query",
    ): Flow<PagingData<UiStatus>>

    fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiUser>>

    fun discoverUsers(pageSize: Int = 20): Flow<PagingData<UiUser>>

    fun discoverStatuses(
        pageSize: Int = 20,
        scope: CoroutineScope,
        pagingKey: String = "discover_status",
    ): Flow<PagingData<UiStatus>>

    fun discoverHashtags(pageSize: Int = 20): Flow<PagingData<UiHashtag>>

    fun supportedComposeEvent(statusKey: MicroBlogKey? = null): List<SupportedComposeEvent>
}

data class ComposeProgress(
    val progress: Int,
    val total: Int,
) {
    val percent: Double
        get() = progress.toDouble() / total.toDouble()
}

enum class NotificationFilter {
    All,
    Mention,
}

enum class SupportedComposeEvent {
    Media,
    Poll,
    Emoji,
    ContentWarning,
    Visibility,
}

fun MicroblogDataSource.relationKeyWithUserKey(userKey: MicroBlogKey) = "relation:${account.accountKey}:$userKey"
