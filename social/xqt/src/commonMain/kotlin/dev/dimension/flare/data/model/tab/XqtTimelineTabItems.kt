package dev.dimension.flare.data.model.tab

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.xqt.XQTDeviceFollowTimelinePresenter

public fun SourceTimelineTabItemV2.Companion.xqtDeviceFollow(accountKey: MicroBlogKey): SourceTimelineTabItemV2 =
    runtime(
        id = "${TimelineSpecIds.XQT_DEVICE_FOLLOW}:$accountKey",
        title = UiStrings.Posts.asText(),
        icon = UiIcon.List.asType(),
        createPresenter = {
            XQTDeviceFollowTimelinePresenter(
                AccountType.Specific(accountKey),
            )
        },
    )
