package dev.dimension.flare.ui.model

import dev.dimension.flare.common.JSON_WITH_ENCODE_DEFAULT
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class UiTimelineV2StableItemKeyTest {
    @Test
    fun keysAreStoredAndRecomputedForCopiesAndDeserialization() {
        val profile = createSampleUser()
        val post = createSampleStatus(profile)
        val feed =
            UiTimelineV2.Feed(
                title = "feed",
                description = null,
                url = "https://example.com/feed",
                createdAt = post.createdAt,
                source = UiTimelineV2.Feed.Source("test", null),
                accountType = post.accountType,
            )
        val notification =
            UiTimelineV2.TimelinePostItem(
                post = post,
                presentation = UiTimelineV2.PostPresentation(notificationKey = MicroBlogKey("notification", "example.com")),
            )
        val items =
            listOf(
                post,
                feed,
                notification,
                UiTimelineV2.Message(
                    statusKey = post.statusKey,
                    icon = UiIcon.Reply,
                    type = UiTimelineV2.Message.Type.Raw("message"),
                    createdAt = post.createdAt,
                    clickEvent = ClickEvent.Noop,
                    accountType = post.accountType,
                ),
                UiTimelineV2.User(
                    value = profile,
                    createdAt = post.createdAt,
                    statusKey = post.statusKey,
                    accountType = post.accountType,
                ),
                UiTimelineV2.UserList(
                    message = null,
                    users = persistentListOf(profile),
                    createdAt = post.createdAt,
                    statusKey = post.statusKey,
                    post = null,
                    accountType = post.accountType,
                ),
            )

        assertEquals(items.size, items.map { it.stableItemKey }.distinct().size)
        items.forEach { item ->
            assertSame(item.stableItemKey, item.stableItemKey)
            val copied = item.withItemKey("explicit-key")
            assertEquals("explicit-key", copied.stableItemKey)
            assertEquals(item.stableItemKey, copied.withItemKey(null).stableItemKey)

            val encoded = JSON_WITH_ENCODE_DEFAULT.encodeToString<UiTimelineV2>(copied)
            assertFalse("\"stableItemKey\"" in encoded)
            val decoded = JSON_WITH_ENCODE_DEFAULT.decodeFromString<UiTimelineV2>(encoded)
            assertEquals(item.stableItemKey, decoded.stableItemKey)
            assertSame(decoded.stableItemKey, decoded.stableItemKey)
        }
        assertNotEquals(post.stableItemKey, post.copy(statusKey = MicroBlogKey("other", "example.com")).stableItemKey)
        assertNotEquals(feed.stableItemKey, feed.copy(url = "https://example.com/other").stableItemKey)
        assertNotEquals(
            notification.stableItemKey,
            notification
                .copy(
                    presentation = notification.presentation.copy(notificationKey = MicroBlogKey("other", "example.com")),
                ).stableItemKey,
        )
    }
}
