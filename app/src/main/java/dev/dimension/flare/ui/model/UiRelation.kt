package dev.dimension.flare.ui.model

sealed interface UiRelation {
    data class Mastodon(
        val following: Boolean,
        val isFans: Boolean,
        val blocking: Boolean,
        val muting: Boolean,
        val requested: Boolean,
        val domainBlocking: Boolean
    ) : UiRelation

    data class Misskey(
        val following: Boolean,
        val isFans: Boolean,
        val blocking: Boolean,
        val blocked: Boolean,
        val muted: Boolean,
        val hasPendingFollowRequestFromYou: Boolean,
        val hasPendingFollowRequestToYou: Boolean
    ) : UiRelation

    data class Bluesky(
        val following: String?,
        val followedBy: String?,
        val blocked: Boolean,
        val muted: Boolean
    ) : UiRelation {
        val isFans = followedBy.isNullOrEmpty().not()
        val isFollowing = following.isNullOrEmpty().not()
    }
}
