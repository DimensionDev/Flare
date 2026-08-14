package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PinnedTimelinesManagementSheetResponse(
    val data: PinnedTimelinesManagementSheetResponseData? = null,
) {
    val tagPinnedTimelines: List<TagPinnedTimeline>
        get() =
            data
                ?.pinnableTimelines
                ?.items
                .orEmpty()
                .mapNotNull(PinnableTimeline::asTagPinnedTimeline)
}

@Serializable
internal data class PinnedTimelinesManagementSheetResponseData(
    @SerialName("pinnable_timelines")
    val pinnableTimelines: PinnableTimelines? = null,
)

@Serializable
internal data class PinnableTimelines(
    @SerialName("pinnable_timelines")
    val items: List<PinnableTimeline> = emptyList(),
)

@Serializable
internal data class PinnableTimeline(
    @SerialName("__typename")
    val typeName: String,
    @SerialName("icon_name")
    val iconName: String? = null,
    val name: String? = null,
    val scribe: String? = null,
    @SerialName("tab_label")
    val tabLabel: String? = null,
    val tag: String? = null,
) {
    fun asTagPinnedTimeline(): TagPinnedTimeline? {
        if (typeName != TAG_PINNED_TIMELINE_TYPE) {
            return null
        }
        val tag = tag ?: return null
        val name = name ?: tabLabel ?: return null
        return TagPinnedTimeline(
            iconName = iconName,
            name = name,
            scribe = scribe,
            tabLabel = tabLabel,
            tag = tag,
        )
    }
}

@Serializable
internal data class TagPinnedTimeline(
    @SerialName("icon_name")
    val iconName: String? = null,
    val name: String,
    val scribe: String? = null,
    @SerialName("tab_label")
    val tabLabel: String? = null,
    val tag: String,
) {
    val title: String
        get() = tabLabel?.takeIf(String::isNotBlank) ?: name
}

private const val TAG_PINNED_TIMELINE_TYPE = "TagPinnedTimeline"
