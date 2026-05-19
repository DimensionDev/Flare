package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.platform.XqtTimelineSpecs
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
public fun xqtDeviceFollowTimelineSource(accountKey: MicroBlogKey): TimelineSourceRef {
    val spec = XqtTimelineSpecs.deviceFollow
    val data = TimelineSpec.AccountBasedData(accountKey)
    return TimelineSourceRef(
        id = "${spec.id}:$accountKey",
        specId = spec.id,
        title = UiStrings.Posts.asText(),
        icon = UiIcon.List.asType(),
        data = ProtoBuf.encodeToHexString(spec.serializer, data),
    )
}
