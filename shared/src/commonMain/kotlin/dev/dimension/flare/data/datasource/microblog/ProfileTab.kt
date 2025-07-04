package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.BaseTimelineLoader

@Immutable
public sealed interface ProfileTab {
    @Immutable
    public data class Timeline internal constructor(
        internal val type: Type,
        internal val loader: BaseTimelineLoader,
    ) : ProfileTab {
        @Immutable
        public enum class Type {
            Status,
            StatusWithReplies,
            Likes,
        }
    }

    @Immutable
    public data object Media : ProfileTab
}
