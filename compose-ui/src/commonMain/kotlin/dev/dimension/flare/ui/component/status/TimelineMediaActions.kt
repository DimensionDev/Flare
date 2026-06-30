package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2

public enum class TimelineMediaMenuAction {
    Download,
    DownloadAll,
    ShareImage,
    CopyLink,
}

public fun interface TimelineMediaActionHandler {
    public fun handle(
        post: UiTimelineV2.Post,
        media: UiMedia,
        action: TimelineMediaMenuAction,
    )
}

public data class TimelineMediaActionConfig(
    public val showShareImage: Boolean,
    public val handler: TimelineMediaActionHandler,
)

public val LocalTimelineMediaActionConfig: ProvidableCompositionLocal<TimelineMediaActionConfig?> =
    staticCompositionLocalOf {
        null
    }
