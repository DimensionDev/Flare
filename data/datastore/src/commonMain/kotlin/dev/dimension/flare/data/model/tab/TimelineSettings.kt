package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class TabSettingsV2(
    public val homeSlots: List<TimelineSlot> = emptyList(),
)

public const val SYSTEM_HOME_MIXED_TIMELINE_ID: String = "mixed_timeline_system_home"

@Serializable
public data class TimelineSlot(
    public val id: String,
    public val content: TimelineSlotContent,
    public val presentation: TimelinePresentation = TimelinePresentation(),
) {
    public val title: UiText =
        presentation.titleOverride?.let { UiText.Raw(it) } ?: when (content) {
            is TimelineSlotContent.Source -> content.source.title
            is TimelineSlotContent.Group -> UiStrings.MixedTimeline.asText()
        }

    public val icon: IconType =
        presentation.iconOverride ?: when (content) {
            is TimelineSlotContent.Source -> content.source.icon
            is TimelineSlotContent.Group -> UiIcon.Rss.asType()
        }
}

@Serializable
public data class TimelinePresentation(
    public val titleOverride: String? = null,
    public val iconOverride: IconType? = null,
    public val appearanceOverride: AppearanceBag? = null,
    public val enabled: Boolean = true,
    public val filterConfig: TimelineFilterConfig = TimelineFilterConfig(),
)

@Serializable
public sealed interface TimelineSlotContent {
    @Serializable
    @SerialName("source")
    public data class Source(
        @SerialName("target")
        public val source: TimelineSourceRef,
    ) : TimelineSlotContent

    @Serializable
    @SerialName("group")
    public data class Group(
        public val children: List<TimelineSlot> = emptyList(),
        public val source: GroupSource = GroupSource.Manual,
        public val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
    ) : TimelineSlotContent
}

@Serializable
public enum class GroupSource {
    Manual,
    SystemHome,
}

@Serializable
public enum class TimelineMergePolicy {
    Time,
    TimePerPage,
    Staggered,
}

@Serializable
public data class TimelineSourceRef(
    public val id: String,
    public val specId: String,
    public val title: UiText,
    public val icon: IconType,
    public val data: String,
)

public fun TimelineSourceRef.toSlot(
    slotId: String = id,
    presentation: TimelinePresentation = TimelinePresentation(),
): TimelineSlot =
    TimelineSlot(
        id = slotId,
        content = TimelineSlotContent.Source(this),
        presentation = presentation,
    )

public fun List<TimelineSlot>.normalizeSystemHomeMixedTimeline(enabled: Boolean): List<TimelineSlot> {
    val deduplicatedSlots = distinctBy { it.id }
    val existingSystemHomeGroup = deduplicatedSlots.firstOrNull { it.isSystemHomeMixedTimeline() }
    val slotsWithoutSystemHomeGroup = deduplicatedSlots.filterNot { it.isSystemHomeMixedTimeline() }
    if (!enabled || slotsWithoutSystemHomeGroup.size < 2) {
        return slotsWithoutSystemHomeGroup
    }

    val systemHomeGroup =
        TimelineSlot(
            id = SYSTEM_HOME_MIXED_TIMELINE_ID,
            content =
                TimelineSlotContent.Group(
                    children = slotsWithoutSystemHomeGroup,
                    source = GroupSource.SystemHome,
                    mergePolicy =
                        (existingSystemHomeGroup?.content as? TimelineSlotContent.Group)?.mergePolicy
                            ?: TimelineMergePolicy.TimePerPage,
                ),
            presentation = existingSystemHomeGroup?.presentation ?: TimelinePresentation(),
        )
    val targetIndex = deduplicatedSlots.indexOfFirst { it.isSystemHomeMixedTimeline() }.takeIf { it >= 0 } ?: 0
    return slotsWithoutSystemHomeGroup
        .toMutableList()
        .apply {
            add(minOf(targetIndex, size), systemHomeGroup)
        }
}

private fun TimelineSlot.isSystemHomeMixedTimeline(): Boolean = (content as? TimelineSlotContent.Group)?.source == GroupSource.SystemHome
