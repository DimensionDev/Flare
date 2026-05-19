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

public interface VvoTimelineDataSource {
    public fun favouriteTimeline(): RemoteLoader<UiTimelineV2>

    public fun likeRemoteMediator(): RemoteLoader<UiTimelineV2>
}

public object VvoTimelineSpecs {
    public val favorite: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "vvo.favorite",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is VvoTimelineDataSource)
                service.favouriteTimeline()
            },
        )

    public val liked: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "vvo.liked",
            title = UiStrings.Liked,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is VvoTimelineDataSource)
                service.likeRemoteMediator()
            },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            favorite,
            liked,
        )
}
