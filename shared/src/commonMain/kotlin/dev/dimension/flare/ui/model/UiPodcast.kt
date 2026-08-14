package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList

@Immutable
public data class UiPodcast(
    val id: String,
    val title: String,
    val playbackUrl: String?,
    val ended: Boolean,
    val creator: UiProfile,
    val hosts: SerializableImmutableList<UiProfile>,
    val speakers: SerializableImmutableList<UiProfile>,
    val listeners: SerializableImmutableList<UiProfile>,
)
