package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserHighlightsInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileTabCapabilitiesTest {
    @Test
    fun optionalTabsFollowUserMetadata() {
        assertFalse(User(restId = "1").hasHighlightsTab)
        assertFalse(userWithHighlights(canHighlight = false, count = "1").hasHighlightsTab)
        assertFalse(userWithHighlights(canHighlight = true, count = "0").hasHighlightsTab)
        assertFalse(userWithHighlights(canHighlight = true, count = "invalid").hasHighlightsTab)
        assertTrue(userWithHighlights(canHighlight = true, count = "1").hasHighlightsTab)

        assertFalse(User(restId = "1").hasArticlesTab)
        assertFalse(User(restId = "1", userSeedTweetCount = 0).hasArticlesTab)
        assertTrue(User(restId = "1", userSeedTweetCount = 1).hasArticlesTab)
    }
}

private fun userWithHighlights(
    canHighlight: Boolean,
    count: String,
) = User(
    restId = "1",
    highlightsInfo =
        UserHighlightsInfo(
            canHighlightTweets = canHighlight,
            highlightedTweets = count,
        ),
)
