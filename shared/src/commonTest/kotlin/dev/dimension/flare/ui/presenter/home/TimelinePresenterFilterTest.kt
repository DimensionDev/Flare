package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostContent
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.repository.KeywordFilterPattern
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.context.startKoin
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
                    single<PlatformFormatter> { TestFormatter() }
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
        val repost = base.copy(statusKey = base.statusKey.copy(id = "repost"))
        val quote = base.copy(statusKey = base.statusKey.copy(id = "quote"))
        val parent = createSampleStatus(parentUser)
        val filtered =
            base.copy(
                content = "".toUiPlainText(),
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
                parents = persistentListOf(parent),
                quote = persistentListOf(quote),
                internalRepost = repost,
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
        val replyWithImage =
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
                parents = persistentListOf(createSampleStatus(parentUser)),
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
        val selfThread =
            original.copy(
                statusKey = original.statusKey.copy(id = "self-thread"),
                parents = persistentListOf(createSampleStatus(currentUser)),
            )
        val replyToOtherUser =
            original.copy(
                statusKey = original.statusKey.copy(id = "reply-to-other-user"),
                parents =
                    persistentListOf(
                        createSampleStatus(
                            currentUser.copy(key = MicroBlogKey("parentKey", "sampleHost")),
                        ),
                    ),
            )

        assertFalse(TimelinePostKind.Reply in selfThread.traits().kinds)
        assertTrue(TimelinePostKind.Reply in replyToOtherUser.traits().kinds)
    }

    @Test
    fun matchesKeywordFiltersUsesRegexForRegexRules() {
        val status =
            createSampleStatus(createSampleUser()).copy(
                content = "Hello Kotlin 2.0".toUiPlainText(),
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
                content = "Visible original #blocked".toUiPlainText(),
            )
        val repostWrapper =
            base.copy(
                statusKey = original.statusKey.copy(id = "repost"),
                content = "".toUiPlainText(),
                internalRepost = original,
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
                content = "Nested original #deepblocked".toUiPlainText(),
            )
        val directRepost =
            base.copy(
                statusKey = base.statusKey.copy(id = "direct-repost"),
                content = "".toUiPlainText(),
                internalRepost = nestedOriginal,
            )
        val repostWrapper =
            base.copy(
                statusKey = base.statusKey.copy(id = "repost-wrapper"),
                content = "".toUiPlainText(),
                internalRepost = directRepost,
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
                content = "Hello Kotlin".toUiPlainText(),
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
