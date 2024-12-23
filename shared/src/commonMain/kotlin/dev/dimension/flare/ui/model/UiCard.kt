package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
public data class UiCard internal constructor(
    val title: String,
    val description: String?,
    val media: UiMedia?,
    val url: String,
)
