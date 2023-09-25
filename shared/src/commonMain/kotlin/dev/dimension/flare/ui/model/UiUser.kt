package dev.dimension.flare.ui.model

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.humanize

expect class UiUserExtra

internal expect fun createUiUserExtra(user: UiUser): UiUserExtra

sealed class UiUser {
    abstract val userKey: MicroBlogKey
    abstract val handle: String
    abstract val avatarUrl: String
    val extra by lazy {
        createUiUserExtra(this)
    }

    data class Mastodon(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        val remoteHost: String,
        override val avatarUrl: String,
        val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val locked: Boolean,
        internal val raw: dev.dimension.flare.data.network.mastodon.api.model.Account,
    ) : UiUser() {
        override val handle = "@$handleInternal@$remoteHost"

        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long,
        ) {
            val fansCountHumanized = fansCount.humanize()
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }
    }

    data class Misskey(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        val remoteHost: String,
        override val avatarUrl: String,
        val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val isCat: Boolean,
        val isBot: Boolean,
        val relation: UiRelation.Misskey,
        internal val accountHost: String,
    ) : UiUser() {

        override val handle = "@$handleInternal@$remoteHost"
        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long,
        ) {
            val fansCountHumanized = fansCount.humanize()
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }
    }

//    data class Bluesky(
//        override val userKey: MicroBlogKey,
//        val name: String,
//        val handleInternal: String,
//        override val avatarUrl: String,
//        val bannerUrl: String?,
//        val description: String?,
//        val matrices: Matrices,
//        val relation: UiRelation.Bluesky,
//    ) : UiUser {
//
//        override val handle: String = "@$handleInternal"
//        data class Matrices(
//            val fansCount: Long,
//            val followsCount: Long,
//            val statusesCount: Long,
//        ) {
//            val fansCountHumanized = fansCount.humanize()
//            val followsCountHumanized = followsCount.humanize()
//            val statusesCountHumanized = statusesCount.humanize()
//        }
//    }
}
