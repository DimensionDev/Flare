package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2

@Immutable
public data class ProfileTab(
    public val name: UiStrings,
    public val displayType: DisplayType = DisplayType.Timeline,
    public val showAllImagesInGallery: Boolean = true,
    public val loader: RemoteLoader<UiTimelineV2>,
) {
    public enum class DisplayType {
        Timeline,
        Gallery,
    }
}
