package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun standaloneQuoteUsesRepostPositionWithoutCreatingMoreMenu() {
        val quote = action(PostActionFamily.Quote, UiIcon.Quote)
        val like = action(PostActionFamily.Like, UiIcon.Like)
        val actions = persistentListOf<ActionMenu>(quote, like)
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Like, PostActionFamily.Repost),
                overflow = persistentListOf(),
            )

        assertEquals(listOf<ActionMenu>(like, quote), actions.applyPostActionLayout(config))
    }

    @Test
    fun standaloneQuoteUsesRepostPositionInMoreMenu() {
        val quote = action(PostActionFamily.Quote, UiIcon.Quote)
        val share = action(PostActionFamily.Share, UiIcon.Share)
        val actions = persistentListOf<ActionMenu>(quote, share)
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(),
                overflow = persistentListOf(PostActionFamily.Repost, PostActionFamily.Share),
            )

        val more = assertIs<ActionMenu.Group>(actions.applyPostActionLayout(config).single())
        assertEquals(moreItem(), more.displayItem)
        assertEquals(listOf<ActionMenu>(quote, share), more.actions)
    }

    @Test
    fun hidingRepostHidesStandaloneQuote() {
        val quote = action(PostActionFamily.Quote, UiIcon.Quote)
        val like = action(PostActionFamily.Like, UiIcon.Like)
        val actions = persistentListOf<ActionMenu>(quote, like)
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Like),
                overflow = persistentListOf(),
                hidden = persistentListOf(PostActionFamily.Repost),
            )

        assertEquals(listOf<ActionMenu>(like), actions.applyPostActionLayout(config))
    }

    @Test
    fun unavailableActionsAreOmittedWithoutChangingAvailableActionOrder() {
        val reply = action(PostActionFamily.Reply, UiIcon.Reply)
        val like = action(PostActionFamily.Like, UiIcon.Like)
        val families = PostActionLayoutHelpers.allEditableFamilies.reversed()
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = (families + families).toPersistentList(),
            )

        val result = persistentListOf(reply, like).applyPostActionLayout(config)

        assertEquals(listOf<ActionMenu>(like, reply), result)
        assertEquals(result, result.applyPostActionLayout(config))
    }

    @Test
    fun standaloneQuoteDoesNotGenerateUnavailableReply() {
        val quote = action(PostActionFamily.Quote, UiIcon.Quote)
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Repost, PostActionFamily.Reply),
            )

        assertEquals(listOf<ActionMenu>(quote), persistentListOf(quote).applyPostActionLayout(config))
    }

    @Test
    fun favoriteWithBookmarkIconDoesNotGenerateUnavailableBookmark() {
        val favorite = action(PostActionFamily.Favorite, UiIcon.Bookmark)
        val actions = persistentListOf(ActionMenu.Group(displayItem = moreItem(), actions = persistentListOf(favorite)))
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Bookmark, PostActionFamily.Favorite),
            )

        assertEquals(listOf<ActionMenu>(favorite), actions.applyPostActionLayout(config))
    }

    @Test
    fun existingActionsWithTheSameIconRemainDistinct() {
        val bookmark = action(PostActionFamily.Bookmark, UiIcon.Bookmark)
        val favorite = action(PostActionFamily.Favorite, UiIcon.Bookmark)
        val actions = persistentListOf(bookmark, favorite)
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Favorite, PostActionFamily.Bookmark),
            )

        assertEquals(listOf<ActionMenu>(favorite, bookmark), actions.applyPostActionLayout(config))
    }

    @Test
    fun existingDisabledAndNoopActionsKeepTheirState() {
        val disabled = action(PostActionFamily.Reply, UiIcon.Reply).copy(enabled = false)
        val translating = action(PostActionFamily.Translate, UiIcon.Translate).copy(clickEvent = ClickEvent.Noop)
        val config =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Reply, PostActionFamily.Translate, PostActionFamily.Like),
            )

        assertEquals(
            listOf<ActionMenu>(disabled, translating),
            persistentListOf(disabled, translating).applyPostActionLayout(config),
        )
    }

    @Test
    fun emptyOrFullyHiddenActionsDoNotGenerateButtons() {
        val reply = action(PostActionFamily.Reply, UiIcon.Reply)
        val config = PostActionLayoutConfig(enabled = true)

        assertTrue(persistentListOf<ActionMenu>().applyPostActionLayout(config).isEmpty())
        assertTrue(
            persistentListOf(reply)
                .applyPostActionLayout(config.copy(hidden = persistentListOf(PostActionFamily.Reply)))
                .isEmpty(),
        )
    }

    @Test
    fun unavailablePrimaryActionsKeepRemainingActionsInMoreMenu() {
        val share = action(PostActionFamily.Share, UiIcon.Share)
        val unknown = ActionMenu.Item(icon = UiIcon.Info)
        val actions = persistentListOf(ActionMenu.Group(displayItem = moreItem(), actions = persistentListOf(share, unknown)))

        assertEquals(actions, actions.applyPostActionLayout(PostActionLayoutConfig(enabled = true)))
    }

    @Test
    fun unavailableActionsStayHiddenOutsideButtonRow() {
        val families = PostActionLayoutHelpers.allEditableFamilies
        val actions =
            persistentListOf(
                action(PostActionFamily.Reply, UiIcon.Reply),
                ActionMenu.Group(
                    displayItem = moreItem(),
                    actions = persistentListOf(action(PostActionFamily.Share, UiIcon.Share)),
                ),
            )
        val overflowConfig =
            PostActionLayoutConfig(
                enabled = true,
                primary = persistentListOf(PostActionFamily.Reply),
                overflow = families.toPersistentList(),
            )
        val primaryConfig = overflowConfig.copy(primary = families.toPersistentList())

        listOf(
            PostActionLayoutConfig.Default,
            overflowConfig,
            primaryConfig.copy(enabled = false),
            primaryConfig.copy(
                hidden =
                    families
                        .filterNot {
                            it == PostActionFamily.Reply || it == PostActionFamily.Share
                        }.toPersistentList(),
                primary = families.filterNot { it == PostActionFamily.Share }.toPersistentList(),
            ),
        ).forEach { config ->
            assertEquals(actions, actions.applyPostActionLayout(config))
        }
    }

    @Test
    fun availableTranslationActionsKeepTheirOriginalState() {
        listOf(
            ActionMenu.Item.Text.Localized.Type.Translate,
            ActionMenu.Item.Text.Localized.Type.RetryTranslation,
            ActionMenu.Item.Text.Localized.Type.ShowOriginal,
        ).forEach { type ->
            val translate =
                ActionMenu.Item(
                    icon = UiIcon.Translate,
                    text = ActionMenu.Item.Text.Localized(type),
                    actionFamily = PostActionFamily.Translate,
                )
            val actions = persistentListOf(ActionMenu.Group(displayItem = moreItem(), actions = persistentListOf(translate)))
            val result =
                actions.applyPostActionLayout(
                    PostActionLayoutConfig(enabled = true, primary = persistentListOf(PostActionFamily.Translate)),
                )

            val displayed = assertIs<ActionMenu.Item>(result.single())
            assertEquals(translate, displayed)
            assertTrue(displayed.enabled)
        }
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
