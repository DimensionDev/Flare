package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey

@Immutable
public sealed class ComposeStatus {
    public abstract val statusKey: MicroBlogKey

    public data class Quote(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus()

    public open class Reply(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus()

    public data class VVOComment(
        override val statusKey: MicroBlogKey,
        val rootId: String,
    ) : Reply(statusKey)
}
