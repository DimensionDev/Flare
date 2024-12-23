package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import androidx.paging.PagingData
import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.coroutines.flow.Flow

@Immutable
public sealed interface ProfileTab {
    @Immutable
    public data class Timeline internal constructor(
        val type: Type,
        val flow: Flow<PagingData<UiTimeline>>,
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
