package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.fallbackString

@Immutable
public data class ProfileTab(
    public val name: UiText,
    public val displayType: DisplayType = DisplayType.Timeline,
    public val showAllImagesInGallery: Boolean = true,
    public val loader: RemoteLoader<UiTimelineV2>,
    public val id: String = profileTabId(name, displayType, showAllImagesInGallery, loader),
) {
    public constructor(
        name: UiStrings,
        displayType: DisplayType = DisplayType.Timeline,
        showAllImagesInGallery: Boolean = true,
        loader: RemoteLoader<UiTimelineV2>,
        id: String = profileTabId(name.asText(), displayType, showAllImagesInGallery, loader),
    ) : this(
        name = name.asText(),
        displayType = displayType,
        showAllImagesInGallery = showAllImagesInGallery,
        loader = loader,
        id = id,
    )

    public enum class DisplayType {
        Timeline,
        Gallery,
    }
}

private fun profileTabId(
    name: UiText,
    displayType: ProfileTab.DisplayType,
    showAllImagesInGallery: Boolean,
    loader: RemoteLoader<UiTimelineV2>,
): String =
    (loader as? CacheableRemoteLoader<*>)?.pagingKey
        ?: "profile_tab_${displayType.name}_${name.fallbackString}_$showAllImagesInGallery"
