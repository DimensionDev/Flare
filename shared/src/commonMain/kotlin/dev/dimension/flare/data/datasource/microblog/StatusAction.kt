package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.humanizer.humanize
import kotlinx.collections.immutable.ImmutableList

sealed interface StatusAction {
    data class Group(
        val displayItem: Item,
        val actions: ImmutableList<StatusAction>,
    ) : StatusAction

    sealed interface Item : StatusAction {
        data object More : Item

        data class Like(
            val count: Long,
            val liked: Boolean,
            val onClicked: () -> Unit,
        ) : Item {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data class Retweet(
            val count: Long,
            val retweeted: Boolean,
            val onClicked: () -> Unit,
        ) : Item {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data class Reply(
            val count: Long,
        ) : Item {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data class Quote(
            val count: Long,
        ) : Item {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data class Bookmark(
            val count: Long,
            val bookmarked: Boolean,
            val onClicked: () -> Unit,
        ) : Item {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data object Delete : Item

        data object Report : Item

        data class Reaction(
            val reacted: Boolean,
        ) : Item
    }
}
