package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class UiAccount(
    public val accountKey: MicroBlogKey,
    public val platformType: PlatformType,
)
