package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

@Immutable
public data class UiSearchHistory internal constructor(
    val keyword: String,
    val createdAt: Instant,
)

@HiddenFromObjC
public fun createUiSearchHistory(
    keyword: String,
    createdAt: Instant,
): UiSearchHistory = UiSearchHistory(keyword = keyword, createdAt = createdAt)
