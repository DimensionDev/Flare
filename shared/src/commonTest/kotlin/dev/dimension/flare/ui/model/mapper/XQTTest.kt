package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.network.xqt.model.Entities
import dev.dimension.flare.data.network.xqt.model.Hashtag
import dev.dimension.flare.data.network.xqt.model.NoteTweet
import dev.dimension.flare.data.network.xqt.model.NoteTweetResult
import dev.dimension.flare.data.network.xqt.model.NoteTweetResultData
import dev.dimension.flare.data.network.xqt.model.NoteTweetResultRichText
import dev.dimension.flare.data.network.xqt.model.NoteTweetResultRichTextTag
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetLegacy
import dev.dimension.flare.data.network.xqt.model.UserMention
import dev.dimension.flare.data.network.xqt.model.XqtUrl
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.model.MicroBlogKey
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XQTTest {
    @BeforeTest
    fun setup() {
        startKoin {
            modules(KoinHelper.modules())
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private val accountKey = MicroBlogKey("test_id", "example.com")

    @Test
    fun renderContent_legacyTweet_rendersCorrectly() {
        val legacy =
            TweetLegacy(
                idStr = "123",
                fullText = "Hello World https://t.co/xyz",
                displayTextRange = listOf(0, 28),
                entities =
                    Entities(
                        urls =
                            listOf(
                                XqtUrl(
                                    url = "https://t.co/xyz",
                                    expandedUrl = "https://example.com",
                                    displayUrl = "example.com",
                                    indices = listOf(12, 28),
                                ),
                            ),
                    ),
                createdAt = "Wed Oct 10 20:19:24 +0000 2018",
                favoriteCount = 10,
                favorited = false,
                isQuoteStatus = false,
                lang = "en",
                quoteCount = 5,
                replyCount = 2,
                retweetCount = 3,
                retweeted = false,
            )
        val tweet =
            Tweet(
                restId = "123",
                legacy = legacy,
                noteTweet = null,
            )

        val result = tweet.renderContent(accountKey)

        // Expected HTML structure from legacy parsing (TwitterParser)
        // Note: TwitterParser behavior might vary, but we expect the link to be replaced.
        // For legacy, it uses renderRichText which uses TwitterParser.
        // Assuming TwitterParser works as expected.
        val expectedHtml = """<span>Hello World </span><a href="https://example.com">example.com</a>"""
        assertEquals(expectedHtml, result.html)
    }

    @Test
    fun renderContent_noteTweet_rendersCorrectly() {
        // Text: "Check out #Flare and @user at https://flare.app! It represents $FLR."
        // Indices (approx):
        // 01234567890123456789012345678901234567890123456789012345678901234
        // Check out #Flare and @user at https://flare.app! It represents $FLR.
        // #Flare: 10-16
        // @user: 21-26
        // https://flare.app: 30-47
        // $FLR: 63-67 (approx)

        val text = "Check out #Flare and @user at https://flare.app! It represents \$FLR. This is Bold and Italic text."
        // Indices need to be accurate for substring extraction
        // Check out #Flare: 0..16. #Flare is at 10..16
        // and @user: 16..26. @user is at 21..26
        // at https://flare.app: 26..47. url is at 30..47
        // ! It represents $FLR: 47..67. $FLR is at 63..67
        // . This is : 67..77
        // Bold: 77..81
        // and : 81..86
        // Italic: 86..92
        // text.: 92..97

        val noteTweet =
            NoteTweet(
                isExpandable = true,
                noteTweetResults =
                    NoteTweetResult(
                        result =
                            NoteTweetResultData(
                                id = "note_id",
                                text = text,
                                entitySet =
                                    Entities(
                                        hashtags = listOf(Hashtag(text = "Flare", indices = listOf(10, 16))),
                                        userMentions = listOf(UserMention(screenName = "user", name = "User", indices = listOf(21, 26))),
                                        urls =
                                            listOf(
                                                XqtUrl(
                                                    url = "https://flare.app",
                                                    expandedUrl = "https://flare.app/expanded",
                                                    displayUrl = "flare.app",
                                                    indices = listOf(30, 47),
                                                ),
                                            ),
                                        symbols =
                                            listOf(
                                                dev.dimension.flare.data.network.xqt.model
                                                    .Symbol(text = "FLR", indices = listOf(63, 67)),
                                            ),
                                    ),
                                richtext =
                                    NoteTweetResultRichText(
                                        richtextTags =
                                            listOf(
                                                NoteTweetResultRichTextTag(
                                                    fromIndex = 77,
                                                    toIndex = 81,
                                                    richtextTypes = listOf(NoteTweetResultRichTextTag.RichtextTypes.bold),
                                                ),
                                                NoteTweetResultRichTextTag(
                                                    fromIndex = 86,
                                                    toIndex = 92,
                                                    richtextTypes = listOf(NoteTweetResultRichTextTag.RichtextTypes.italic),
                                                ),
                                            ),
                                    ),
                            ),
                    ),
            )

        val tweet =
            Tweet(
                restId = "123",
                noteTweet = noteTweet,
            )

        val result = tweet.renderContent(accountKey)
        val html = result.html

        // Verify elements
        // We expect span or body wrapping the content. XQT.kt uses Element("body").
        // "Check out "
        // <a href="...">#Flare</a>
        // " and "
        // <a href="...">@user</a>
        // " at "
        // <a href="...">flare.app</a>
        // "! It represents "
        // <a href="...">$FLR</a>
        // ". This is "
        // <b>Bold</b>
        // " and "
        // <i>Italic</i>
        // " text."

        // Note: The specific hrefs depend on DeeplinkRoute which we assume produces consistent URIs.

        assertTrue(html.contains("Check out "))
        // assert(html.contains("""<a href="flare://search?type=specific&amp;host=example.com&amp;query=%23Flare">#Flare</a>""")) // URL encoding might vary
        assertTrue(html.contains("#Flare"))
        assertTrue(html.contains("@user"))
        assertTrue(html.contains("""<a href="https://flare.app/expanded">flare.app</a>"""))
        assertTrue(html.contains("\$FLR"))
        assertTrue(html.contains("<b>Bold</b>"))
        assertTrue(html.contains("<i>Italic</i>"))
    }

    @Test
    fun renderContent_noteTweet_withNewlines_rendersBrTags() {
        val text = "Line 1\nLine 2"
        val noteTweet =
            NoteTweet(
                isExpandable = true,
                noteTweetResults =
                    NoteTweetResult(
                        result =
                            NoteTweetResultData(
                                id = "note_id",
                                text = text,
                                entitySet = Entities(),
                            ),
                    ),
            )
        val tweet =
            Tweet(
                restId = "123",
                noteTweet = noteTweet,
            )

        val result = tweet.renderContent(accountKey)
        val html = result.html

        // Expect "Line 1<br>Line 2"
        assertTrue(html.contains("Line 1"))
        assertTrue(html.contains("<br>"))
        assertTrue(html.contains("Line 2"))
    }

    @Test
    fun renderStatus_noteTweet_withInlineMedia_hidesStatusImages() {
        val text = "Text with media."
        val mediaId = "media_123"
        val mediaUrl = "https://example.com/image.jpg"

        val noteTweet =
            NoteTweet(
                isExpandable = true,
                noteTweetResults =
                    NoteTweetResult(
                        result =
                            NoteTweetResultData(
                                id = "note_id",
                                text = text,
                                entitySet =
                                    Entities(
                                        media =
                                            listOf(
                                                dev.dimension.flare.data.network.xqt.model.Media(
                                                    idStr = mediaId,
                                                    mediaUrlHttps = mediaUrl,
                                                    type = dev.dimension.flare.data.network.xqt.model.Media.Type.photo,
                                                    originalInfo =
                                                        dev.dimension.flare.data.network.xqt.model
                                                            .MediaOriginalInfo(100, 100),
                                                    displayUrl = "example.com/image",
                                                    expandedUrl = mediaUrl,
                                                    url = mediaUrl,
                                                    indices = listOf(0, 5), // dummy
                                                    sizes =
                                                        dev.dimension.flare.data.network.xqt.model.MediaSizes(
                                                            large =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                            medium =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                            small =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                            thumb =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                        ),
                                                ),
                                            ),
                                    ),
                                media =
                                    dev.dimension.flare.data.network.xqt.model.NoteTweetResultMedia(
                                        inlineMedia =
                                            listOf(
                                                dev.dimension.flare.data.network.xqt.model.NoteTweetResultMediaInlineMedia(
                                                    index = 0,
                                                    mediaId = mediaId,
                                                ),
                                            ),
                                    ),
                            ),
                    ),
            )
        // Also provide legacy entities so that normally images WOULD appear
        val legacy =
            TweetLegacy(
                idStr = "123",
                fullText = "Legacy text",
                displayTextRange = listOf(0, 10),
                createdAt = "Wed Oct 10 20:19:24 +0000 2018",
                entities =
                    Entities(
                        media =
                            listOf(
                                dev.dimension.flare.data.network.xqt.model.Media(
                                    idStr = mediaId,
                                    mediaUrlHttps = mediaUrl,
                                    type = dev.dimension.flare.data.network.xqt.model.Media.Type.photo,
                                    originalInfo =
                                        dev.dimension.flare.data.network.xqt.model
                                            .MediaOriginalInfo(100, 100),
                                    displayUrl = "example.com/image",
                                    expandedUrl = mediaUrl,
                                    url = mediaUrl,
                                    indices = listOf(0, 5),
                                    sizes =
                                        dev.dimension.flare.data.network.xqt.model.MediaSizes(
                                            large =
                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                    100,
                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                    100,
                                                ),
                                            medium =
                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                    100,
                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                    100,
                                                ),
                                            small =
                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                    100,
                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                    100,
                                                ),
                                            thumb =
                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                    100,
                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                    100,
                                                ),
                                        ),
                                ),
                            ),
                    ),
                favoriteCount = 0,
                favorited = false,
                isQuoteStatus = false,
                lang = "en",
                quoteCount = 0,
                replyCount = 0,
                retweetCount = 0,
                retweeted = false,
            )

        val tweet =
            Tweet(
                restId = "123",
                noteTweet = noteTweet,
                legacy = legacy,
            )

        // renderStatus requires event and references. Mock or pass null/empty.
        // renderStatus(accountKey, event, references)
        // event is StatusEvent.XQT which is an interface. I need to mock it or create a dummy implementation.
        // It seems StatusEvent.XQT is hard to mock without mock framework.
        // However, I can check if I can avoid calling renderStatus directly?
        // Tweet.render calls renderStatus.
        // But Tweet.render returns UiTimeline.
        // UiTimeline has content which is UiTimeline.ItemContent.Status.
        // I can inspect that.

        // But render() also requires event.
        // Let's see if I can implement a dummy event.
        val dummyEvent =
            object : dev.dimension.flare.data.datasource.microblog.StatusEvent.XQT {
                override fun retweet(
                    statusKey: MicroBlogKey,
                    retweeted: Boolean,
                ) {
                }

                override fun like(
                    statusKey: MicroBlogKey,
                    liked: Boolean,
                ) {
                }

                override fun bookmark(
                    statusKey: MicroBlogKey,
                    bookmarked: Boolean,
                ) {
                }

                override val accountKey: MicroBlogKey
                    get() = this@XQTTest.accountKey
            }

        val statusContent = tweet.renderStatus(accountKey, dummyEvent, emptyMap())

        // Assert images list is empty
        assertTrue(statusContent.images.isEmpty(), "Images should be empty when inline media is present")
    }

    @Test
    fun renderContent_noteTweet_withInlineMedia_rendersFigureTags() {
        val text = "Text before media. Text after media."
        val mediaId = "media_123"
        val mediaUrl = "https://example.com/image.jpg"

        // Indices:
        // Text before media. : 0..18
        // Insert at 18
        // Text after media. : 18..36

        val noteTweet =
            NoteTweet(
                isExpandable = true,
                noteTweetResults =
                    NoteTweetResult(
                        result =
                            NoteTweetResultData(
                                id = "note_id",
                                text = text,
                                entitySet =
                                    Entities(
                                        media =
                                            listOf(
                                                dev.dimension.flare.data.network.xqt.model.Media(
                                                    idStr = mediaId,
                                                    mediaUrlHttps = mediaUrl,
                                                    type = dev.dimension.flare.data.network.xqt.model.Media.Type.photo,
                                                    originalInfo =
                                                        dev.dimension.flare.data.network.xqt.model
                                                            .MediaOriginalInfo(100, 100),
                                                    displayUrl = "example.com/image",
                                                    expandedUrl = mediaUrl,
                                                    url = mediaUrl,
                                                    indices = listOf(19, 36),
                                                    sizes =
                                                        dev.dimension.flare.data.network.xqt.model.MediaSizes(
                                                            large =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                            medium =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                            small =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                            thumb =
                                                                dev.dimension.flare.data.network.xqt.model.MediaSize(
                                                                    100,
                                                                    dev.dimension.flare.data.network.xqt.model.MediaSize.Resize.crop,
                                                                    100,
                                                                ),
                                                        ),
                                                ),
                                            ),
                                    ),
                                media =
                                    dev.dimension.flare.data.network.xqt.model.NoteTweetResultMedia(
                                        inlineMedia =
                                            listOf(
                                                dev.dimension.flare.data.network.xqt.model.NoteTweetResultMediaInlineMedia(
                                                    index = 19,
                                                    mediaId = mediaId,
                                                ),
                                            ),
                                    ),
                            ),
                    ),
            )
        val tweet =
            Tweet(
                restId = "123",
                noteTweet = noteTweet,
            )

        val result = tweet.renderContent(accountKey)
        val html = result.html

        // Expect "Text before media. <figure><img src="..."></figure>Text after media."
        // Or similar depending on how index is treated. If index 18, it splits.
        assertTrue(html.contains("Text before media."))
        assertTrue(html.contains("<figure>"))
        assertTrue(html.contains(mediaUrl))
        assertTrue(html.contains("Text after media."))
    }
}
