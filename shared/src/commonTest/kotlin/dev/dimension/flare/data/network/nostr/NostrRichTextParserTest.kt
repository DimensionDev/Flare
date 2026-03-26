package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NostrRichTextParserTest {
    private val accountKey = MicroBlogKey("nostr-parser-test", NostrService.NOSTR_HOST)

    @BeforeTest
    fun setUp() {
        stopKoin()
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
    fun parsesUrlsHashtagsAndNewlines() {
        val text = "Visit https://example.com/long/path?q=1\n#nostr"
        val result = parseNostrRichText(text, accountKey)

        val content = assertIs<RenderContent.Text>(result.renderRuns.single())
        val runs = content.runs

        assertEquals("Visit ", assertIs<RenderRun.Text>(runs[0]).text)
        val urlRun = assertIs<RenderRun.Text>(runs[1])
        assertEquals("example.com/long/path?q=1", urlRun.text)
        assertEquals("https://example.com/long/path?q=1", urlRun.style.link)
        assertEquals("\n", assertIs<RenderRun.Text>(runs[2]).text)

        val hashtagRun = assertIs<RenderRun.Text>(runs[3])
        assertEquals("#nostr", hashtagRun.text)
        val route = DeeplinkRoute.parse(assertNotNull(hashtagRun.style.link))
        val search = assertIs<DeeplinkRoute.Search>(route)
        assertEquals("#nostr", search.query)
    }

    @Test
    fun parsesNpubAsProfileLink() {
        val pubKeyHex = "1111111111111111111111111111111111111111111111111111111111111111"
        val npub = bech32PublicKey(pubKeyHex)
        val result = parseNostrRichText("nostr:$npub", accountKey)

        val content = assertIs<RenderContent.Text>(result.renderRuns.single())
        val run = assertIs<RenderRun.Text>(content.runs.single())

        assertEquals("nostr:$npub", run.text)
        val route = DeeplinkRoute.parse(assertNotNull(run.style.link))
        val profile = assertIs<DeeplinkRoute.Profile.User>(route)
        assertEquals(pubKeyHex, profile.userKey.id)
    }

    @Test
    fun rendersNpubAsAtHandleWhenProfileIsAvailable() {
        val pubKeyHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val npub = bech32PublicKey(pubKeyHex)
        val result =
            parseNostrRichText(
                text = "nostr:$npub",
                accountKey = accountKey,
                profiles =
                    mapOf(
                        pubKeyHex to
                            UiProfile(
                                key = MicroBlogKey(pubKeyHex, NostrService.NOSTR_HOST),
                                handle = UiHandle(raw = "alice", host = NostrService.NOSTR_HOST),
                                avatar = "",
                                nameInternal = "Alice".toUiPlainText(),
                                platformType = PlatformType.Nostr,
                                clickEvent = ClickEvent.Noop,
                                banner = null,
                                description = null,
                                matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
                                mark = persistentListOf(),
                                bottomContent = null,
                            ),
                    ),
            )

        val content = assertIs<RenderContent.Text>(result.renderRuns.single())
        val run = assertIs<RenderRun.Text>(content.runs.single())
        assertEquals("@alice", run.text)
        val route = DeeplinkRoute.parse(assertNotNull(run.style.link))
        val profile = assertIs<DeeplinkRoute.Profile.User>(route)
        assertEquals(pubKeyHex, profile.userKey.id)
    }

    @Test
    fun extractsMentionedProfilePubkeysFromNostrUris() {
        val pubKeyHex = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val npub = bech32PublicKey(pubKeyHex)

        val result = extractMentionedProfilePubkeys("hello nostr:$npub")

        assertEquals(setOf(pubKeyHex), result)
    }

    @Test
    fun buildRenderContextCollectsMentionAndPreprocessedMedia() {
        val pubKeyHex = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val npub = bech32PublicKey(pubKeyHex)
        val mediaUrl = "https://cdn.example.com/final-image.webp"

        val context = buildNostrTextRenderContext("hello nostr:$npub $mediaUrl", emptyArray())

        assertEquals("hello nostr:$npub", context.preprocessedText.text)
        assertEquals(listOf(mediaUrl), context.preprocessedText.extractedMediaUrls)
        assertEquals(setOf(pubKeyHex), context.mentionedProfilePubKeys)
    }

    @Test
    fun parsesNoteAsStatusLinkAndLeavesTrailingPunctuationPlain() {
        val eventIdHex = "2222222222222222222222222222222222222222222222222222222222222222"
        val note =
            rust.nostr.sdk.EventId
                .parse(eventIdHex)
                .use { it.toBech32() }
        val result = parseNostrRichText("Check nostr:$note!", accountKey)

        val content = assertIs<RenderContent.Text>(result.renderRuns.single())
        assertEquals(3, content.runs.size)
        assertEquals("Check ", assertIs<RenderRun.Text>(content.runs[0]).text)

        val noteRun = assertIs<RenderRun.Text>(content.runs[1])
        val route = DeeplinkRoute.parse(assertNotNull(noteRun.style.link))
        val detail = assertIs<DeeplinkRoute.Status.Detail>(route)
        assertEquals(eventIdHex, detail.statusKey.id)
        assertTrue(noteRun.text.startsWith("nostr:note1"))

        assertEquals("!", assertIs<RenderRun.Text>(content.runs[2]).text)
    }

    @Test
    fun removesTaggedMediaTextAndTrimsTrailingBlankLine() {
        val mediaUrl = "https://cdn.example.com/media.png"
        val text = "hello\n$mediaUrl\n"
        val tags =
            arrayOf(
                arrayOf(
                    "imeta",
                    "url $mediaUrl",
                    "m image/png",
                ),
            )

        val result = parseNostrRichText(text, tags, accountKey)

        val content = assertIs<RenderContent.Text>(result.renderRuns.single())
        val run = assertIs<RenderRun.Text>(content.runs.single())
        assertEquals("hello", run.text)
        assertEquals("hello", result.innerText)
    }

    @Test
    fun removesTaggedMediaTextFromRTag() {
        val mediaUrl = "https://cdn.example.com/video.mp4"
        val text = "intro\n$mediaUrl"
        val tags =
            arrayOf(
                arrayOf("r", mediaUrl),
            )

        val result = parseNostrRichText(text, tags, accountKey)

        val content = assertIs<RenderContent.Text>(result.renderRuns.single())
        val run = assertIs<RenderRun.Text>(content.runs.single())
        assertEquals("intro", run.text)
    }

    @Test
    fun removesQuotedEventTextWhenQTagExists() {
        val eventIdHex = "3333333333333333333333333333333333333333333333333333333333333333"
        val note =
            rust.nostr.sdk.EventId
                .parse(eventIdHex)
                .use { it.toBech32() }
        val tags = arrayOf(arrayOf("q", eventIdHex))

        val result = parseNostrRichText("before nostr:$note\nafter", tags, accountKey)

        val content = assertIs<RenderContent.Text>(result.renderRuns.single())
        assertEquals("before\nafter", content.runs.joinToString(separator = "") { assertIs<RenderRun.Text>(it).text })
    }

    @Test
    fun preprocessExtractsTrailingMediaUrlFromText() {
        val mediaUrl = "https://cdn.example.com/final-image.jpg"
        val result = preprocessNostrText("hello\n$mediaUrl", emptyArray())

        assertEquals("hello", result.text)
        assertEquals(listOf(mediaUrl), result.extractedMediaUrls)
    }

    @Test
    fun preprocessExtractsTrailingMediaUrlButKeepsHashtagsOnSameLine() {
        val mediaUrl = "https://cdn.example.com/final-image.webp"
        val result = preprocessNostrText("#tumblr #openvibe $mediaUrl", emptyArray())

        assertEquals("#tumblr #openvibe", result.text)
        assertEquals(listOf(mediaUrl), result.extractedMediaUrls)
    }
}
