package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.AccountTimelineSpec
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType

internal object CommonTimelineSpecs {
    val home =
        AccountTimelineSpec(
            id = "common.home",
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                service.homeTimeline()
            },
        )

    val discover =
        AccountTimelineSpec(
            id = "common.discover",
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                service.discoverStatuses()
            },
        )

    val list =
        AccountTimelineSpec(
            id = "common.list",
            title = UiStrings.List,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is ListDataSource)
                service.listTimeline(listId = data.resourceId)
            },
        )
}

internal fun UiList.List.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 {
    val source =
        CommonTimelineSpecs.list.target(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = avatar?.let { IconType.Url(it) } ?: UiIcon.List.asType(),
        )
    return SourceTimelineTabItemV2.fromSource(source) {
        CommonTimelineSpecs.list.createPresenter(source.data)
    }
}
