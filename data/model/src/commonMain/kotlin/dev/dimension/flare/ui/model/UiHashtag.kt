package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
public data class UiHashtag(
    val hashtag: String,
    val description: String?,
    val searchContent: String,
)
