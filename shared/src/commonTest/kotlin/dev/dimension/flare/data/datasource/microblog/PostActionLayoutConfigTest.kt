package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.model.UiIcon
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class PostActionLayoutConfigTest {
    @Test
    fun disabledConfigKeepsOriginalActions() {
        val actions =
            persistentListOf(
                action(PostActionFamily.Reply, UiIcon.Reply),
                ActionMenu.Group(
                    displayItem = moreItem(),
                    actions = persistentListOf(action(PostActionFamily.Share, UiIcon.Share)),
                ),
            )

        assertEquals(actions, actions.applyPostActionLayout(PostActionLayoutConfig.Default))
    }

    @Test
    fun enabledConfigMovesGroupsAndKeepsUnknownActionsInOverflow() {
        val unknown = ActionMenu.Item(icon = UiIcon.Info)
        val repostGroup =
            ActionMenu.Group(
                displayItem = action(PostActionFamily.Repost, UiIcon.Retweet),
                actions =
                    persistentListOf(
                        action(PostActionFamily.Repost, UiIcon.Retweet),
                        action(PostActionFamily.Quote, UiIcon.Quote),
                    ),
            )
        val actions =
            persistentListOf(
                action(PostActionFamily.Reply, UiIcon.Reply),
                repostGroup,
                action(PostActionFamily.Like, UiIcon.Like),
                ActionMenu.Group(
                    displayItem = moreItem(),
                    actions =
                        persistentListOf(
                            action(PostActionFamily.Bookmark, UiIcon.Bookmark),
                            ActionMenu.Divider,
                            action(PostActionFamily.Share, UiIcon.Share),
                            ActionMenu.Divider,
                            action(PostActionFamily.Delete, UiIcon.Delete),
                            unknown,
                        ),
                ),
            )
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary =
                    persistentListOf(
                        PostActionFamily.Like,
                        PostActionFamily.Bookmark,
                        PostActionFamily.Reply,
                    ),
                overflow =
                    persistentListOf(
                        PostActionFamily.Share,
                    ),
                hidden = persistentListOf(PostActionFamily.Delete),
            )

        val result = actions.applyPostActionLayout(config)

        assertEquals(
            listOf(PostActionFamily.Like, PostActionFamily.Bookmark, PostActionFamily.Reply),
            result.take(3).map { (it as ActionMenu.Item).actionFamily },
        )
        val more = assertIs<ActionMenu.Group>(result.last())
        assertEquals(
            listOf(PostActionFamily.Share, PostActionFamily.Repost, null),
            more.actions.map {
                when (it) {
                    is ActionMenu.Item -> it.actionFamily
                    is ActionMenu.Group -> it.displayItem.actionFamily
                    ActionMenu.Divider -> null
                }
            },
        )
        assertEquals(repostGroup, more.actions[1])
        assertFalse(more.actions.any { (it as? ActionMenu.Item)?.actionFamily == PostActionFamily.Delete })
    }

    @Test
    fun emptyOverflowDoesNotGenerateMoreGroup() {
        val actions =
            persistentListOf(
                action(PostActionFamily.Reply, UiIcon.Reply),
                action(PostActionFamily.Like, UiIcon.Like),
            )
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Reply, PostActionFamily.Like),
                overflow = persistentListOf<PostActionFamily>(),
            )

        val result = actions.applyPostActionLayout(config)

        assertEquals(2, result.size)
        assertFalse(result.any { it is ActionMenu.Group })
    }

    private fun action(
        family: PostActionFamily,
        icon: UiIcon,
    ): ActionMenu.Item =
        ActionMenu.Item(
            icon = icon,
            actionFamily = family,
        )

    private fun moreItem(): ActionMenu.Item =
        ActionMenu.Item(
            icon = UiIcon.More,
            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
        )
}
