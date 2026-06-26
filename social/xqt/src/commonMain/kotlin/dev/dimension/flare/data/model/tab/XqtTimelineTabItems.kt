package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun UiSourceTimelineTabItem.Companion.xqtDeviceFollow(accountKey: MicroBlogKey): UiSourceTimelineTabItem =
    XqtPlatformSpec.deviceFollowTimelineSpec
        .candidate(
            data = TimelineSpec.AccountBasedData(accountKey),
            title = UiStrings.Posts.asText(),
            icon = UiIcon.List.asType(),
        ).toUiTimelineTabItem()
