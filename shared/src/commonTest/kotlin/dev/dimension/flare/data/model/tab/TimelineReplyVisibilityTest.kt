package dev.dimension.flare.data.model.tab

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineReplyVisibilityTest {
    @Test
    fun legacyReplyExclusionsMapToNewVisibilityOptions() {
        assertEquals(
            TimelineReplyVisibility.AllReplies,
            TimelineFilterConfig().replyVisibility,
        )
        assertEquals(
            TimelineReplyVisibility.ToFollowedAccounts,
            TimelineFilterConfig(excludedKinds = listOf(TimelinePostKind.ReplyToUnfollowed)).replyVisibility,
        )
        assertEquals(
            TimelineReplyVisibility.NoReplies,
            TimelineFilterConfig(excludedKinds = listOf(TimelinePostKind.Reply)).replyVisibility,
        )
        assertEquals(
            TimelineReplyVisibility.NoReplies,
            TimelineFilterConfig(
                excludedKinds = listOf(TimelinePostKind.Reply, TimelinePostKind.ReplyToUnfollowed),
            ).replyVisibility,
        )
    }

    @Test
    fun changingReplyVisibilityPreservesOtherFilters() {
        val original =
            TimelineFilterConfig(
                excludedKinds = listOf(TimelinePostKind.ReplyToUnfollowed, TimelinePostKind.Repost),
                excludedContents = listOf(TimelinePostContent.Video),
            )

        val changed = original.withReplyVisibility(TimelineReplyVisibility.NoReplies)

        assertEquals(
            listOf(TimelinePostKind.Repost, TimelinePostKind.Reply),
            changed.excludedKinds,
        )
        assertEquals(original.excludedContents, changed.excludedContents)
    }
}
