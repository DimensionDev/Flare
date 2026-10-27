package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.coroutines.flow.first
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
    ) {
        val type =
            when (data.referenceStatus?.composeStatus) {
                is ComposeStatus.Quote -> ComposeType.Quote
                is ComposeStatus.Reply -> ComposeType.Reply
                null -> ComposeType.New
            }
        // ponytail: Use cached/default limits like the editor; add provider expiry if stale limits cause failures.
        val remaining = composeConfig(type).text?.remainingLength(data.content, data.spoilerText)?.first()
        require(remaining?.isValid != false) { "Post text exceeds the platform limits." }
        publish(data, progress)
    }

    // Platform hook; callers use compose to validate before uploading media.
    public suspend fun publish(
        data: ComposeData,
        progress: () -> Unit,
    )

    public fun composeConfig(type: ComposeType): ComposeConfig
}
