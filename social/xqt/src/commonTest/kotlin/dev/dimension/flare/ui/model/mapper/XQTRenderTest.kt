package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.mapper.XQTTimeline
import dev.dimension.flare.data.network.xqt.model.Entities
import dev.dimension.flare.data.network.xqt.model.GrokTranslatedPost
import dev.dimension.flare.data.network.xqt.model.GrokTranslatedPostWithAvailability
import dev.dimension.flare.data.network.xqt.model.ItemResult
import dev.dimension.flare.data.network.xqt.model.Media
import dev.dimension.flare.data.network.xqt.model.MediaOriginalInfo
import dev.dimension.flare.data.network.xqt.model.MediaSize
import dev.dimension.flare.data.network.xqt.model.MediaSizes
import dev.dimension.flare.data.network.xqt.model.MediaVideoInfo
import dev.dimension.flare.data.network.xqt.model.MediaVideoInfoVariant
import dev.dimension.flare.data.network.xqt.model.TimelineTweet
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetLegacy
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserLegacy
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.XqtUrl
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class XQTRenderTest {
    private val accountKey = MicroBlogKey(id = "me", host = "x.com")

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
    fun mediaIsRecognized() {
        val status =
            createTweet(
                id = "status-media",
                user = createUser("user-media", "media_user"),
                text = "post with media",
                media =
                    listOf(
                        createPhotoMedia("img-1", "https://pbs.twimg.com/media/image.jpg", 1200, 800),
                        createVideoMedia("vid-1", "https://pbs.twimg.com/media/video.jpg", "https://video.twimg.com/video.mp4", 1920, 1080),
                    ),
            )

        val rendered = rootPostOf(status.render(accountKey))
        assertEquals(2, rendered.images.size)

        val image = assertIs<UiMedia.Image>(rendered.images[0])
        assertEquals("https://pbs.twimg.com/media/image.jpg?name=orig", image.url)
        assertEquals("https://pbs.twimg.com/media/image.jpg", image.previewUrl)
        assertEquals(1200f, image.width)
        assertEquals(800f, image.height)

        val video = assertIs<UiMedia.Video>(rendered.images[1])
        assertEquals("https://video.twimg.com/video.mp4", video.url)
        assertEquals("https://pbs.twimg.com/media/video.jpg", video.thumbnailUrl)
        assertEquals(1920f, video.width)
        assertEquals(1080f, video.height)
    }

    @Test
    fun quoteIsRecognized() {
        val quoted =
            createTweet(
                id = "status-quoted",
                user = createUser("user-quoted", "quoted_user"),
                text = "quoted content",
            )
        val status =
            createTweet(
                id = "status-main",
                user = createUser("user-main", "main_user"),
                text = "main content",
                quotedStatus = quoted,
            )

        val rendered = timelinePostItemOf(status.render(accountKey))
        assertEquals(1, rendered.presentation.quotes.size)
        assertEquals(
            "quoted content",
            rendered.presentation.quotes
                .first()
                .content.original.innerText,
        )
    }

    @Test
    fun parentsAreRecognized() {
        val parent =
            createTweet(
                id = "status-parent",
                user = createUser("user-parent", "parent_user"),
                text = "parent content",
            )
        val child =
            createTweet(
                id = "status-child",
                user = createUser("user-child", "child_user"),
                text = "child content",
            )

        val rendered =
            timelinePostItemOf(
                assertNotNull(
                    createTimeline(
                        tweet = child,
                        parents = listOf(createTimeline(parent)),
                    ).render(accountKey),
                ),
            )
        assertEquals(1, rendered.presentation.inlineParents.size)
        assertEquals(
            "parent content",
            rendered.presentation.inlineParents
                .first()
                .content.original.innerText,
        )
        assertEquals(
            "status-parent",
            rendered.presentation.inlineParents
                .first()
                .statusKey.id,
        )
    }

    @Test
    fun repostShowsMessageAndUsesRepostedContent() {
        val original =
            createTweet(
                id = "status-original",
                user = createUser("user-original", "original_user"),
                text = "original content",
            )
        val repostWrapper =
            createTweet(
                id = "status-repost-wrapper",
                user = createUser("user-reposter", "reposter_user"),
                text = "wrapper content",
                retweetedStatus = original,
            )

        val rendered = timelinePostItemOf(assertNotNull(createTimeline(repostWrapper).render(accountKey)))
        val message = assertNotNull(rendered.presentation.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
        val repostInternal = assertNotNull(rendered.presentation.repost)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("user-reposter", message.user?.key?.id)
        assertEquals("wrapper content", rendered.post.content.original.innerText)
        assertEquals(
            "user-reposter",
            rendered.post.user
                ?.key
                ?.id,
        )
        assertEquals("status-repost-wrapper", rendered.statusKey.id)
        assertEquals("original content", repostInternal.content.original.innerText)
        assertEquals("user-original", repostInternal.user?.key?.id)
        assertEquals("status-original", repostInternal.statusKey.id)
    }

    @Test
    fun repostWithQuoteShowsMessagePostAndQuote() {
        val quoted =
            createTweet(
                id = "status-quoted-2",
                user = createUser("user-quoted-2", "quoted_user_2"),
                text = "quoted payload",
            )
        val original =
            createTweet(
                id = "status-original-2",
                user = createUser("user-original-2", "original_user_2"),
                text = "original payload",
                quotedStatus = quoted,
            )
        val repostWrapper =
            createTweet(
                id = "status-repost-wrapper-2",
                user = createUser("user-reposter-2", "reposter_user_2"),
                text = "wrapper payload",
                retweetedStatus = original,
            )

        val rendered = timelinePostItemOf(assertNotNull(createTimeline(repostWrapper).render(accountKey)))
        val message = assertNotNull(rendered.presentation.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
        val repostInternal = assertNotNull(rendered.presentation.repost)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("status-repost-wrapper-2", rendered.statusKey.id)
        assertEquals("wrapper payload", rendered.post.content.original.innerText)
        assertEquals("original payload", repostInternal.content.original.innerText)
        assertEquals(1, rendered.presentation.quotes.size)
        assertEquals(
            "quoted payload",
            rendered.presentation.quotes
                .first()
                .content.original.innerText,
        )
    }

    @Test
    fun repostMessageUserComesFromWrapperTweetUser() {
        val original =
            createTweet(
                id = "2029481529815253017",
                user = createUser("3656078773", "papeushikaeru"),
                text = "恋人の聖地に行った時の写真です",
            )
        val repostWrapper =
            createTweet(
                id = "2029691916246515890",
                user = createUser("128843027", "Moo_YOSHIO"),
                text = "RT @papeushikaeru: 恋人の聖地に行った時の写真です",
                retweetedStatus = original,
            )

        val rendered = timelinePostItemOf(assertNotNull(createTimeline(repostWrapper).render(accountKey)))
        val message = assertNotNull(rendered.presentation.message)
        val repostInternal = assertNotNull(rendered.presentation.repost)

        assertEquals(
            "128843027",
            rendered.post.user
                ?.key
                ?.id,
        )
        assertEquals("128843027", message.user?.key?.id)
        assertEquals("3656078773", repostInternal.user?.key?.id)
        assertEquals("2029691916246515890", rendered.statusKey.id)
        assertEquals("2029481529815253017", repostInternal.statusKey.id)
    }

    @Test
    fun platformTranslationUsesFullMatchingTranslationAndEntities() {
        val shortUrl = "https://t.co/translated"
        val expandedUrl = "https://example.com/translated"
        val status =
            createTweet(
                id = "status-translated",
                user = createUser("user-translated", "translated_user"),
                text = "original content",
                grokTranslation =
                    GrokTranslatedPostWithAvailability(
                        isAvailable = true,
                        data =
                            GrokTranslatedPost(
                                destinationLanguage = Locale.language.replace('-', '_').uppercase(),
                                previewTranslation = "truncated preview",
                                translation = "full translation $shortUrl",
                                entities =
                                    Entities(
                                        urls =
                                            listOf(
                                                XqtUrl(
                                                    url = shortUrl,
                                                    expandedUrl = expandedUrl,
                                                ),
                                            ),
                                    ),
                            ),
                    ),
            )

        val rendered = rootPostOf(status.render(accountKey))
        val translation = assertNotNull(rendered.content.translation)
        val link =
            translation.renderRuns
                .filterIsInstance<RenderContent.Text>()
                .flatMap { it.runs }
                .filterIsInstance<RenderRun.Text>()
                .first { it.style.link != null }

        assertEquals("original content", rendered.content.original.innerText)
        assertEquals("full translation example.com/translated", translation.innerText)
        assertEquals(expandedUrl, link.style.link)
    }

    @Test
    fun rejectsUnavailableEmptyPreviewOnlyAndWrongLanguageTranslations() {
        val matchingLanguage = Locale.language
        val wrongLanguage = if (matchingLanguage.startsWith("zz", ignoreCase = true)) "yy" else "zz"
        val rejected =
            listOf(
                GrokTranslatedPostWithAvailability(
                    isAvailable = false,
                    data = GrokTranslatedPost(destinationLanguage = matchingLanguage, translation = "unavailable"),
                ),
                GrokTranslatedPostWithAvailability(
                    isAvailable = true,
                    data = GrokTranslatedPost(destinationLanguage = matchingLanguage, translation = "", previewTranslation = "preview"),
                ),
                GrokTranslatedPostWithAvailability(
                    isAvailable = true,
                    data = GrokTranslatedPost(destinationLanguage = matchingLanguage, previewTranslation = "preview only"),
                ),
                GrokTranslatedPostWithAvailability(
                    isAvailable = true,
                    data = GrokTranslatedPost(destinationLanguage = wrongLanguage, translation = "wrong language"),
                ),
            )

        rejected.forEachIndexed { index, grokTranslation ->
            val rendered =
                rootPostOf(
                    createTweet(
                        id = "status-rejected-$index",
                        user = createUser("user-rejected-$index", "rejected_$index"),
                        text = "original",
                        grokTranslation = grokTranslation,
                    ).render(accountKey),
                )
            assertEquals(null, rendered.content.translation)
        }
    }

    private fun createTimeline(
        tweet: Tweet,
        parents: List<XQTTimeline> = emptyList(),
    ): XQTTimeline =
        XQTTimeline(
            parents = parents,
            tweets = TimelineTweet(tweetResults = ItemResult(result = tweet)),
            id = tweet.restId,
            sortedIndex = 1L,
        )

    private fun createTweet(
        id: String,
        user: User,
        text: String,
        media: List<Media> = emptyList(),
        retweetedStatus: Tweet? = null,
        quotedStatus: Tweet? = null,
        grokTranslation: GrokTranslatedPostWithAvailability? = null,
    ): Tweet =
        Tweet(
            restId = id,
            core = UserResultCore(userResults = UserResults(result = user)),
            legacy =
                TweetLegacy(
                    idStr = id,
                    fullText = text,
                    displayTextRange = listOf(0, text.length),
                    entities = Entities(media = media),
                    createdAt = "Wed Oct 10 20:19:24 +0000 2018",
                    favoriteCount = 0,
                    favorited = false,
                    isQuoteStatus = quotedStatus != null,
                    lang = "en",
                    quoteCount = if (quotedStatus != null) 1 else 0,
                    replyCount = 0,
                    retweetCount = if (retweetedStatus != null) 1 else 0,
                    retweeted = false,
                    userIdStr = user.restId,
                    retweetedStatusResult = retweetedStatus?.let { ItemResult(result = it) },
                ),
            quotedStatusResult = quotedStatus?.let { ItemResult(result = it) },
            grokTranslatedPostWithAvailability = grokTranslation,
        )

    private fun createUser(
        id: String,
        screenName: String,
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

    private fun createPhotoMedia(
        id: String,
        mediaUrl: String,
        width: Int,
        height: Int,
    ): Media =
        Media(
            idStr = id,
            displayUrl = mediaUrl,
            expandedUrl = mediaUrl,
            indices = listOf(0, 1),
            mediaUrlHttps = mediaUrl,
            originalInfo = MediaOriginalInfo(height = height, width = width),
            sizes = createSizes(width = width, height = height),
            type = Media.Type.photo,
            url = mediaUrl,
        )

    private fun rootPostOf(item: UiTimelineV2): UiTimelineV2.Post =
        when (item) {
            is UiTimelineV2.TimelinePostItem -> item.post
            is UiTimelineV2.Post -> item
            else -> error("Expected post timeline item, got ${item::class.simpleName}")
        }

    private fun timelinePostItemOf(item: UiTimelineV2): UiTimelineV2.TimelinePostItem = assertIs<UiTimelineV2.TimelinePostItem>(item)

    private fun createVideoMedia(
        id: String,
        mediaUrl: String,
        videoUrl: String,
        width: Int,
        height: Int,
    ): Media =
        Media(
            idStr = id,
            displayUrl = mediaUrl,
            expandedUrl = mediaUrl,
            indices = listOf(0, 1),
            mediaUrlHttps = mediaUrl,
            originalInfo = MediaOriginalInfo(height = height, width = width),
            sizes = createSizes(width = width, height = height),
            type = Media.Type.video,
            url = mediaUrl,
            videoInfo =
                MediaVideoInfo(
                    aspectRatio = listOf(width, height),
                    variants =
                        listOf(
                            MediaVideoInfoVariant(
                                contentType = "video/mp4",
                                url = videoUrl,
                                bitrate = 2048000,
                            ),
                        ),
                ),
        )

    private fun createSizes(
        width: Int,
        height: Int,
    ): MediaSizes {
        val size = MediaSize(h = height, resize = MediaSize.Resize.fit, w = width)
        return MediaSizes(
            large = size,
            medium = size,
            small = size,
            thumb = size,
        )
    }
}
