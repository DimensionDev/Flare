package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
public data class UiPodcast(
    val id: String,
    val title: String,
    val playbackUrl: String?,
    val ended: Boolean,
    val creator: UiProfile,
    val hosts: ImmutableList<UiProfile>,
    val speakers: ImmutableList<UiProfile>,
    val listeners: ImmutableList<UiProfile>,
)
