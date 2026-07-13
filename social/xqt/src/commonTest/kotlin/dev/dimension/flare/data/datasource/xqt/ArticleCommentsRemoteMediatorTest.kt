package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.database.cache.mapper.conversationTweets
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.ItemResult
import dev.dimension.flare.data.network.xqt.model.ModuleEntry
import dev.dimension.flare.data.network.xqt.model.ModuleItem
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntry
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineItem
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineModule
import dev.dimension.flare.data.network.xqt.model.TimelineTweet
import dev.dimension.flare.data.network.xqt.model.Tweet
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleCommentsRemoteMediatorTest {
    @Test
    fun conversationTweetsReadsReplyModulesWithoutTheFocalTweet() {
        val focalTweet =
            TimelineAddEntry(
                content = TimelineTimelineItem(itemContent = timelineTweet(id = "article")),
                entryId = "tweet-article",
                sortIndex = "2",
            )
        val reply =
            TimelineAddEntry(
                content =
                    TimelineTimelineModule(
                        items =
                            listOf(
                                ModuleItem(
                                    entryId = "conversationthread-article-tweet-reply",
                                    item = ModuleEntry(itemContent = timelineTweet(id = "reply")),
                                ),
                            ),
                    ),
                entryId = "conversationthread-article",
                sortIndex = "1",
            )
        val instructions =
            listOf<InstructionUnion>(
                TimelineAddEntries(propertyEntries = listOf(focalTweet, reply)),
            )

        assertEquals(listOf("reply"), instructions.conversationTweets().map { it.id })
    }

    private fun timelineTweet(id: String): TimelineTweet =
        TimelineTweet(
            tweetResults = ItemResult(result = Tweet(restId = id)),
        )
}
