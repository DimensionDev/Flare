package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import androidx.paging.PagingData
import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.coroutines.flow.Flow

@Immutable
sealed interface ProfileTab {
    @Immutable
    data class Timeline(
        val type: Type,
        val flow: Flow<PagingData<UiTimeline>>,
    ) : ProfileTab {
        @Immutable
        enum class Type {
            Status,
            StatusWithReplies,
            Likes,
        }
    }

    @Immutable
    data object Media : ProfileTab
}
