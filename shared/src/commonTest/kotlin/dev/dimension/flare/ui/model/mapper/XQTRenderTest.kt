package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.mapper.XQTTimeline
import dev.dimension.flare.data.network.xqt.model.Entities
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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
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

        val rendered = assertIs<UiTimelineV2.Post>(status.render(accountKey))
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

        val rendered = assertIs<UiTimelineV2.Post>(status.render(accountKey))
        assertEquals(1, rendered.quote.size)
        assertEquals(
            "quoted content",
            rendered.quote
                .first()
                .content.innerText,
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
            assertIs<UiTimelineV2.Post>(
                createTimeline(
                    tweet = child,
                    parents = listOf(createTimeline(parent)),
                ).render(accountKey),
            )
        assertEquals(1, rendered.parents.size)
        assertEquals(
            "parent content",
            rendered.parents
                .first()
                .content.innerText,
        )
        assertEquals(
            "status-parent",
            rendered.parents
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

        val rendered = assertIs<UiTimelineV2.Post>(createTimeline(repostWrapper).render(accountKey))
        val message = assertNotNull(rendered.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("user-reposter", message.user?.key?.id)
        assertEquals("original content", rendered.content.innerText)
        assertEquals("user-original", rendered.user?.key?.id)
        assertEquals("status-repost-wrapper", rendered.statusKey.id)
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

        val rendered = assertIs<UiTimelineV2.Post>(createTimeline(repostWrapper).render(accountKey))
        val message = assertNotNull(rendered.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("status-repost-wrapper-2", rendered.statusKey.id)
        assertEquals("original payload", rendered.content.innerText)
        assertEquals(1, rendered.quote.size)
        assertEquals(
            "quoted payload",
            rendered.quote
                .first()
                .content.innerText,
        )
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
