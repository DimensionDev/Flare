package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

internal interface AuthenticatedMicroblogDataSource : MicroblogDataSource {
    val accountKey: MicroBlogKey

    fun notification(type: NotificationFilter = NotificationFilter.All): RemoteLoader<UiTimelineV2>

    val supportedNotificationFilter: List<NotificationFilter>

    suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    )

    fun composeConfig(type: ComposeType): ComposeConfig
}
