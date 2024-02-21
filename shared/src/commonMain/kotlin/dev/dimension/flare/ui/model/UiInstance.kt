package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.PlatformType

@Immutable
data class UiInstance(
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val domain: String,
    val type: PlatformType,
    val bannerUrl: String?,
    val usersCount: Long,
)
