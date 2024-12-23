package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal interface AuthenticatedMicroblogDataSource : MicroblogDataSource {
    val accountKey: MicroBlogKey

    fun notification(
        type: NotificationFilter = NotificationFilter.All,
        pageSize: Int = 20,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>>

    val supportedNotificationFilter: List<NotificationFilter>

    fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>>

    suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    )

    suspend fun deleteStatus(statusKey: MicroBlogKey)

    fun composeConfig(statusKey: MicroBlogKey? = null): ComposeConfig

    fun profileActions(): List<ProfileAction>

    suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    )

    fun notificationBadgeCount(): CacheData<Int> = Cacheable({ }, { flowOf(0) })
}

internal data class ComposeProgress(
    val progress: Int,
    val total: Int,
) {
    val percent: Double
        get() = progress.toDouble() / total.toDouble()
}

public enum class NotificationFilter {
    All,
    Mention,
    Comment,
    Like,
}

internal fun AuthenticatedMicroblogDataSource.relationKeyWithUserKey(userKey: MicroBlogKey) = "relation:$accountKey:$userKey"
