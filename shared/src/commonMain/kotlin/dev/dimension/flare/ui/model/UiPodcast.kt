package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
public data class UiPodcast(
    val id: String,
    val title: String,
    val playbackUrl: String?,
    val ended: Boolean,
    val creator: UiUserV2,
    val hosts: ImmutableList<UiUserV2>,
    val speakers: ImmutableList<UiUserV2>,
    val listeners: ImmutableList<UiUserV2>,
)
