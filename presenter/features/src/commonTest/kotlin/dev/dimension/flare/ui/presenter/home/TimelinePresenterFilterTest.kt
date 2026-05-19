package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostContent
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.local.KeywordFilterPattern
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
        val base = createSampleStatus(createSampleUser())
        val repost = base.copy(statusKey = base.statusKey.copy(id = "repost"))
        val quote = base.copy(statusKey = base.statusKey.copy(id = "quote"))
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
                replyToHandle = "@flare",
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
        val originalTextOnly = createSampleStatus(createSampleUser())
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
                replyToHandle = "@flare",
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
