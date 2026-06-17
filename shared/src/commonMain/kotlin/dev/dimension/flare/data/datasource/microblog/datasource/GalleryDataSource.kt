package dev.dimension.flare.data.datasource.microblog.datasource

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface GalleryDataSource {
    public fun galleryDetail(statusKey: MicroBlogKey): RemoteLoader<GalleryDetail>

    public fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>

    public fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>
}

@Serializable
@Immutable
public data class GalleryDetail(
    val post: UiTimelineV2.Post,
    val orientation: GalleryOrientation,
)

@Serializable
@Immutable
public enum class GalleryOrientation {
    Horizontal,
    Vertical,
}
