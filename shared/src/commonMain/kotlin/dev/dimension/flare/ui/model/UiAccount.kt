package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class UiAccount(
    public val accountKey: MicroBlogKey,
    @SerialName("platformType")
    public val platformId: String,
    public val platformDisplayName: String = platformId,
    public val platformIcon: IconType = UiIcon.World.asType(),
    public val platformAvailable: Boolean = true,
    public val supportsRelayManagement: Boolean = false,
    public val platformDisplayNameText: UiText = UiText.Raw(platformDisplayName),
)
