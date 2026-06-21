package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.network.xqt.model.Entities
import dev.dimension.flare.data.network.xqt.model.Hashtag
import dev.dimension.flare.data.network.xqt.model.NoteTweet
import dev.dimension.flare.data.network.xqt.model.NoteTweetResult
import dev.dimension.flare.data.network.xqt.model.NoteTweetResultData
import dev.dimension.flare.data.network.xqt.model.NoteTweetResultRichText
import dev.dimension.flare.data.network.xqt.model.NoteTweetResultRichTextTag
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetLegacy
import dev.dimension.flare.data.network.xqt.model.TwitterArticle
import dev.dimension.flare.data.network.xqt.model.TwitterArticleBlock
import dev.dimension.flare.data.network.xqt.model.TwitterArticleContentState
import dev.dimension.flare.data.network.xqt.model.TwitterArticleEntity
import dev.dimension.flare.data.network.xqt.model.TwitterArticleEntityData
import dev.dimension.flare.data.network.xqt.model.TwitterArticleEntityEntry
import dev.dimension.flare.data.network.xqt.model.TwitterArticleEntityRange
import dev.dimension.flare.data.network.xqt.model.TwitterArticleInlineStyleRange
import dev.dimension.flare.data.network.xqt.model.TwitterArticleMedia
import dev.dimension.flare.data.network.xqt.model.TwitterArticleMediaInfo
import dev.dimension.flare.data.network.xqt.model.TwitterArticleMediaItem
import dev.dimension.flare.data.network.xqt.model.TwitterArticleResult
import dev.dimension.flare.data.network.xqt.model.TwitterArticleResults
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserLegacy
import dev.dimension.flare.data.network.xqt.model.UserMention
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.XqtUrl
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiArticleAuthor
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.route.DeeplinkRoute
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class XQTTest {
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

    private val accountKey = MicroBlogKey("test_id", "example.com")

    @Test
    fun parseXQTCustomDateTime_appliesTimezoneOffset() {
        val parsedPlus = parseXQTCustomDateTime("Wed Mar 04 14:17:49 +0900 2026")
        val parsedMinus = parseXQTCustomDateTime("Wed Mar 04 14:17:49 -0500 2026")

        assertEquals(Instant.parse("2026-03-04T05:17:49Z"), parsedPlus)
        assertEquals(Instant.parse("2026-03-04T19:17:49Z"), parsedMinus)
    }

    @Test
    fun parseXQTCustomDateTime_supportsDoubleSpacesInDayField() {
        val parsed = parseXQTCustomDateTime("Wed Mar  4 14:17:49 +0000 2026")

        assertEquals(Instant.parse("2026-03-04T14:17:49Z"), parsed)
    }

    @Test
    fun renderStatus_articleCard_usesGenericArticleRouteWithStatusKey() {
        val tweet =
            createArticleTweet(
                id = "tweet-123",
                article =
                    TwitterArticleResult(
                        title = "Article title",
                        previewText = "Article preview",
                    ),
            )

        val card = tweet.renderStatus(accountKey).card
        val route = DeeplinkRoute.parse(card?.url.orEmpty())

        val articleRoute = assertIs<DeeplinkRoute.Article>(route)
        assertEquals(MicroBlogKey("tweet-123", accountKey.host), articleRoute.articleKey)
        assertEquals("Article title", card?.title)
    }

    @Test
    fun renderArticle_outputsGenericArticleBlocksAndImageBlocks() {
        val articleImageUrl = "https://pbs.twimg.com/article/image.jpg"
        val tweet =
            createArticleTweet(
                id = "tweet-456",
                user = createUser(id = "user-456", screenName = "flare_dev"),
                article =
                    TwitterArticleResult(
                        title = "Longform title",
                        previewText = "Longform preview",
                        coverMedia =
                            TwitterArticleMedia(
                                mediaId = "cover",
                                mediaInfo =
                                    TwitterArticleMediaInfo(
                                        originalImgUrl = "https://pbs.twimg.com/article/cover.jpg",
                                        originalImgWidth = 1200,
                                        originalImgHeight = 630,
                                    ),
                            ),
                        mediaEntities =
                            listOf(
                                TwitterArticleMedia(
                                    mediaId = "media-1",
                                    mediaInfo =
                                        TwitterArticleMediaInfo(
                                            originalImgUrl = articleImageUrl,
                                            originalImgWidth = 800,
                                            originalImgHeight = 600,
                                        ),
                                ),
                            ),
                        contentState =
                            TwitterArticleContentState(
                                blocks =
                                    listOf(
                                        TwitterArticleBlock(
                                            key = "text-block",
                                            text = "Hello link",
                                            type = "header-one",
                                            entityRanges =
                                                listOf(
                                                    TwitterArticleEntityRange(
                                                        key = 0,
                                                        offset = 6,
                                                        length = 4,
                                                    ),
                                                ),
                                            inlineStyleRanges =
                                                listOf(
                                                    TwitterArticleInlineStyleRange(
                                                        offset = 0,
                                                        length = 5,
                                                        style = "bold",
                                                    ),
                                                ),
                                        ),
                                        TwitterArticleBlock(
                                            key = "media-block",
                                            type = "atomic",
                                            entityRanges =
                                                listOf(
                                                    TwitterArticleEntityRange(
                                                        key = 1,
                                                        offset = 0,
                                                        length = 1,
                                                    ),
                                                ),
                                        ),
                                    ),
                                entityMap =
                                    listOf(
                                        TwitterArticleEntityEntry(
                                            key = "0",
                                            value =
                                                TwitterArticleEntity(
                                                    type = "LINK",
                                                    data = TwitterArticleEntityData(url = "https://example.com/link"),
                                                ),
                                        ),
                                        TwitterArticleEntityEntry(
                                            key = "1",
                                            value =
                                                TwitterArticleEntity(
                                                    type = "MEDIA",
                                                    data =
                                                        TwitterArticleEntityData(
                                                            caption = "Image caption",
                                                            mediaItems =
                                                                listOf(
                                                                    TwitterArticleMediaItem(mediaId = "media-1"),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                    ),
                            ),
                    ),
            )

        val article = tweet.renderArticle(accountKey) ?: error("Expected article")

        assertEquals("tweet-456@example.com", article.key)
        assertEquals("Longform title", article.title)
        assertEquals("https://example.com/flare_dev/status/tweet-456", article.sourceUrl)
        assertEquals(Instant.parse("2018-10-10T20:19:24Z"), article.publishDate?.value)
        assertEquals("https://pbs.twimg.com/article/cover.jpg", article.cover?.url)
        val author = assertIs<UiArticleAuthor.Profile>(article.author)
        assertEquals("flare_dev", author.profile.handle.raw)

        val textBlock = assertIs<UiArticleBlock.Text>(article.content.blocks[0])
        assertEquals(1, textBlock.content.block.headingLevel)
        val textRuns = textBlock.content.runs.filterIsInstance<RenderRun.Text>()
        assertTrue(textRuns.any { it.text == "Hello" && it.style.bold })
        assertTrue(textRuns.any { it.text == "link" && it.style.link == "https://example.com/link" })
        assertTrue(textBlock.richText.renderRuns.none { it is RenderContent.BlockImage })

        val imageBlock = assertIs<UiArticleBlock.Image>(article.content.blocks[1])
        assertEquals(articleImageUrl, imageBlock.media.url)
        assertEquals(800f, imageBlock.media.width)
        assertEquals(600f, imageBlock.media.height)
        assertEquals("Image caption", imageBlock.media.description)
    }

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
        assertEquals("Hello World example.com", result.innerText)
        val linkRun = result.allTextRuns().last()
        assertEquals("example.com", linkRun.text)
        assertEquals("https://example.com", linkRun.style.link)
    }

    @Test
    fun renderContent_noteTweet_rendersCorrectly() {
        val text = "Check out #Flare and @user at https://flare.app! It represents \$FLR. This is Bold and Italic text."

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
        val textRuns = result.allTextRuns()

        assertTrue(result.innerText.contains("Check out "))
        assertTrue(textRuns.any { it.text.contains("#Flare") })
        assertTrue(textRuns.any { it.text.contains("@user") })
        assertTrue(textRuns.any { it.text == "flare.app" && it.style.link == "https://flare.app/expanded" })
        assertTrue(result.innerText.contains("\$FLR"))
        assertTrue(textRuns.any { it.text == "Bold" && it.style.bold })
        assertTrue(textRuns.any { it.text == "Italic" && it.style.italic })
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
        assertEquals("Line 1\nLine 2", result.raw)
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

        val statusContent = tweet.renderStatus(accountKey)

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
                                entitySet = Entities(),
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
                legacy =
                    TweetLegacy(
                        idStr = "123",
                        fullText = "Legacy text",
                        favoriteCount = 0,
                        favorited = false,
                        isQuoteStatus = false,
                        lang = "en",
                        quoteCount = 0,
                        replyCount = 0,
                        retweetCount = 0,
                        retweeted = false,
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
                    ),
            )

        val result = tweet.renderContent(accountKey)
        assertTrue(result.innerText.contains("Text before media."))
        assertTrue(result.innerText.contains("Text after media."))
        assertTrue(result.renderRuns.any { it is RenderContent.BlockImage && it.url == mediaUrl })
    }

    @Test
    fun renderContent_complexEntities_rendersCorrectly() {
        // Text: "Start 🫠 @user End"
        // Indices (UTF-16):
        // "Start " -> 0..5 (6 chars)
        // "🫠" -> 6..7 (2 chars, 1 code point)
        // " " -> 8 (1 char)
        // "@user" -> 9..13 (5 chars)
        // " " -> 14 (1 char)
        // "End" -> 15..17 (3 chars)
        val text = "Start 🫠 @user End"

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
                                        userMentions =
                                            listOf(
                                                UserMention(
                                                    screenName = "user",
                                                    name = "User",
                                                    indices = listOf(8, 13),
                                                ),
                                            ),
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
                                                    index = 14, // UTF-16 index before " " (Space before End)
                                                    mediaId = mediaId,
                                                ),
                                            ),
                                    ),
                                richtext =
                                    NoteTweetResultRichText(
                                        richtextTags =
                                            listOf(
                                                NoteTweetResultRichTextTag(
                                                    fromIndex = 0,
                                                    toIndex = 5,
                                                    richtextTypes = listOf(NoteTweetResultRichTextTag.RichtextTypes.bold),
                                                ),
                                                NoteTweetResultRichTextTag(
                                                    fromIndex = 15,
                                                    toIndex = 18,
                                                    richtextTypes = listOf(NoteTweetResultRichTextTag.RichtextTypes.italic),
                                                ),
                                            ),
                                    ),
                            ),
                    ),
            )
        // Legacy needed for resolving media URL in inline media processing
        val legacy =
            TweetLegacy(
                idStr = "123",
                fullText = "Legacy text",
                displayTextRange = listOf(0, 10),
                createdAt = "Wed Oct 10 20:19:24 +0000 2018",
                favoriteCount = 0,
                favorited = false,
                isQuoteStatus = false,
                lang = "en",
                quoteCount = 0,
                replyCount = 0,
                retweetCount = 0,
                retweeted = false,
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
            )

        val tweet =
            Tweet(
                restId = "123",
                noteTweet = noteTweet,
                legacy = legacy,
            )

        val result = tweet.renderContent(accountKey)
        val textRuns = result.allTextRuns()
        assertTrue(textRuns.any { it.text == "Start" && it.style.bold }, "Start should be bold")
        assertTrue(result.innerText.contains("🫠"), "Emoji preserved")
        assertTrue(textRuns.any { it.text == "@user" && it.style.link != null }, "User mention linked")
        assertTrue(result.renderRuns.any { it is RenderContent.BlockImage && it.url == mediaUrl }, "Media figure present")
        assertTrue(textRuns.any { it.text == "End" && it.style.italic }, "End should be italic")
    }

    private fun createArticleTweet(
        id: String,
        article: TwitterArticleResult,
        user: User = createUser(),
    ): Tweet =
        Tweet(
            restId = id,
            core = UserResultCore(UserResults(user)),
            article = TwitterArticle(TwitterArticleResults(article)),
            legacy =
                TweetLegacy(
                    idStr = id,
                    fullText = "",
                    displayTextRange = listOf(0, 0),
                    entities = Entities(),
                    createdAt = "Wed Oct 10 20:19:24 +0000 2018",
                    favoriteCount = 0,
                    favorited = false,
                    isQuoteStatus = false,
                    lang = "en",
                    quoteCount = 0,
                    replyCount = 0,
                    retweetCount = 0,
                    retweeted = false,
                    userIdStr = user.restId,
                ),
        )

    private fun createUser(
        id: String = "user-123",
        screenName: String = "article_author",
    ): User =
        User(
            restId = id,
            legacy =
                UserLegacy(
                    name = screenName,
                    screenName = screenName,
                    profileImageUrlHttps = "https://pbs.twimg.com/profile_images/$screenName.jpg",
                ),
        )

    private fun dev.dimension.flare.ui.render.UiRichText.allTextRuns(): List<RenderRun.Text> =
        renderRuns
            .filterIsInstance<RenderContent.Text>()
            .flatMap { it.runs }
            .filterIsInstance<RenderRun.Text>()
}
