package dev.dimension.flare.ui.model

import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class UiTimelineV2AuthorSubtitleTest {
    @Test
    fun subtitleFallsBackToTheCurrentAuthorHandle() {
        val user = createSampleUser()
        val post = createSampleStatus(user)
        val updatedUser = user.copy(handle = user.handle.copy(raw = "updated"))

        assertEquals(user.handle.canonical, post.authorSubtitle)
        assertEquals(updatedUser.handle.canonical, post.copy(user = updatedUser).authorSubtitle)
        assertEquals("", post.copy(user = null).authorSubtitle)
        assertEquals("", post.copy(authorSubtitleInternal = "").authorSubtitle)
    }

    @Test
    fun subtitleSurvivesPostStorageWithoutChangingTheAuthor() {
        val user = createSampleUser()
        val post = createSampleStatus(user).copy(authorSubtitleInternal = "发布于 北京")
        val restored = assertIs<UiTimelineV2.Post>(post.encodeProtobuf<UiTimelineV2>().decodeProtobuf<UiTimelineV2>())

        assertEquals("发布于 北京", restored.authorSubtitle)
        assertEquals(user, restored.user)
        assertEquals("发布于 北京", restored.copy(user = null).authorSubtitle)
    }

    @Test
    fun subtitleChangesInvalidatePostsAndQuotedPosts() {
        val post = createSampleStatus(createSampleUser())
        val updated = post.copy(authorSubtitleInternal = "来自广东")
        val item = UiTimelineV2.TimelinePostItem(post)
        val quote = item.copy(presentation = UiTimelineV2.PostPresentation(quotes = persistentListOf(post)))

        assertNotEquals(post.renderHash, updated.renderHash)
        assertNotEquals(item.renderHash, item.copy(post = updated).renderHash)
        assertNotEquals(
            quote.renderHash,
            quote.copy(presentation = quote.presentation.copy(quotes = persistentListOf(updated))).renderHash,
        )
    }
}
