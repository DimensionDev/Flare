package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ComposeConfigTest {
    @Test
    fun defaultVisibilityOptionsDoNotExposeChannelPosts() {
        assertFalse(UiTimelineV2.Post.Visibility.Channel in ComposeConfig.Visibility().allVisibilities)
    }

    @Test
    fun visibilityMergeHidesSelectionEvenWhenDifferentRangesSharePublic() {
        val first = visibility(UiTimelineV2.Post.Visibility.Public, UiTimelineV2.Post.Visibility.SelfOnly)
        val second = visibility(UiTimelineV2.Post.Visibility.Public, UiTimelineV2.Post.Visibility.Followers)
        assertNull(first.merge(second))
    }

    @Test
    fun visibilityMergeReturnsNullWhenThereIsNoCommonOption() {
        assertNull(visibility(UiTimelineV2.Post.Visibility.Home).merge(visibility(UiTimelineV2.Post.Visibility.Followers)))
    }

    @Test
    fun visibilityMergeKeepsASharedNonPublicDefault() {
        val config = visibility(UiTimelineV2.Post.Visibility.Followers, UiTimelineV2.Post.Visibility.Public)
        assertEquals(config, config.merge(config))
    }

    @Test
    fun visibilityMergeHidesSelectionWhenPlatformDefaultsDiffer() {
        val first = visibility(UiTimelineV2.Post.Visibility.Public, UiTimelineV2.Post.Visibility.Followers)
        val second = first.copy(defaultVisibility = UiTimelineV2.Post.Visibility.Followers)
        assertNull(first.merge(second))
    }

    @Test
    fun visibilityMergeAcceptsTheSameOptionsInAnotherOrder() {
        val first = visibility(UiTimelineV2.Post.Visibility.Public, UiTimelineV2.Post.Visibility.Followers)
        val second =
            visibility(
                UiTimelineV2.Post.Visibility.Followers,
                UiTimelineV2.Post.Visibility.Public,
                default = UiTimelineV2.Post.Visibility.Public,
            )
        assertEquals(first, first.merge(second))
    }

    @Test
    fun visibilityRequiresAtLeastOneOption() {
        assertFailsWith<IllegalArgumentException> {
            ComposeConfig.Visibility(allVisibilities = persistentListOf())
        }
    }

    @Test
    fun visibilityRequiresDefaultToBeAvailable() {
        assertFailsWith<IllegalArgumentException> {
            ComposeConfig.Visibility(
                allVisibilities = persistentListOf(UiTimelineV2.Post.Visibility.Public),
                defaultVisibility = UiTimelineV2.Post.Visibility.Home,
            )
        }
    }

    private fun visibility(
        vararg values: UiTimelineV2.Post.Visibility,
        default: UiTimelineV2.Post.Visibility = values.first(),
    ): ComposeConfig.Visibility =
        ComposeConfig.Visibility(
            allVisibilities = persistentListOf(*values),
            defaultVisibility = default,
        )
}
