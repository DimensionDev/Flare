package dev.dimension.flare.data.datastore.model

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.Serializable

@Serializable
internal data class ComposeConfigData(
    val visibility: UiTimelineV2.Post.Visibility? = null,
    val lastAccounts: List<MicroBlogKey> = emptyList(),
)
