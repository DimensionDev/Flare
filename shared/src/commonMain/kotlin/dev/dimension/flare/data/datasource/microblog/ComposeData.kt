package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus

public data class ComposeData(
    val content: String,
    val visibility: UiTimelineV2.Post.Visibility =
        UiTimelineV2.Post.Visibility.Public,
    val language: List<String> = listOf("en"),
    val medias: List<Media> = emptyList(),
    val sensitive: Boolean = false,
    val spoilerText: String? = null,
    val poll: Poll? = null,
    val localOnly: Boolean = false,
    val referenceStatus: ReferenceStatus? = null,
) {
    public data class Poll(
        val options: List<String>,
        val expiredAfter: Long,
        val multiple: Boolean,
    )

    public data class Media(
        val file: FileItem,
        val altText: String?,
    )

    public data class ReferenceStatus(
        val data: UiTimelineV2?,
        val composeStatus: ComposeStatus,
    )
}
