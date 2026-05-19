package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.PagingData
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.platform.BlueskyTimelineDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

public interface BlueskyFeedDataSource : BlueskyTimelineDataSource {
    public val feedHandler: ListHandler<UiList.Feed>

    public fun popularFeeds(
        query: String?,
        scope: CoroutineScope,
    ): Flow<PagingData<Pair<UiList.Feed, Boolean>>>

    public suspend fun subscribeFeed(data: UiList.Feed)

    public suspend fun unsubscribeFeed(data: UiList.Feed)

    public suspend fun favouriteFeed(data: UiList.Feed)
}

public interface BlueskyReportDataSource {
    public suspend fun report(
        statusKey: MicroBlogKey,
        reason: BlueskyReportReason,
    )
}

public enum class BlueskyReportReason {
    Spam,
    Violation,
    Misleading,
    Sexual,
    Rude,
    Other,
}
