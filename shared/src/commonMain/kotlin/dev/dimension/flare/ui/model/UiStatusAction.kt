package dev.dimension.flare.ui.model

import kotlinx.collections.immutable.ImmutableList

sealed interface UiStatusAction {
    data class Group(
        val displayAction: Action,
        val actions: ImmutableList<UiStatusAction>,
    ) : UiStatusAction

    sealed interface Action : UiStatusAction {
        operator fun invoke()

        data object More : Action {
            override fun invoke() = Unit
        }

        data class Like(
            val count: Long,
            val liked: Boolean,
//            val statusKey: MicroBlogKey,
//            val accountKey: MicroBlogKey,
            val onClicked: () -> Unit,
        ) : Action {
            override fun invoke() = onClicked()
        }

        data class Retweet(
            val count: Long,
            val retweeted: Boolean,
//            val statusKey: MicroBlogKey,
//            val accountKey: MicroBlogKey,
            val onClicked: () -> Unit,
        ) : Action {
            override fun invoke() = onClicked()
        }

        data class Reply(
            val count: Long,
//            val statusKey: MicroBlogKey,
//            val accountKey: MicroBlogKey,
            val onClicked: () -> Unit,
        ) : Action {
            override fun invoke() = onClicked()
        }

        data class Quote(
            val count: Long,
//            val statusKey: MicroBlogKey,
//            val accountKey: MicroBlogKey,
            val onClicked: () -> Unit,
        ) : Action {
            override fun invoke() = onClicked()
        }

        data class Bookmark(
            val count: Long,
            val bookmarked: Boolean,
//            val statusKey: MicroBlogKey,
//            val accountKey: MicroBlogKey,
            val onClicked: () -> Unit,
        ) : Action {
            override fun invoke() = onClicked()
        }

        data class Delete(
//            val statusKey: MicroBlogKey,
//            val accountKey: MicroBlogKey,
            val onClicked: () -> Unit,
        ) : Action {
            override fun invoke() = onClicked()
        }

        data class Report(
//            val statusKey: MicroBlogKey,
//            val accountKey: MicroBlogKey,
            val onClicked: () -> Unit,
        ) : Action {
            override fun invoke() = onClicked()
        }

        data class Reaction(
            val reacted: Boolean,
        ) : Action {
            override fun invoke() = Unit
        }
    }
}
