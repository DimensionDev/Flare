package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.platform.TumblrCredential
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
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
