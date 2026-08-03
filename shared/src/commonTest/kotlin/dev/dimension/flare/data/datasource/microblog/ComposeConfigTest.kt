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
    fun defaultVisibilityOptionsDoNotExposePlatformSpecificPrivatePosts() {
        assertFalse(UiTimelineV2.Post.Visibility.Private in ComposeConfig.Visibility().allVisibilities)
    }

    @Test
    fun visibilityMergeKeepsOnlyCommonOptions() {
        val first =
            visibility(
                UiTimelineV2.Post.Visibility.Home,
                UiTimelineV2.Post.Visibility.Public,
                UiTimelineV2.Post.Visibility.Followers,
                default = UiTimelineV2.Post.Visibility.Home,
            )
        val second =
            visibility(
                UiTimelineV2.Post.Visibility.Public,
                UiTimelineV2.Post.Visibility.Followers,
                default = UiTimelineV2.Post.Visibility.Followers,
            )

        val merged = first.merge(second)

        assertEquals(
            persistentListOf(
                UiTimelineV2.Post.Visibility.Public,
                UiTimelineV2.Post.Visibility.Followers,
            ),
            merged?.allVisibilities,
        )
        assertEquals(UiTimelineV2.Post.Visibility.Public, merged?.defaultVisibility)
    }

    @Test
    fun visibilityMergeReturnsNullWhenThereIsNoCommonOption() {
        val first = visibility(UiTimelineV2.Post.Visibility.Home)
        val second = visibility(UiTimelineV2.Post.Visibility.Followers)

        assertNull(first.merge(second))
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
