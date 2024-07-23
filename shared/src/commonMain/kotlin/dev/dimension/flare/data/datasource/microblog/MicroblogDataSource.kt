package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface MicroblogDataSource {
    val account: UiAccount

    fun homeTimeline(
        pageSize: Int = 20,
        pagingKey: String = "home_${account.accountKey}",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>>

    fun notification(
        type: NotificationFilter = NotificationFilter.All,
        pageSize: Int = 20,
        pagingKey: String = "notification_${type}_${account.accountKey}",
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>>

    val supportedNotificationFilter: List<NotificationFilter>

    fun userByAcct(acct: String): CacheData<UiUserV2>

    fun userById(id: String): CacheData<UiProfile>

    fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>>

    fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        mediaOnly: Boolean = false,
        pagingKey: String = "user_${userKey}_${if (mediaOnly) "media" else "all"}",
    ): Flow<PagingData<UiTimeline>>

    fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        pagingKey: String = "status_$statusKey",
    ): Flow<PagingData<UiTimeline>>

    fun status(statusKey: MicroBlogKey): CacheData<UiTimeline>

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
    ): Flow<PagingData<UiTimeline>>

    fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiUserV2>>

    fun discoverUsers(pageSize: Int = 20): Flow<PagingData<UiUserV2>>

    fun discoverStatuses(
        pageSize: Int = 20,
        scope: CoroutineScope,
        pagingKey: String = "discover_status_${account.accountKey}",
    ): Flow<PagingData<UiTimeline>>

    fun discoverHashtags(pageSize: Int = 20): Flow<PagingData<UiHashtag>>

    fun composeConfig(statusKey: MicroBlogKey? = null): ComposeConfig

    fun profileActions(): List<ProfileAction>

    suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    )
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
    Comment,
    Like,
}

fun MicroblogDataSource.relationKeyWithUserKey(userKey: MicroBlogKey) = "relation:${account.accountKey}:$userKey"
