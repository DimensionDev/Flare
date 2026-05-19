package dev.dimension.flare.data.datasource.microblog.timeline

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public interface ListTimelineDataSource {
    public fun listTimeline(listId: String): RemoteLoader<UiTimelineV2>
}

public object CommonTimelineSpecs {
    public val home: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "common.home",
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                service.homeTimeline()
            },
        )

    public val discover: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "common.discover",
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                service.discoverStatuses()
            },
        )

    public val list: AccountTimelineSpec<TimelineSpec.AccountResourceData> =
        AccountTimelineSpec(
            id = "common.list",
            title = UiStrings.List,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            stableKeyFactory = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is ListTimelineDataSource)
                service.listTimeline(listId = data.resourceId)
            },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            home,
            discover,
            list,
        )
}
