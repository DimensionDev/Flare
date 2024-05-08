package dev.dimension.flare.ui.model

import kotlinx.datetime.Instant

data class UiSearchHistory(
    val keyword: String,
    val createdAt: Instant,
)
