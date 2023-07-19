package dev.dimension.flare.ui.model

import dev.dimension.flare.model.MicroBlogKey

sealed interface UiUser {
    val userKey: MicroBlogKey
    val name: String
    val handle: String
    val avatarUrl: String

    data class Mastodon(
        override val userKey: MicroBlogKey,
        override val name: String,
        override val handle: String,
        override val avatarUrl: String,
    ) : UiUser
}
