package dev.dimension.flare.ui.component.status

import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.render.UiRichText

internal data class TranslateResult(
    val contentWarning: UiState<UiRichText>?,
    val text: UiState<UiRichText>,
)
