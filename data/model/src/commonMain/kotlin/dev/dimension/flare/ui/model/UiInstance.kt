package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.PlatformType

@Immutable
public data class UiInstance internal constructor(
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val domain: String,
    val type: PlatformType,
    val bannerUrl: String?,
    val usersCount: Long,
)
