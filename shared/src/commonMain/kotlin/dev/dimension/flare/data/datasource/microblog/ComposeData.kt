package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.compose.ComposeStatus

public data class ComposeData(
    val account: UiAccount,
    val content: String,
    val visibility: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type =
        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public,
    val language: List<String> = listOf("en"),
    val medias: List<FileItem> = emptyList(),
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

    public data class ReferenceStatus(
        val data: UiTimeline?,
        val composeStatus: ComposeStatus,
    )
}
