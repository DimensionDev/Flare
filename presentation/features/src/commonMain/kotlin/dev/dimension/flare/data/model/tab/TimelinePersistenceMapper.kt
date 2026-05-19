package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineRef
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabDescriptor

internal class TimelinePersistenceMapper(
    private val catalog: TimelineCatalog,
    private val timelineResolver: TimelineResolver,
) {
    fun toTabItem(descriptor: TimelineTabDescriptor.Source): SourceTimelineTabItemV2 =
        timelineResolver.toTabItem(toSourceRef(descriptor))

    fun toTabItem(slot: TimelineSlot): TimelineTabItemV2 =
        timelineResolver.toTabItem(slot)

    fun toSlot(
        descriptor: TimelineTabDescriptor.Source,
        presentation: TimelinePresentation = TimelinePresentation(),
    ): TimelineSlot =
        toSourceRef(descriptor).toSlot(
            slotId = descriptor.id,
            presentation = presentation,
        )

    fun toSlot(item: TimelineTabItemV2): TimelineSlot =
        timelineResolver.toSlot(item)

    fun decode(source: TimelineSourceRef): TimelineRef<out TimelineSpec.Data> =
        catalog.decode(
            specId = source.specId,
            encodedData = source.data,
        )

    fun toSourceRef(descriptor: TimelineTabDescriptor.Source): TimelineSourceRef {
        val encoded = catalog.encode(descriptor.ref)
        return TimelineSourceRef(
            id = descriptor.id,
            specId = encoded.specId,
            title = descriptor.display.title,
            icon = descriptor.display.icon,
            data = encoded.data,
        )
    }
}
