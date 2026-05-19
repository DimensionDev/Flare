package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.timeline.AccountTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.CommonTimelineSpecs
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public interface BlueskyTimelineDataSource {
    public fun bookmarkTimeline(): RemoteLoader<UiTimelineV2>

    public fun feedTimelineLoader(uri: String): RemoteLoader<UiTimelineV2>
}

public object BlueskyTimelineSpecs {
    public val bookmark: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "bluesky.bookmark",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is BlueskyTimelineDataSource)
                service.bookmarkTimeline()
            },
        )

    public val feed: AccountTimelineSpec<TimelineSpec.AccountResourceData> =
        AccountTimelineSpec(
            id = "bluesky.feed",
            title = UiStrings.Feeds,
            icon = UiIcon.Feeds.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            stableKeyFactory = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is BlueskyTimelineDataSource)
                service.feedTimelineLoader(data.resourceId)
            },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            bookmark,
            feed,
        )
}
