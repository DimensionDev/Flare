package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface AuthenticatedMicroblogDataSource : MicroblogDataSource {
    public val accountKey: MicroBlogKey
}

@HiddenFromObjC
public interface NotificationTimelineDataSource : AuthenticatedMicroblogDataSource {
    public fun notification(type: NotificationFilter = NotificationFilter.All): RemoteLoader<UiTimelineV2>

    public val supportedNotificationFilter: List<NotificationFilter>
}

@HiddenFromObjC
public interface ComposeDataSource : AuthenticatedMicroblogDataSource {
    public suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    )

    public fun composeConfig(type: ComposeType): ComposeConfig
}
