package dev.dimension.flare.data.platform

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.DiscoverStatusTimelinePresenter
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ListTimelinePresenter
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public object CommonTimelineSpecs {
    public val home: TimelineSpec<TimelineSpec.AccountBasedData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_HOME,
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

    public val discover: TimelineSpec<TimelineSpec.AccountBasedData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_DISCOVER,
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                DiscoverStatusTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    public val list: TimelineSpec<TimelineSpec.AccountResourceData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_LIST,
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

public fun UiList.List.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 =
    CommonTimelineSpecs.list
        .tabItem(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = avatar?.let { IconType.Url(it) } ?: UiIcon.List.asType(),
        )
