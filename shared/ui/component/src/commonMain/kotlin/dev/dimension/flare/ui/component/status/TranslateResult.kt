package dev.dimension.flare.ui.component.status

import dev.dimension.flare.ui.model.UiState

internal data class TranslateResult(
    val contentWarning: UiState<String>?,
    val text: UiState<String>,
)
