package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal interface AuthenticatedMicroblogDataSource : MicroblogDataSource {
    val accountKey: MicroBlogKey

    fun notification(type: NotificationFilter = NotificationFilter.All): RemoteLoader<UiTimelineV2>

    val supportedNotificationFilter: List<NotificationFilter>

    suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    )

    fun deleteStatus(statusKey: MicroBlogKey)

    fun composeConfig(type: ComposeType): ComposeConfig

    fun profileActions(): List<ActionMenu>

    suspend fun notificationBadgeCount(): Int

    fun handleEvent(event: PostEvent)
}

internal interface RelationDataSource {
    suspend fun relation(userKey: MicroBlogKey): UiRelation

    fun follow(userKey: MicroBlogKey)

    fun unfollow(userKey: MicroBlogKey)

    fun block(userKey: MicroBlogKey)

    fun unblock(userKey: MicroBlogKey)

    fun mute(userKey: MicroBlogKey)

    fun unmute(userKey: MicroBlogKey)
}

internal enum class ComposeType {
    New,
    Quote,
    Reply,
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
