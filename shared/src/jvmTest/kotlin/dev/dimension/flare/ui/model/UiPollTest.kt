package dev.dimension.flare.ui.model

import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UiPollTest {
    @Test
    fun onVoteUsesSelectedOptionIndexesWhenBuildingDeeplink() {
        val accountKey = MicroBlogKey(id = "account", host = "test.social")
        val postKey = MicroBlogKey(id = "post", host = "test.social")
        val poll =
            UiPoll(
                id = "poll",
                options =
                    persistentListOf(
                        UiPoll.Option(title = "A", votesCount = 1, percentage = 0.5f),
                        UiPoll.Option(title = "B", votesCount = 1, percentage = 0.5f),
                    ),
                multiple = true,
                ownVotes = persistentListOf(),
                voteEvent =
                    PostEvent.Misskey.Vote(
                        accountKey = accountKey,
                        postKey = postKey,
                        options = persistentListOf(),
                    ),
                expiresAt = null,
            )

        var launchedUrl: String? = null
        UiPoll::class.java
            .getDeclaredMethod(
                "_init_\$lambda\$3\$0",
                UiPoll::class.java,
                ClickContext::class.java,
                ImmutableList::class.java,
            ).apply {
                isAccessible = true
            }.invoke(
                null,
                poll,
                ClickContext(launcher = { launchedUrl = it }),
                persistentListOf(1),
            )

        val deeplink = assertNotNull(launchedUrl)
        val event = assertNotNull(DeeplinkEvent.parse(deeplink))
        val voteEvent = assertNotNull(event.postEvent as? PostEvent.Misskey.Vote)
        assertEquals(listOf(1), voteEvent.options)
    }
}
