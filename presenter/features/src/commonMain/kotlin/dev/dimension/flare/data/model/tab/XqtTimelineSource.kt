package dev.dimension.flare.data.model.tab

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
public fun xqtDeviceFollowTimelineSource(accountKey: MicroBlogKey): TimelineSourceRef =
    TimelineSourceRef(
        id = "xqt.device_follow:$accountKey",
        specId = "xqt.device_follow",
        title = UiStrings.Posts.asText(),
        icon = UiIcon.List.asType(),
        data =
            ProtoBuf.encodeToHexString(
                TimelineSpec.AccountBasedData.serializer(),
                TimelineSpec.AccountBasedData(accountKey),
            ),
    )
