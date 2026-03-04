package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

internal interface DatabaseUpdater {
    suspend fun updateCache(
        postKey: MicroBlogKey,
        update: suspend (UiTimelineV2) -> UiTimelineV2,
    )

    suspend fun deleteFromCache(postKey: MicroBlogKey)

    suspend fun updateActionMenu(
        postKey: MicroBlogKey,
        newActionMenu: ActionMenu.Item,
    )
}
