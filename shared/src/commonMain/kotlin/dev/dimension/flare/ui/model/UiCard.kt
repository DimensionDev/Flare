package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class UiCard(
    val title: String,
    val description: String?,
    val media: UiMedia?,
    val url: String,
)
