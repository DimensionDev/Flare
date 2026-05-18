package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
public data class UiSearchHistory(
    val keyword: String,
    val createdAt: Instant,
)
