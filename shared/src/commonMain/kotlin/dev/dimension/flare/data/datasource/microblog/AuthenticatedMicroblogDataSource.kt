package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
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
        val check = checkComposeText(data)
        require(check?.isValid != false) { check?.error ?: "Post text exceeds the character limit." }
        publish(data, progress)
    }

    public suspend fun checkComposeText(data: ComposeData): ComposeConfig.Text.Check? {
        val type =
            when (data.referenceStatus?.composeStatus) {
                is ComposeStatus.Quote -> ComposeType.Quote
                is ComposeStatus.Reply -> ComposeType.Reply
                null -> ComposeType.New
            }
        return composeConfig(type).text?.validate(data.content, data.spoilerText)
    }

    // Platform hook; callers use compose to validate before uploading media.
    public suspend fun publish(
        data: ComposeData,
        progress: () -> Unit,
    )

    public fun composeConfig(type: ComposeType): ComposeConfig
}
