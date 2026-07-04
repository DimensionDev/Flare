package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2

@Immutable
public data class ProfileTab(
    public val name: UiStrings,
    public val displayType: DisplayType = DisplayType.Timeline,
    public val showAllImagesInGallery: Boolean = true,
    public val loader: RemoteLoader<UiTimelineV2>,
    public val id: String = profileTabId(name, displayType, showAllImagesInGallery, loader),
) {
    public enum class DisplayType {
        Timeline,
        Gallery,
    }
}

private fun profileTabId(
    name: UiStrings,
    displayType: ProfileTab.DisplayType,
    showAllImagesInGallery: Boolean,
    loader: RemoteLoader<UiTimelineV2>,
): String =
    (loader as? CacheableRemoteLoader<*>)?.pagingKey
        ?: "profile_tab_${displayType.name}_${name.name}_$showAllImagesInGallery"
