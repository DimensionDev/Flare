package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutHelpers
import dev.dimension.flare.data.datasource.microblog.PostActionPlacement
import dev.dimension.flare.data.datasource.microblog.applyPostActionLayout
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.vvo.model.Comment
import dev.dimension.flare.data.network.vvo.model.Large
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.StatusPic
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.model.withProxy
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class VVORenderTest {
    private val accountKey = MicroBlogKey(id = "me", host = "weibo.cn")

    @BeforeTest
    fun setup() {
        startKoin {
            modules(VvoRenderTestModule::class)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun statusSubtitlePrefersRegionThenSourceWithoutChangingTheAuthor() {
        val user = createUser(1L, "author")
        val status = createStatus("status-region", user, "content")
        val expectedUser = user.render(accountKey)
        val cases =
            listOf(
                Triple("发布于 北京", "微博网页版", "发布于 北京"),
                Triple(null, "微博网页版", "微博网页版"),
                Triple(null, null, expectedUser.handle.canonical),
                Triple("", "微博网页版", ""),
            )

        cases.forEach { (region, source, expected) ->
            val post = assertIs<UiTimelineV2.Post>(status.copy(regionName = region, source = source).render(accountKey))
            assertEquals(expected, post.authorSubtitle)
            assertEquals(expectedUser, post.user)
        }
    }

    @Test
    fun commentSubtitleDecodesRegionAndFallsBackToSource() {
        val user = createUser(1L, "commenter")
        val expectedUser = user.render(accountKey)
        val cases =
            listOf(
                """{"id":"comment","region_name":"发布于 北京","source":"来自广东"}""" to "发布于 北京",
                """{"id":"comment","region_name":null,"source":"来自广东"}""" to "来自广东",
                """{"id":"comment","source":"来自广东"}""" to "来自广东",
                """{"id":"comment"}""" to expectedUser.handle.canonical,
                """{"id":"comment","region_name":"","source":"来自广东"}""" to "",
            )

        cases.forEach { (json, expected) ->
            val comment = JSON.decodeFromString<Comment>(json).copy(user = user)
            val post = assertIs<UiTimelineV2.Post>(comment.render(accountKey))
            assertEquals(expected, post.authorSubtitle)
            assertEquals(expectedUser, post.user)
        }
    }

    @Test
    fun quotesAndChildCommentsKeepTheirOwnSubtitle() {
        val user = createUser(1L, "author")
        val quoted = createStatus("quoted", user, "quoted").copy(regionName = "发布于 上海")
        val status = createStatus("root", user, "root", retweetedStatus = quoted).copy(regionName = "发布于 北京")
        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(status.render(accountKey))

        assertEquals("发布于 北京", rendered.post.authorSubtitle)
        assertEquals(
            "发布于 上海",
            rendered.presentation.quotes
                .single()
                .authorSubtitle,
        )

        val comment =
            JSON.decodeFromString<Comment>(
                """{"id":"root","region_name":"发布于 北京","comments":[{"id":"child","region_name":"发布于 上海","source":"来自广东"}]}""",
            )
        val renderedComment = assertIs<UiTimelineV2.TimelinePostItem>(comment.render(accountKey))
        assertEquals("发布于 北京", renderedComment.contentPostOrNull()?.authorSubtitle)
        assertEquals(
            "发布于 上海",
            renderedComment.presentation.quotes
                .single()
                .authorSubtitle,
        )
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

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(status.render(accountKey))
        assertEquals(1, rendered.presentation.quotes.size)
        assertEquals(
            "quoted content",
            rendered.presentation.quotes
                .first()
                .content.original.innerText,
        )
    }

    @Test
    fun imageLinksDoNotPersistProcessSignatures() {
        val text =
            renderVVOText(
                """<a href="https://$vvoHostShort/sinaurl?u=https://wx1.sinaimg.cn/large/photo.jpg">查看图片</a>""",
                accountKey,
            )
        val restored = JSON.decodeFromString<UiRichText>(JSON.encodeToString(text)).withProxy(ProxyRef.Account(accountKey))
        val block = assertIs<RenderContent.Text>(restored.renderRuns.single())
        val run = assertIs<RenderRun.Text>(block.runs.single())
        assertNull(run.style.link, "Process-signed links must not be persisted in rich text")
        val route = assertIs<DeeplinkRoute.Media.Image>(DeeplinkRoute.parse(requireNotNull(run.style.resolvedLink)))
        assertEquals("https://wx1.sinaimg.cn/large/photo.jpg", route.uri)
        assertEquals(ProxyRef.Account(accountKey), route.proxy)
    }

    @Test
    fun quoteFollowsRepostLayoutAfterClearingMoreMenu() {
        val status =
            createStatus(
                id = "status-actions",
                user = createUser(1L, "actions-user"),
                text = "post with quote action",
            )
        val post = assertIs<UiTimelineV2.Post>(status.render(accountKey))
        val quote = post.actions.filterIsInstance<ActionMenu.Item>().single { it.actionFamily == PostActionFamily.Quote }
        var config = PostActionLayoutHelpers.withEnabled(PostActionLayoutConfig.Default, true)
        for (family in config.overflow.toList()) {
            config = PostActionLayoutHelpers.moveTo(config, family, PostActionPlacement.Hidden)
        }
        assertTrue(config.overflow.isEmpty())

        val actions = post.actions.applyPostActionLayout(config)

        assertEquals(
            listOf(
                PostActionFamily.Comment,
                PostActionFamily.Quote,
                PostActionFamily.Like,
            ),
            actions.map { assertIs<ActionMenu.Item>(it).actionFamily },
        )
        assertTrue(actions.all { it in post.actions })
        assertEquals(quote, actions[1])

        val hiddenConfig = PostActionLayoutHelpers.moveTo(config, PostActionFamily.Repost, PostActionPlacement.Hidden)
        assertEquals(
            actions.filterNot { it == quote },
            post.actions.applyPostActionLayout(hiddenConfig),
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

@Module
internal class VvoRenderTestModule {
    @Single
    fun platformFormatter(): PlatformFormatter = TestFormatter()
}
