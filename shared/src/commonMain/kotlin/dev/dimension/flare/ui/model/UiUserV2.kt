package dev.dimension.flare.ui.model

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.UiRichText

data class UiUserV2(
    val avatar: String,
    val name: UiRichText,
    val handle: String,
    val key: MicroBlogKey,
)
