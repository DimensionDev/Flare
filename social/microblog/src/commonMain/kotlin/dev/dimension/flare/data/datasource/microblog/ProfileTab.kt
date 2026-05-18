package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2

@Immutable
public sealed interface ProfileTab {
    @Immutable
    public data class Timeline(
        val type: Type,
        val loader: RemoteLoader<UiTimelineV2>,
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
