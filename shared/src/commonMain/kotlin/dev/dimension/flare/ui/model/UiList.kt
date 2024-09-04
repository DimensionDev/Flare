package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class UiList(
    val id: String,
    val title: String,
    val description: String? = null,
    val avatar: String? = null,
    val creator: UiUserV2? = null,
)
