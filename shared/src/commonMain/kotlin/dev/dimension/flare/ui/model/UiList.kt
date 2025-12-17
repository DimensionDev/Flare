package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.PlatformType

@Immutable
public data class UiList internal constructor(
    val id: String,
    val title: String,
    val description: String? = null,
    val avatar: String? = null,
    val creator: UiUserV2? = null,
    val likedCount: UiNumber = UiNumber(0),
    val liked: Boolean = false,
    val platformType: PlatformType,
    val type: Type = Type.List,
    val readonly: Boolean = false,
) {
    public enum class Type {
        Feed,
        List,
        Antenna,
    }
}
