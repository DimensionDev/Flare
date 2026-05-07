package dev.dimension.flare.data.platform

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ListTimelinePresenter

internal object CommonTimelineSpecs {
    val home =
        TimelineSpec(
            id = "common.home",
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                HomeTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    val list =
        TimelineSpec(
            id = "common.list",
            title = UiStrings.List,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            presenterFactory = {
                ListTimelinePresenter(
                    accountType = AccountType.Specific(it.accountKey),
                    listId = it.resourceId,
                )
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
