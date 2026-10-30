package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType

@Immutable
public data class UiInstance(
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val domain: String,
    val platformId: String,
    val bannerUrl: String?,
    val usersCount: Long,
    val platformDisplayName: String = platformId,
    val platformIcon: IconType = UiIcon.World.asType(),
)
