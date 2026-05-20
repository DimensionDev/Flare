package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineRef
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabDescriptor
import dev.dimension.flare.model.MicroBlogKey

internal class TimelinePersistenceMapper(
    private val catalog: TimelineCatalog,
) {
    fun toTabItem(descriptor: TimelineTabDescriptor.Source): SourceTimelineTabItemV2 =
        SourceTimelineTabItemV2.fromSource(
            source = toSourceRef(descriptor),
            ref = descriptor.ref,
        )

    fun toTabItem(source: TimelineSourceRef): SourceTimelineTabItemV2 =
        SourceTimelineTabItemV2.fromSource(
            source = source,
            ref = runCatching { decode(source) }.getOrNull(),
        )

    fun toTabItem(slot: TimelineSlot): TimelineTabItemV2 =
        when (val content = slot.content) {
            is TimelineSlotContent.Source -> {
                SourceTimelineTabItemV2.fromSlot(
                    slot = slot,
                    source = content.source,
                    ref = runCatching { decode(content.source) }.getOrNull(),
                )
            }

            is TimelineSlotContent.Group -> {
                GroupTimelineTabItemV2(
                    id = slot.id,
                    children = content.children.map { toTabItem(it) },
                    mergePolicy = content.mergePolicy,
                    source = content.source,
                    presentation = slot.presentation,
                    title = slot.title,
                    icon = slot.icon,
                    appearancePatch = slot.presentation.appearance,
                    enabled = slot.presentation.enabled,
                )
            }
        }

    fun toSlot(
        descriptor: TimelineTabDescriptor.Source,
        presentation: TimelinePresentation = TimelinePresentation(),
    ): TimelineSlot =
        toSourceRef(descriptor).toSlot(
            slotId = descriptor.id,
            presentation = presentation,
        )

    fun toSlot(item: TimelineTabItemV2): TimelineSlot =
        when (item) {
            is SourceTimelineTabItemV2 -> {
                val source =
                    item.source
                        ?: throw IllegalArgumentException("Runtime timeline tab cannot be persisted: ${item.id}")
                source.toSlot(
                    slotId = item.id,
                    presentation = item.presentation ?: TimelinePresentation(),
                )
            }

            is GroupTimelineTabItemV2 -> {
                TimelineSlot(
                    id = item.id,
                    content =
                        TimelineSlotContent.Group(
                            children = item.children.map { toSlot(it) },
                            source = item.source,
                            mergePolicy = item.mergePolicy,
                        ),
                    presentation = item.presentation,
                )
            }
        }

    fun resolveAccountKey(slot: TimelineSlot): MicroBlogKey? =
        when (val content = slot.content) {
            is TimelineSlotContent.Source -> {
                resolveAccountKey(content.source)
            }

            is TimelineSlotContent.Group -> {
                null
            }
        }

    fun resolveAccountKey(item: TimelineTabItemV2): MicroBlogKey? =
        when (item) {
            is SourceTimelineTabItemV2 -> {
                item.ref
                    ?.data
                    ?.let { it as? TimelineSpec.AccountData }
                    ?.accountKey
                    ?: item.source?.let(::resolveAccountKey)
            }

            is GroupTimelineTabItemV2 -> {
                null
            }
        }

    fun resolveAccountKey(source: TimelineSourceRef): MicroBlogKey? =
        runCatching { decode(source) }
            .getOrNull()
            ?.data
            ?.let { it as? TimelineSpec.AccountData }
            ?.accountKey

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
