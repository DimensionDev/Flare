package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
public data class UiSearchHistory internal constructor(
    val keyword: String,
    val createdAt: Instant,
)
