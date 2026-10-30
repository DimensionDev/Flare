package dev.dimension.flare.ui.model

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginCachedMediaPolicyTest {
    @Test
    fun currentPermissionAppliesAfterProfileSerializationAndTranslation() {
        val richText = parseHtml("<p>text<img src='https://cdn.example/image.png'></p>").toUi()
        val profile =
            createSampleUser().copy(
                pluginId = "fixture.plugin",
                nameInternal = richText,
                description = richText,
                bottomContent = UiProfile.BottomContent.Fields(persistentMapOf("field" to richText)),
            )
        val bytes = profile.encodeJson()
        PluginMediaPermissions.register("fixture.plugin", true)
        assertEquals(
            profile.avatar?.url,
            bytes
                .decodeJson<UiProfile>()
                .withProxy(ProxyRef.Global)
                .avatar
                ?.url,
        )
        PluginMediaPermissions.register("fixture.plugin", false)
        val restored = bytes.decodeJson<UiProfile>().withProxy(ProxyRef.Global)
        assertNull(restored.avatar)
        assertNull(restored.banner)
        assertTrue(restored.description!!.imageMedias.isEmpty())
        assertTrue(restored.description.imageUrls.isEmpty())
        assertTrue(restored.name.imageMedias.isEmpty())
        assertTrue(assertIs<UiProfile.BottomContent.Fields>(restored.bottomContent).fields.values.all { it.imageMedias.isEmpty() })
    }

    @Test
    fun disabledOrUninstalledPluginsCannotRestoreMedia() {
        val profile = createSampleUser().copy(pluginId = "removed.plugin")
        PluginMediaPermissions.replace(emptyMap())
        assertNull(profile.withProxy(ProxyRef.Global).avatar)
        assertEquals(createSampleUser().avatar?.url, createSampleUser().withProxy(ProxyRef.Global).avatar?.url)
    }

    @Test
    fun cachedTimelineTranslationsQuotesAndHistoryCoversRecheckPermissions() {
        val user = createSampleUser()
        val richText = parseHtml("<img src='https://cdn.example/translated.png'>").toUi()
        val post =
            createSampleStatus(user).copy(
                pluginId = "timeline.plugin",
                images = persistentListOf(UiMedia.Image("https://cdn.example/post.png")),
                content = UiTranslatableText("body".toUiPlainText(), translation = richText),
            )
        val item = UiTimelineV2.TimelinePostItem(post, UiTimelineV2.PostPresentation(quotes = persistentListOf(post)))
        val bytes = item.encodeJson()
        PluginMediaPermissions.register("timeline.plugin", true)
        assertEquals(
            1,
            bytes
                .decodeJson<UiTimelineV2.TimelinePostItem>()
                .bindMediaProxy()
                .post.images.size,
        )
        PluginMediaPermissions.register("timeline.plugin", false)
        val restored = bytes.decodeJson<UiTimelineV2.TimelinePostItem>().bindMediaProxy()
        assertTrue(restored.post.images.isEmpty())
        assertNull(restored.post.user?.avatar)
        assertTrue(
            restored.post.content.translation!!
                .imageMedias
                .isEmpty(),
        )
        assertTrue(
            restored.presentation.quotes
                .single()
                .images
                .isEmpty(),
        )
        val history =
            UiTimelineV2
                .Feed(
                    title = "article",
                    description = "body",
                    url = "https://plugin.example/article",
                    createdAt = post.createdAt,
                    source = UiTimelineV2.Feed.Source("plugin", "https://cdn.example/icon.png"),
                    media = UiMedia.Image("https://cdn.example/cover.png"),
                    accountType = post.accountType,
                    pluginId = post.pluginId,
                ).encodeJson()
                .decodeJson<UiTimelineV2.Feed>()
                .bindMediaProxy()
        assertNull(history.media)
        assertNull(history.source.icon)
    }

    @Test
    fun cachedDirectMessageRoomsItemsAndArticlesRecheckPermissions() {
        val user = createSampleUser()
        val post = createSampleStatus(user)
        val message =
            UiDMItem(
                key = MicroBlogKey("message", user.key.host),
                user = user,
                content = UiDMItem.Message.Media(UiMedia.Image("https://cdn.example/dm.png")),
                timestamp = post.createdAt,
                isFromMe = false,
                sendState = null,
                showSender = true,
                pluginId = "dm.plugin",
            )
        PluginMediaPermissions.register("dm.plugin", false)
        val restored = message.encodeJson().decodeJson<UiDMItem>().withProxy(ProxyRef.Account(user.key))
        assertIs<UiDMItem.Message.Text>(restored.content)
        assertNull(restored.user.avatar)
        val room =
            UiDMRoom(MicroBlogKey("room", user.key.host), persistentListOf(user), message, 0, "dm.plugin")
                .encodeJson()
                .decodeJson<UiDMRoom>()
                .withProxy(ProxyRef.Global)
        assertNull(room.users.single().avatar)
        assertNull(room.lastMessage?.user?.avatar)
        val article =
            UiArticle(
                "article",
                "Title",
                uiArticleContentOf(listOf(UiArticleBlock.Image("image", UiMedia.Image("https://cdn.example/article.png")))),
                cover = UiMedia.Image("https://cdn.example/cover.png"),
                author = UiArticleAuthor.Profile(user),
                pluginId = "dm.plugin",
            ).encodeJson().decodeJson<UiArticle>().withProxy(ProxyRef.Global)
        assertNull(article.cover)
        assertNull(assertIs<UiArticleAuthor.Profile>(article.author).profile.avatar)
        assertTrue(article.content.blocks.isEmpty())
    }

    @Test
    fun profileMergesCannotLaunderPluginMediaOwnership() {
        val cached = createSampleUser().copy(pluginId = "old.plugin")
        val fresh = createSampleUser().copy(avatar = null, banner = null)
        assertNull(fresh.mergeWith(cached).avatar)
        assertEquals("old.plugin", cached.copy(avatar = null).mergeWith(cached).pluginId)
        assertEquals(cached.avatar, cached.copy(avatar = null).mergeWith(cached).avatar)
    }
}
