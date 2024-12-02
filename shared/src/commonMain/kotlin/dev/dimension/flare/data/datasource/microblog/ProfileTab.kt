package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import androidx.paging.PagingData
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.profile.ProfileMedia
import kotlinx.coroutines.flow.Flow

sealed interface ProfileTab {
    data class Timeline(
        val type: Type,
        val flow: Flow<PagingData<UiTimeline>>,
    ) : ProfileTab {
        enum class Type {
            Status,
            StatusWithReplies,
        }
    }

    data class Media(
        val flow: Flow<PagingData<ProfileMedia>>,
    ) : ProfileTab
}