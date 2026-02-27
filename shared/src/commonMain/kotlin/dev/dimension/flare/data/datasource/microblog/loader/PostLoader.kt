package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

internal interface PostLoader {
    suspend fun status(statusKey: MicroBlogKey): UiTimelineV2

    suspend fun deleteStatus(statusKey: MicroBlogKey)
}
