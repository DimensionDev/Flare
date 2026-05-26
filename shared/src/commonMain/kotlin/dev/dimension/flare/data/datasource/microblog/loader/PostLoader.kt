package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

public interface PostLoader {
    public suspend fun status(statusKey: MicroBlogKey): UiTimelineV2

    public suspend fun deleteStatus(statusKey: MicroBlogKey)
}
