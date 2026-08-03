package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.platform.TumblrCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TumblrDataSourceTest {
    @Test
    fun homeTimelineCandidatesUseFullWidthPosts() {
        val dataSource =
            TumblrDataSource(
                accountKey = accountKey,
                credentialFlow = flowOf(credential()),
                updateCredential = {},
            )

        assertFullWidthPost(dataSource.defaultTabs.single())
        assertFullWidthPost(dataSource.builtInTimelineTabs.single())
        assertFullWidthPost(assertIs<ShortcutSpec.Target.Timeline>(dataSource.shortcuts.single().target).candidate)
    }

    @Test
    fun composeExposesOnlySupportedVisibilityChoices() {
        val dataSource = dataSource()
        val visibility = assertNotNull(dataSource.composeConfig(ComposeType.New).visibility)

        assertEquals(
            listOf(
                UiTimelineV2.Post.Visibility.Public,
                UiTimelineV2.Post.Visibility.Private,
            ),
            visibility.allVisibilities,
        )
        assertEquals(UiTimelineV2.Post.Visibility.Public, visibility.defaultVisibility)
    }

    @Test
    fun composeVisibilityMapsToTumblrPostState() {
        assertEquals(
            "published",
            UiTimelineV2.Post.Visibility.Public
                .toTumblrState(),
        )
        assertEquals(
            "private",
            UiTimelineV2.Post.Visibility.Private
                .toTumblrState(),
        )
        assertFailsWith<IllegalStateException> {
            UiTimelineV2.Post.Visibility.Home
                .toTumblrState()
        }
    }

    @Test
    fun composeTextLimitsCountUnicodeCodePoints() {
        val boundary = "a".repeat(4095) + "😀"

        assertEquals(
            listOf(boundary, "b"),
            (boundary + "b").chunkedByCodePoints(4096),
        )
        assertEquals(boundary, (boundary + "b").takeCodePoints(4096))
    }

    @Test
    fun replyComposeIsRejectedInsteadOfCreatingReblog() =
        runTest {
            val dataSource = dataSource()

            assertFailsWith<IllegalArgumentException> {
                dataSource.compose(
                    data =
                        ComposeData(
                            content = "This must not become a reblog",
                            referenceStatus =
                                ComposeData.ReferenceStatus(
                                    ComposeStatus.Reply(tumblrPostKey("staff", "123")),
                                ),
                        ),
                    progress = {},
                )
            }
        }

    private fun dataSource(): TumblrDataSource =
        TumblrDataSource(
            accountKey = accountKey,
            credentialFlow = flowOf(credential()),
            updateCredential = {},
        )

    private fun assertFullWidthPost(candidate: TimelineCandidate<*>) {
        val patch = assertNotNull(candidate.appearancePatch)

        assertTrue(patch.contains(AppearanceKeys.FullWidthPost))
        assertEquals(true, patch[AppearanceKeys.FullWidthPost])
    }

    private companion object {
        val accountKey = MicroBlogKey("staff", "tumblr.com")

        fun credential(): TumblrCredential =
            TumblrCredential(
                accessToken = "access",
                refreshToken = "refresh",
                blogIdentifier = "staff",
                blogName = "staff",
                blogUrl = "https://staff.tumblr.com",
            )
    }
}
