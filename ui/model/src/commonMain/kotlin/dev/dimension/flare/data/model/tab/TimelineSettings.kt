package dev.dimension.flare.data.model.tab

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class TabSettingsV2(
    public val homeSlots: List<TimelineSlot> = emptyList(),
)

public const val SYSTEM_HOME_MIXED_TIMELINE_ID: String = "mixed_timeline_system_home"

@Immutable
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

@Immutable
@Serializable
public data class TimelinePresentation(
    public val titleOverride: String? = null,
    public val iconOverride: IconType? = null,
    public val appearanceOverride: AppearanceBag? = null,
    public val enabled: Boolean = true,
    public val filterConfig: TimelineFilterConfig = TimelineFilterConfig(),
) {
    public val appearance: AppearancePatch? by lazy {
        appearanceOverride?.toPatch()
    }

    public fun withOverrides(
        titleOverride: String?,
        iconOverride: IconType?,
        appearancePatch: AppearancePatch?,
        enabled: Boolean,
        filterConfig: TimelineFilterConfig,
    ): TimelinePresentation =
        TimelinePresentation(
            titleOverride = titleOverride,
            iconOverride = iconOverride,
            appearanceOverride = appearancePatch?.takeUnless { it == AppearancePatch.EMPTY }?.toBag(),
            enabled = enabled,
            filterConfig = filterConfig,
        )
}

@Immutable
@Serializable
public sealed interface TimelineSlotContent {
    @Immutable
    @Serializable
    @SerialName("source")
    public data class Source(
        @SerialName("target")
        public val source: TimelineSourceRef,
    ) : TimelineSlotContent

    @Immutable
    @Serializable
    @SerialName("group")
    public data class Group(
        public val children: List<TimelineSlot> = emptyList(),
        public val source: GroupSource = GroupSource.Manual,
        public val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
    ) : TimelineSlotContent
}

@Immutable
@Serializable
public enum class GroupSource {
    Manual,
    SystemHome,
}

@Immutable
@Serializable
public enum class TimelineMergePolicy {
    Time,
    TimePerPage,
    Staggered,
}

@Immutable
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
