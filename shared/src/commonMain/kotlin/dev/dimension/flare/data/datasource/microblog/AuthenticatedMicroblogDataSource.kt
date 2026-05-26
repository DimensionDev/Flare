package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

public interface AuthenticatedMicroblogDataSource : MicroblogDataSource {
    public val accountKey: MicroBlogKey

    public fun notification(type: NotificationFilter = NotificationFilter.All): RemoteLoader<UiTimelineV2>

    public val supportedNotificationFilter: List<NotificationFilter>

    public suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    )

    public fun composeConfig(type: ComposeType): ComposeConfig
}
