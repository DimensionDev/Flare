package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.humanizer.humanize

@Immutable
data class UiList(
    val id: String,
    val title: String,
    val description: String? = null,
    val avatar: String? = null,
    val creator: UiUserV2? = null,
    val likedCount: Long = 0,
    val liked: Boolean = false,
) {
    val likedCountHumanized by lazy {
        likedCount.humanize()
    }
}
