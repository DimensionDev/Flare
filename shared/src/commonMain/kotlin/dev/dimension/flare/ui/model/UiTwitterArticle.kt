package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public data class UiTwitterArticle internal constructor(
    val profile: UiProfile,
    val image: String? = null,
    val title: String,
    val content: UiRichText,
)
