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

public interface XqtTimelineDataSource {
    public fun featuredTimelineLoader(): RemoteLoader<UiTimelineV2>

    public fun bookmarkTimelineLoader(): RemoteLoader<UiTimelineV2>

    public fun deviceFollowTimelineLoader(): RemoteLoader<UiTimelineV2>
}

public object XqtTimelineSpecs {
    public val featured: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "xqt.featured",
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is XqtTimelineDataSource)
                service.featuredTimelineLoader()
            },
        )

    public val bookmark: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "xqt.bookmark",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is XqtTimelineDataSource)
                service.bookmarkTimelineLoader()
            },
        )

    public val deviceFollow: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "xqt.device_follow",
            title = UiStrings.Posts,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is XqtTimelineDataSource)
                service.deviceFollowTimelineLoader()
            },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            featured,
            bookmark,
            deviceFollow,
        )
}
