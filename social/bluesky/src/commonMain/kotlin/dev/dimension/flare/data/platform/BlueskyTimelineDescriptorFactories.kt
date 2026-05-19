package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabDescriptor
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineTabDescriptor
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType

public fun UiList.Feed.toTimelineTabDescriptor(accountKey: MicroBlogKey): TimelineTabDescriptor.Source =
    BlueskyTimelineSpecs.feed.toTimelineTabDescriptor(
        data = TimelineSpec.AccountResourceData(accountKey, id),
        title = UiText.Raw(title),
        icon = avatar?.let { IconType.Url(it) } ?: UiIcon.Feeds.asType(),
    )
