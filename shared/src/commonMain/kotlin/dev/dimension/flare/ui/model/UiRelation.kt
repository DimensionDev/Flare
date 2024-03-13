package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface UiRelation {
    @Immutable
    data class Mastodon(
        val following: Boolean,
        val isFans: Boolean,
        val blocking: Boolean,
        val muting: Boolean,
        val requested: Boolean,
        val domainBlocking: Boolean,
    ) : UiRelation

    @Immutable
    data class Misskey(
        val following: Boolean,
        val isFans: Boolean,
        val blocking: Boolean,
        val blocked: Boolean,
        val muted: Boolean,
        val hasPendingFollowRequestFromYou: Boolean,
        val hasPendingFollowRequestToYou: Boolean,
    ) : UiRelation

    @Immutable
    data class Bluesky(
        val isFans: Boolean,
        val following: Boolean,
        val blocking: Boolean,
        val muting: Boolean,
    ) : UiRelation

    @Immutable
    data class XQT(
        val isFans: Boolean,
        val following: Boolean,
        val blocking: Boolean,
        val blockedBy: Boolean,
        val protected: Boolean,
        val muting: Boolean,
    ) : UiRelation
}
