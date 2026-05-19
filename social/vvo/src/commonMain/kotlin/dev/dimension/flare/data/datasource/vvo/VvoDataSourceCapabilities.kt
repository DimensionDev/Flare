package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow

public interface VvoStatusDataSource {
    public val accountKey: MicroBlogKey

    public fun statusComment(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>

    public fun statusRepost(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>

    public fun commentChild(commentKey: MicroBlogKey): RemoteLoader<UiTimelineV2>

    public fun comment(statusKey: MicroBlogKey): CacheData<UiTimelineV2>

    public fun statusExtendedText(statusKey: MicroBlogKey): Flow<UiState<String>>

    public fun status(statusKey: MicroBlogKey): Flow<UiState<UiTimelineV2>>
}
