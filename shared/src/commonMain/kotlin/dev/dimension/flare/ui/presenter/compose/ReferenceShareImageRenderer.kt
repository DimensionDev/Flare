package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

public interface ReferenceShareImageRenderer {
    public fun render(
        post: UiTimelineV2,
        completion: (media: ComposeData.Media?, errorMessage: String?) -> Unit,
    )
}

internal suspend fun ReferenceShareImageRenderer.renderAndAwait(post: UiTimelineV2): ComposeData.Media =
    suspendCoroutine { continuation ->
        render(post) { media, errorMessage ->
            if (media != null) {
                continuation.resume(media)
            } else {
                continuation.resumeWithException(
                    IllegalStateException(errorMessage ?: "Unable to render referenced post image."),
                )
            }
        }
    }
