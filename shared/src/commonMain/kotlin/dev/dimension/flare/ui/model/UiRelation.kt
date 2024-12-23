package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
public data class UiRelation internal constructor(
    val following: Boolean = false,
    val isFans: Boolean = false,
    val blocking: Boolean = false,
    val blockedBy: Boolean = false,
    val muted: Boolean = false,
    val hasPendingFollowRequestFromYou: Boolean = false,
    val hasPendingFollowRequestToYou: Boolean = false,
)
