package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

@Immutable
public data class UiSearchHistory internal constructor(
    val keyword: String,
    val createdAt: Instant,
)
