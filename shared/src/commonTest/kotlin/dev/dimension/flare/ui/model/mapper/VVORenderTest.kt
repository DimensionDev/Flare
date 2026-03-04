package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.network.vvo.model.Large
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.StatusPic
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class VVORenderTest {
    private val accountKey = MicroBlogKey(id = "me", host = "weibo.cn")

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
        val imagePic =
            StatusPic(
                pid = "pic-image",
                url = "https://wx1.sinaimg.cn/thumbnail.jpg",
                large =
                    Large(
                        url = "https://wx1.sinaimg.cn/large.jpg",
                        geo =
                            buildJsonObject {
                                put("width", JsonPrimitive(1200))
                                put("height", JsonPrimitive(800))
                            },
                    ),
            )
        val videoPic =
            StatusPic(
                pid = "pic-video",
                type = "video",
                videoSrc = "https://video.weibo.com/stream.mp4",
                url = "https://wx1.sinaimg.cn/video-thumb.jpg",
                large =
                    Large(
                        url = "https://wx1.sinaimg.cn/video-large.jpg",
                        geo =
                            buildJsonObject {
                                put("width", JsonPrimitive(1920))
                                put("height", JsonPrimitive(1080))
                            },
                    ),
            )
        val status =
            createStatus(
                id = "status-media",
                user = createUser(1L, "media-user"),
                text = "media post",
                pics = JSON.encodeToJsonElement(ListSerializer(StatusPic.serializer()), listOf(imagePic, videoPic)),
            )

        val rendered = assertIs<UiTimelineV2.Post>(status.render(accountKey))
        assertEquals(2, rendered.images.size)

        val image = assertIs<UiMedia.Image>(rendered.images[0])
        assertEquals("https://wx1.sinaimg.cn/large.jpg", image.url)
        assertEquals("https://wx1.sinaimg.cn/thumbnail.jpg", image.previewUrl)
        assertEquals(1200f, image.width)
        assertEquals(800f, image.height)

        val video = assertIs<UiMedia.Video>(rendered.images[1])
        assertEquals("https://video.weibo.com/stream.mp4", video.url)
        assertEquals("https://wx1.sinaimg.cn/video-thumb.jpg", video.thumbnailUrl)
        assertEquals(1920f, video.width)
        assertEquals(1080f, video.height)
    }

    @Test
    fun quoteIsRecognized() {
        val quoted =
            createStatus(
                id = "status-quoted",
                user = createUser(2L, "quoted-user"),
                text = "quoted content",
            )
        val status =
            createStatus(
                id = "status-main",
                user = createUser(3L, "main-user"),
                text = "main content",
                retweetedStatus = quoted,
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

    private fun createStatus(
        id: String,
        user: User,
        text: String,
        pics: kotlinx.serialization.json.JsonElement? = null,
        retweetedStatus: Status? = null,
    ): Status =
        Status(
            id = id,
            bid = id,
            user = user,
            text = text,
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            pics = pics,
            retweetedStatus = retweetedStatus,
        )

    private fun createUser(
        id: Long,
        name: String,
    ): User =
        User(
            id = id,
            screenName = name,
            profileImageURL = "https://weibo.cn/$name.jpg",
            avatarHD = "https://weibo.cn/$name-hd.jpg",
            followersCountStr = "0",
        )
}
