package dev.dimension.flare.data.platform

import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
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
