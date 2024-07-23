package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.render.UiRichText

@Immutable
sealed interface UiUserV2 {
    val avatar: String
    val name: UiRichText
    val handle: String
    val key: MicroBlogKey
    val platformType: PlatformType
}
