package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.PostDetailTypeV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.RichTextV1
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class PluginWireMapperV1Test {
    private val mapper = createMapper(MicroBlogKey("me", "local.example"))
    private val post =
        PostV1(
            key = EntityKeyV1("post-1", "remote.example"),
            author = ProfileV1(EntityKeyV1("author", "remote.example"), "alice", "Alice"),
            createdAt = "2026-10-27T00:00:00Z",
            content = RichTextV1(value = "Preview"),
        )

    @Test
    fun remoteProfileHandleKeepsItsFederatedHost() {
        val profile =
            ProfileV1(
                key = EntityKeyV1("42", "local.example"),
                handle = "@alice@remote.example",
                displayName = "Alice",
            )

        assertEquals("@alice@remote.example", mapper.profile(profile).handle.canonical)
    }

    @Test
    fun localProfileHandleFallsBackToEntityHost() {
        val profile =
            ProfileV1(
                key = EntityKeyV1("42", "local.example"),
                handle = "alice",
                displayName = "Alice",
            )

        assertEquals("@alice@local.example", mapper.profile(profile).handle.canonical)
    }

    @Test
    fun guestDetailRoutesKeepTheGuestOriginAndEntityKey() {
        val guestMapper = createMapper(null)
        val accountType = AccountType.GuestHost("local.example")
        val key = MicroBlogKey(post.key.id, post.key.host)
        val targets =
            mapOf(
                PostDetailTypeV1.Post to DeeplinkRoute.Status.Detail(key, accountType),
                PostDetailTypeV1.Article to DeeplinkRoute.Article(accountType, key),
                PostDetailTypeV1.Gallery to DeeplinkRoute.Gallery.Detail(key, accountType),
            )

        targets.forEach { (type, route) ->
            val mapped = assertIs<UiTimelineV2.TimelinePostItem>(guestMapper.post(post.copy(detailType = type)))
            assertEquals(ClickEvent.Deeplink(route), mapped.post.clickEvent)
        }
    }

    @Test
    fun articleAndGalleryPreviewsUseTheSelectedDetailClickInMediaGrids() {
        val previews = listOf(post, post.copy(detailType = PostDetailTypeV1.Article), post.copy(detailType = PostDetailTypeV1.Gallery))

        assertEquals(
            listOf(
                UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
                UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent,
                UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent,
            ),
            previews.map { assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(it)).post.mediaClickPolicy },
        )
    }

    @Test
    fun detailTypeUpdatesInvalidateCachedTimelineRows() {
        val article = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(post.copy(detailType = PostDetailTypeV1.Article)))
        val gallery = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(post.copy(detailType = PostDetailTypeV1.Gallery)))

        assertEquals(article.stableItemKey, gallery.stableItemKey)
        assertNotEquals(article.renderHash, gallery.renderHash)
        assertNotEquals(article.post.renderHash, gallery.post.renderHash)
    }

    @Test
    fun repostKeepsItsOwnDetailTarget() {
        val repost = post.copy(key = EntityKeyV1("article-1", "remote.example"), detailType = PostDetailTypeV1.Article)
        val mapped = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(post.copy(repost = repost)))

        assertEquals(
            ClickEvent.Deeplink(
                DeeplinkRoute.Article(
                    AccountType.Specific(MicroBlogKey("me", "local.example")),
                    MicroBlogKey("article-1", "remote.example"),
                ),
            ),
            mapped.presentation.repost?.clickEvent,
        )
    }

    private fun createMapper(accountKey: MicroBlogKey?): PluginWireMapperV1 =
        PluginWireMapperV1(
            pluginId = "test.plugin",
            platformId = "Test",
            accountKey = accountKey,
            originHost = "local.example",
            profileAvailable = true,
            postDetailAvailable = true,
            articleDetailAvailable = true,
            galleryDetailAvailable = true,
            postMutationAvailable = true,
            postDeleteAvailable = true,
            composeAvailable = true,
        )
}
