package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

public interface DatabaseUpdater {
    public suspend fun updateCache(
        postKey: MicroBlogKey,
        update: suspend (UiTimelineV2) -> UiTimelineV2,
    )

    public suspend fun deleteFromCache(postKey: MicroBlogKey)

    public suspend fun updateActionMenu(
        postKey: MicroBlogKey,
        newActionMenu: ActionMenu.Item,
    )
}
