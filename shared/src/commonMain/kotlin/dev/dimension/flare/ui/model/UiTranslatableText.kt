package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public data class UiTranslatableText(
    public val original: UiRichText,
    public val translation: UiRichText? = null,
)
