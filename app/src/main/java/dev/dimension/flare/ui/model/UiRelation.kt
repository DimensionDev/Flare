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
}
