package dev.dimension.flare.ui.screen.status

import dev.dimension.flare.ui.model.UiState

data class TranslateResult(
    val contentWarning: UiState<String>?,
    val text: UiState<String>,
)
