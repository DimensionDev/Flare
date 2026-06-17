package dev.dimension.flare.ui.component.status

import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri

internal fun UiTimelineV2.Post.openMedia(
    media: UiMedia,
    launcher: (String) -> Unit,
) {
    when (mediaClickPolicy) {
        UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia -> {
            launcher(statusMediaRoute(media).toUri())
        }

        UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent -> {
            onClicked.invoke(ClickContext(launcher = launcher))
        }
    }
}

private fun UiTimelineV2.Post.statusMediaRoute(media: UiMedia): DeeplinkRoute.Media.StatusMedia {
    val mediaIndex =
        images.indexOf(media).takeIf { it >= 0 }
            ?: images.indexOfFirst { it.url == media.url }.coerceAtLeast(0)
    return DeeplinkRoute.Media.StatusMedia(
        statusKey = statusKey,
        accountType = accountType,
        index = mediaIndex,
        preview =
            when (media) {
                is UiMedia.Image -> media.previewUrl
                is UiMedia.Video -> media.thumbnailUrl
                is UiMedia.Gif -> media.previewUrl
                is UiMedia.Audio -> null
            },
    )
}
