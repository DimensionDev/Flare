package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
public data class UiEmoji internal constructor(
    val shortcode: String,
    val url: String,
    val category: String,
    val searchKeywords: List<String>,
)
