package dev.dimension.flare.data.datasource.microblog.timeline

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType

public fun TimelineRef<out TimelineSpec.Data>.toTimelineTabDescriptor(
    title: UiText = spec.title.asText(),
    icon: IconType = spec.icon,
): TimelineTabDescriptor.Source =
    TimelineTabDescriptor.Source(
        ref = this,
        display =
            TimelineDisplay(
                title = title,
                icon = icon,
            ),
    )

public fun <T : TimelineSpec.Data> TimelineSpec<T>.toTimelineTabDescriptor(
    data: T,
    title: UiText = this.title.asText(),
    icon: IconType = this.icon,
): TimelineTabDescriptor.Source =
    ref(data).toTimelineTabDescriptor(
        title = title,
        icon = icon,
    )

public fun TimelineTabDescriptor.Source.toTimelineShortcutDescriptor(
    title: UiStrings,
    icon: UiIcon,
): TimelineShortcutDescriptor =
    TimelineShortcutDescriptor(
        title = title,
        icon = icon,
        target =
            TimelineShortcutDescriptor.Target.Timeline(
                ref = ref,
                display = display,
            ),
    )

public fun UiList.List.toTimelineTabDescriptor(accountKey: MicroBlogKey): TimelineTabDescriptor.Source =
    CommonTimelineSpecs.list.toTimelineTabDescriptor(
        data = TimelineSpec.AccountResourceData(accountKey, id),
        title = UiText.Raw(title),
        icon = avatar?.let { IconType.Url(it) } ?: UiIcon.List.asType(),
    )
