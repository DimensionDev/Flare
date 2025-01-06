package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.render.UiRichText

@Immutable
public sealed interface UiUserV2 {
    public val avatar: String
    public val name: UiRichText
    public val handle: String
    public val key: MicroBlogKey
    public val platformType: PlatformType
    public val onClicked: ClickContext.() -> Unit
}
