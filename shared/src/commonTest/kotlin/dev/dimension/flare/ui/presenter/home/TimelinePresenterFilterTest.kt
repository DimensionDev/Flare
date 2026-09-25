package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostContent
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.repository.KeywordFilterPattern
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelinePresenterFilterTest {
    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    testSingle<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun postTraitsCaptureKindsAndContents() {
        val currentUser = createSampleUser()
        val parentUser = currentUser.copy(key = MicroBlogKey("parentKey", "sampleHost"))
        val base = createSampleStatus(currentUser)
        val quote = base.copy(statusKey = base.statusKey.copy(id = "quote"))
        val parent = createSampleStatus(parentUser)
        val filteredPost =
            base.copy(
                content = UiTranslatableText("".toUiPlainText()),
                images =
                    persistentListOf(
                        UiMedia.Image(
                            url = "https://example.com/image.jpg",
                            previewUrl = "https://example.com/image-preview.jpg",
                            description = null,
                            height = 100f,
                            width = 100f,
                            sensitive = false,
                        ),
                        UiMedia.Video(
                            url = "https://example.com/video.mp4",
                            thumbnailUrl = "https://example.com/video.jpg",
                            description = null,
                            height = 100f,
                            width = 100f,
                        ),
                    ),
            )
        val repost = filteredPost.copy(statusKey = base.statusKey.copy(id = "repost"))
        val filtered =
            UiTimelineV2.TimelinePostItem(
                post = filteredPost,
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents = persistentListOf(UiTimelineV2.TimelinePostItem(parent)),
                        quotes = persistentListOf(quote),
                        repost = repost,
                    ),
            )

        val traits = filtered.traits()

        assertEquals(
            setOf(TimelinePostKind.Reply, TimelinePostKind.Repost, TimelinePostKind.Quote),
            traits.kinds,
        )
        assertEquals(
            setOf(TimelinePostContent.Image, TimelinePostContent.Video),
            traits.contents,
        )
    }

    @Test
    fun matchesTimelineFilterExcludesConfiguredKindsAndContents() {
        val currentUser = createSampleUser()
        val parentUser = currentUser.copy(key = MicroBlogKey("parentKey", "sampleHost"))
        val originalTextOnly = createSampleStatus(currentUser)
        val replyPost =
            originalTextOnly.copy(
                statusKey = originalTextOnly.statusKey.copy(id = "reply"),
                images =
                    persistentListOf(
                        UiMedia.Image(
                            url = "https://example.com/reply.jpg",
                            previewUrl = "https://example.com/reply-preview.jpg",
                            description = null,
                            height = 100f,
                            width = 100f,
                            sensitive = false,
                        ),
                    ),
            )
        val replyWithImage =
            UiTimelineV2.TimelinePostItem(
                post = replyPost,
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents = persistentListOf(UiTimelineV2.TimelinePostItem(createSampleStatus(parentUser))),
                    ),
            )
        val filter =
            TimelineFilterConfig(
                excludedKinds = listOf(TimelinePostKind.Reply),
                excludedContents = listOf(TimelinePostContent.Image),
            )

        assertTrue(originalTextOnly.matchesTimelineFilter(filter))
        assertFalse(replyWithImage.matchesTimelineFilter(filter))
    }

    @Test
    fun postTraitsOnlyMarksReplyWhenParentUserDiffersFromCurrentUser() {
        val currentUser = createSampleUser()
        val original = createSampleStatus(currentUser)
        val selfThreadPost =
            original.copy(
                statusKey = original.statusKey.copy(id = "self-thread"),
            )
        val selfThread =
            UiTimelineV2.TimelinePostItem(
                post = selfThreadPost,
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents = persistentListOf(UiTimelineV2.TimelinePostItem(createSampleStatus(currentUser))),
                    ),
            )
        val replyToOtherUser =
            UiTimelineV2.TimelinePostItem(
                post = original.copy(statusKey = original.statusKey.copy(id = "reply-to-other-user")),
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents =
                            persistentListOf(
                                UiTimelineV2.TimelinePostItem(
                                    createSampleStatus(
                                        currentUser.copy(key = MicroBlogKey("parentKey", "sampleHost")),
                                    ),
                                ),
                            ),
                    ),
            )

        assertFalse(TimelinePostKind.Reply in selfThread.traits().kinds)
        assertTrue(TimelinePostKind.Reply in replyToOtherUser.traits().kinds)
    }

    @Test
    fun replyToUnfollowedFilterOnlyExcludesRepliesWithKnownUnfollowedParents() {
        val author = createSampleUser()
        val parentKey = MicroBlogKey("parentKey", "sampleHost")

        fun replyToParent(isFollowing: Boolean?): UiTimelineV2.TimelinePostItem {
            val parent =
                createSampleStatus(
                    author.copy(
                        key = parentKey,
                        isFollowing = isFollowing,
                    ),
                )
            return UiTimelineV2.TimelinePostItem(
                post = createSampleStatus(author),
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents = persistentListOf(UiTimelineV2.TimelinePostItem(parent)),
                    ),
            )
        }

        val filter = TimelineFilterConfig(excludedKinds = listOf(TimelinePostKind.ReplyToUnfollowed))

        assertFalse(replyToParent(isFollowing = false).matchesTimelineFilter(filter))
        assertTrue(replyToParent(isFollowing = true).matchesTimelineFilter(filter))
        assertTrue(replyToParent(isFollowing = null).matchesTimelineFilter(filter))
    }

    @Test
    fun replyToUnfollowedFilterKeepsSelfReplies() {
        val author = createSampleUser()
        val selfThread =
            UiTimelineV2.TimelinePostItem(
                post = createSampleStatus(author),
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents =
                            persistentListOf(
                                UiTimelineV2.TimelinePostItem(
                                    createSampleStatus(author.copy(isFollowing = false)),
                                ),
                            ),
                    ),
            )
        val filter = TimelineFilterConfig(excludedKinds = listOf(TimelinePostKind.ReplyToUnfollowed))

        assertFalse(TimelinePostKind.Reply in selfThread.traits().kinds)
        assertFalse(TimelinePostKind.ReplyToUnfollowed in selfThread.traits().kinds)
        assertTrue(selfThread.matchesTimelineFilter(filter))
    }

    @Test
    fun accountRelationOverridesStaleParentFollowingSnapshot() {
        val author = createSampleUser()
        val parentKey = MicroBlogKey("parentKey", "sampleHost")
        val replyPost = createSampleStatus(author)
        val parentPost =
            createSampleStatus(author.copy(key = parentKey, isFollowing = false))
                .copy(accountType = replyPost.accountType)
        val reply =
            UiTimelineV2.TimelinePostItem(
                post = replyPost,
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents = persistentListOf(UiTimelineV2.TimelinePostItem(parentPost)),
                    ),
            )
        val filter = TimelineFilterConfig(excludedKinds = listOf(TimelinePostKind.ReplyToUnfollowed))
        val relationKey = (parentPost.accountType as DbAccountType) to parentKey

        assertFalse(reply.matchesTimelineFilter(filter))
        assertTrue(reply.matchesTimelineFilter(filter, mapOf(relationKey to true)))
        assertFalse(reply.matchesTimelineFilter(filter, mapOf(relationKey to false)))
    }

    @Test
    fun matchesKeywordFiltersUsesRegexForRegexRules() {
        val status =
            createSampleStatus(createSampleUser()).copy(
                content = UiTranslatableText("Hello Kotlin 2.0".toUiPlainText()),
            )

        assertFalse(
            status.matchesKeywordFilters(
                listOf(
                    KeywordFilterPattern(
                        keyword = "hello\\s+kotlin\\s+\\d+\\.\\d+",
                        isRegex = true,
                        regex = Regex("hello\\s+kotlin\\s+\\d+\\.\\d+", setOf(RegexOption.IGNORE_CASE)),
                    ),
                ),
            ),
        )
        assertTrue(
            status.matchesKeywordFilters(
                listOf(
                    KeywordFilterPattern(
                        keyword = "^bye",
                        isRegex = true,
                        regex = Regex("^bye", setOf(RegexOption.IGNORE_CASE)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun matchesKeywordFiltersChecksInternalRepostContent() {
        val base = createSampleStatus(createSampleUser())
        val original =
            base.copy(
                statusKey = base.statusKey.copy(id = "original"),
                content = UiTranslatableText("Visible original #blocked".toUiPlainText()),
            )
        val repostWrapper =
            UiTimelineV2.TimelinePostItem(
                post =
                    base.copy(
                        statusKey = original.statusKey.copy(id = "repost"),
                        content = UiTranslatableText("".toUiPlainText()),
                    ),
                presentation = UiTimelineV2.PostPresentation(repost = original),
            )

        assertFalse(
            repostWrapper.matchesKeywordFilters(
                listOf(
                    KeywordFilterPattern(
                        keyword = "#blocked",
                        isRegex = false,
                    ),
                ),
            ),
        )
    }

    @Test
    fun matchesKeywordFiltersDoesNotSearchNestedInternalRepostContent() {
        val base = createSampleStatus(createSampleUser())
        val nestedOriginal =
            base.copy(
                statusKey = base.statusKey.copy(id = "nested-original"),
                content = UiTranslatableText("Nested original #deepblocked".toUiPlainText()),
            )
        val directRepost =
            base.copy(
                statusKey = base.statusKey.copy(id = "direct-repost"),
                content = UiTranslatableText("".toUiPlainText()),
            )
        val repostWrapper =
            UiTimelineV2.TimelinePostItem(
                post =
                    base.copy(
                        statusKey = base.statusKey.copy(id = "repost-wrapper"),
                        content = UiTranslatableText("".toUiPlainText()),
                    ),
                presentation = UiTimelineV2.PostPresentation(repost = directRepost),
            )

        assertTrue(
            repostWrapper.matchesKeywordFilters(
                listOf(
                    KeywordFilterPattern(
                        keyword = "#deepblocked",
                        isRegex = false,
                    ),
                ),
            ),
        )
    }

    @Test
    fun matchesKeywordFiltersIgnoresInvalidRegexRules() {
        val status =
            createSampleStatus(createSampleUser()).copy(
                content = UiTranslatableText("Hello Kotlin".toUiPlainText()),
            )

        assertTrue(
            status.matchesKeywordFilters(
                listOf(
                    KeywordFilterPattern(
                        keyword = "(",
                        isRegex = true,
                        regex = null,
                    ),
                ),
            ),
        )
    }
}
