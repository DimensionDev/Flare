package dev.dimension.flare.data.datasource.microblog.timeline

import androidx.paging.PagingData
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

public data class TimelineDisplay(
    public val title: UiText,
    public val icon: IconType,
)

public sealed interface TimelineTabDescriptor {
    public val id: String
    public val display: TimelineDisplay

    public data class Source(
        public val ref: TimelineRef<out TimelineSpec.Data>,
        override val display: TimelineDisplay,
    ) : TimelineTabDescriptor {
        override val id: String
            get() = ref.semanticId
    }
}

public interface TimelineTabProvider {
    public val defaultTimelineTabs: ImmutableList<TimelineTabDescriptor.Source>
    public val builtInTimelineTabs: ImmutableList<TimelineTabDescriptor.Source>
    public val timelineShortcuts: ImmutableList<TimelineShortcutDescriptor>
}

public interface PinnableTimelineProvider {
    public val pinnableTimelineTabs: List<PinnableTimelineTabSection>
}

public data class PinnableTimelineTabSection(
    public val title: UiStrings,
    public val data: Flow<PagingData<TimelineTabDescriptor.Source>>,
)

public data class TimelineShortcutDescriptor(
    public val title: UiStrings,
    public val icon: UiIcon,
    public val target: Target,
) {
    public object RouteIds {
        public const val ALL_LISTS: String = "all_lists"
        public const val ALL_DIRECT_MESSAGES: String = "all_direct_messages"
        public const val BLUESKY_ALL_FEEDS: String = "bluesky_all_feeds"
        public const val MISSKEY_ALL_ANTENNAS: String = "misskey_all_antennas"
        public const val MISSKEY_ALL_CHANNELS: String = "misskey_all_channels"
    }

    public sealed interface Target {
        public data class Timeline(
            public val ref: TimelineRef<out TimelineSpec.Data>,
            public val display: TimelineDisplay,
        ) : Target

        public data class Route(
            public val id: String,
            public val accountKey: MicroBlogKey? = null,
            public val parameters: Map<String, String> = emptyMap(),
        ) : Target
    }
}
