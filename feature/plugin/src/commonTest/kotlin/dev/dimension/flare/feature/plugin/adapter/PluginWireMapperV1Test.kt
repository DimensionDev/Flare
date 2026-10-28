package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.wire.ActionDescriptorV1
import dev.dimension.flare.feature.plugin.wire.ArticleV1
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.GalleryV1
import dev.dimension.flare.feature.plugin.wire.MediaTypeV1
import dev.dimension.flare.feature.plugin.wire.MediaV1
import dev.dimension.flare.feature.plugin.wire.NotificationKindV1
import dev.dimension.flare.feature.plugin.wire.NotificationV1
import dev.dimension.flare.feature.plugin.wire.PostDetailTypeV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.RichTextFormatV1
import dev.dimension.flare.feature.plugin.wire.RichTextV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Instant

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
    fun unsafeProfileAndNestedPostUrlsAreRejectedBeforeMapping() {
        listOf("http://remote.example/asset", "https://user:secret@remote.example/asset", "javascript:alert(1)").forEach { url ->
            listOf(
                post.author.copy(avatarUrl = url),
                post.author.copy(bannerUrl = url),
                post.author.copy(url = url),
            ).forEach { profile ->
                assertFailsWith<IllegalArgumentException>(url) { mapper.profile(profile) }
                assertFailsWith<IllegalArgumentException>(url) { mapper.post(post.copy(repost = post.copy(author = profile))) }
            }
            listOf(
                post.copy(url = url),
                post.copy(media = listOf(MediaV1("image", MediaTypeV1.Image, url))),
                post.copy(media = listOf(MediaV1("image", MediaTypeV1.Image, "https://remote.example/image", previewUrl = url))),
            ).forEach { nested ->
                assertFailsWith<IllegalArgumentException>(url) { mapper.post(post.copy(repost = nested)) }
            }
        }
    }

    @Test
    fun notificationsForTheSamePostKeepTheirIdentityActorAndTime() {
        val actor = post.author.copy(key = EntityKeyV1("reactor", "remote.example"))
        val notification =
            NotificationV1(
                id = "notice-1",
                createdAt = "2026-10-28T00:00:00Z",
                kind = NotificationKindV1.Favourite,
                actor = actor,
                post = post,
            )
        val first = assertIs<UiTimelineV2.TimelinePostItem>(mapper.notification(notification))
        val second = assertIs<UiTimelineV2.TimelinePostItem>(mapper.notification(notification.copy(id = "notice-2")))
        assertEquals(2, listOf(first, second).distinctBy { it.stableItemKey }.size)
        assertEquals(MicroBlogKey(notification.id, "local.example"), first.presentation.notificationKey)
        assertEquals(
            actor.key.id,
            first.presentation.message
                ?.user
                ?.key
                ?.id,
        )
        assertEquals(Instant.parse(notification.createdAt), first.createdAt.value)
        assertEquals(UiIcon.Heart, first.presentation.message?.icon)
        assertEquals(
            UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Favourite),
            first.presentation.message?.type,
        )
        assertEquals(post.key.id, first.post.statusKey.id)
        assertEquals(mapper.post(post).let { assertIs<UiTimelineV2.TimelinePostItem>(it).post.clickEvent }, first.post.clickEvent)
    }

    @Test
    fun notificationKeepsCustomTextRepostsAndStandaloneActors() {
        val repost = post.copy(key = EntityKeyV1("reposted", "remote.example"))
        val notification =
            NotificationV1(
                id = "notice",
                createdAt = post.createdAt,
                kind = NotificationKindV1.Repost,
                actor = post.author,
                post = post.copy(repost = repost),
                message = RichTextV1(RichTextFormatV1.Html, "<p>Shared your post</p>"),
            )
        val item = assertIs<UiTimelineV2.TimelinePostItem>(mapper.notification(notification))
        assertEquals(
            repost.key.id,
            item.presentation.repost
                ?.statusKey
                ?.id,
        )
        assertEquals(UiTimelineV2.Message.Type.Raw("Shared your post"), item.presentation.message?.type)
        val standalone = assertIs<UiTimelineV2.Message>(mapper.notification(notification.copy(post = null)))
        assertEquals(item.presentation.message, standalone)
        assertEquals(mapper.profile(post.author).clickEvent, standalone.clickEvent)
    }

    @Test
    fun disabledOrUnsupportedGalleryBookmarkDoesNotCreateAMutation() {
        val gallery =
            GalleryV1(
                key = post.key,
                title = "Gallery",
                createdAt = post.createdAt,
                url = "https://remote.example/gallery",
                images = listOf(MediaV1("image", MediaTypeV1.Image, "https://remote.example/image.png")),
                actions =
                    listOf(
                        dev.dimension.flare.feature.plugin.wire.ActionDescriptorV1(
                            action = dev.dimension.flare.feature.plugin.wire.SemanticActionV1.Bookmark,
                            enabled = false,
                        ),
                    ),
            )
        assertEquals(ClickEvent.Noop, mapper.gallery(gallery).bookmarkAction)
        val disabledBookmark =
            gallery.copy(
                actions =
                    gallery.actions.map {
                        it.copy(action = dev.dimension.flare.feature.plugin.wire.SemanticActionV1.Unbookmark, active = true)
                    },
            )
        assertEquals(true, mapper.gallery(disabledBookmark).isBookmarked)
        assertEquals(ClickEvent.Noop, mapper.gallery(disabledBookmark).bookmarkAction)
        val available = gallery.copy(actions = gallery.actions.map { it.copy(enabled = true) })
        assertNotEquals(ClickEvent.Noop, mapper.gallery(available).bookmarkAction)
        assertEquals(ClickEvent.Noop, createMapper(null).gallery(available).bookmarkAction)
        assertEquals(
            ClickEvent.Noop,
            createMapper(MicroBlogKey("me", "local.example"), postMutationAvailable = false).gallery(available).bookmarkAction,
        )
    }

    @Test
    fun selfProfileUsesTheCanonicalAccountKeyForCacheLookups() {
        val key = MicroBlogKey("me", "local.example")
        val profile = post.author.copy(key = EntityKeyV1(key.id, key.host.uppercase()))
        assertEquals(key, mapper.profile(profile).key)
        assertEquals(key, mapper.cachedProfile(profile).key)
        assertEquals(
            MicroBlogKey("other", key.host),
            mapper.profile(profile.copy(key = profile.key.copy(id = "other"))).key,
        )
        assertEquals(post.author.key.host, mapper.profile(post.author).key.host)
    }

    @Test
    fun savedLoginIdentitySurvivesMediaPermissionRemoval() {
        val denied = createMapper(MicroBlogKey("me", "local.example"), mediaNetwork = false)
        val profile =
            denied.cachedProfile(
                post.author.copy(
                    avatarUrl = "https://cdn.example/avatar.png",
                    bannerUrl = "https://cdn.example/banner.png",
                    description = RichTextV1(RichTextFormatV1.Html, "<p>About<img src='https://cdn.example/old.png'></p>"),
                ),
            )
        assertEquals(null, profile.avatar)
        assertEquals(null, profile.banner)
        assertEquals("About", profile.description?.raw)
        assertEquals(post.author.key.id, profile.key.id)
        assertEquals(
            false,
            dev.dimension.flare.ui.model.PluginMediaPermissions
                .isAllowed(profile.pluginId!!),
        )
    }

    @Test
    fun remoteMediaRequiresAnExplicitNetworkGrant() {
        val denied = createMapper(MicroBlogKey("me", "local.example"), mediaNetwork = false)
        assertFailsWith<IllegalArgumentException> {
            denied.profile(post.author.copy(avatarUrl = "https://unapproved.example/collect?token=dummy"))
        }
        assertFailsWith<IllegalArgumentException> {
            denied.post(post.copy(media = listOf(MediaV1("image", MediaTypeV1.Image, "https://cdn.example/image.png"))))
        }
        assertFailsWith<IllegalArgumentException> {
            denied.post(post.copy(content = RichTextV1(RichTextFormatV1.Html, "<p><img src='https://cdn.example/image.png'></p>")))
        }
        assertIs<UiTimelineV2.TimelinePostItem>(denied.post(post))
        assertEquals(
            "https://cdn.example/avatar.png",
            mapper.profile(post.author.copy(avatarUrl = "https://cdn.example/avatar.png")).avatar?.url,
        )
    }

    @Test
    fun richTextResolvesRelativeResourcesAtTheVerifiedOrigin() {
        val value = mapper.post(post.copy(content = RichTextV1(RichTextFormatV1.Html, "<p><img src='/image.png'></p>")))
        val image =
            assertIs<UiTimelineV2.TimelinePostItem>(value)
                .post.content.original.imageMedias
                .single()
        assertEquals("https://local.example/image.png", image.url)
        assertEquals(ProxyRef.Account(MicroBlogKey("me", "local.example")), image.proxy)
    }

    @Test
    fun galleryMediaKeepsTheViewingAccountScope() {
        val value =
            GalleryV1(
                key = post.key,
                title = "Gallery",
                createdAt = post.createdAt,
                url = "https://remote.example/gallery",
                author = post.author.copy(avatarUrl = "https://remote.example/avatar.png"),
                images = listOf(MediaV1("image", MediaTypeV1.Image, "https://remote.example/image.png")),
            )
        val detail = mapper.gallery(value)

        assertEquals(ProxyRef.Account(MicroBlogKey("me", "local.example")), detail.images.single().proxy)
        assertEquals(detail.images.single().proxy, detail.author?.avatar?.proxy)
        assertEquals(
            ProxyRef.Global,
            createMapper(null)
                .gallery(value)
                .images
                .single()
                .proxy,
        )
    }

    @Test
    fun articleBlockImagesKeepTheirPosition() {
        val value =
            ArticleV1(
                key = post.key,
                title = "Article",
                content =
                    RichTextV1(
                        RichTextFormatV1.Html,
                        "<p>Before</p><figure><img src='https://remote.example/image.png'></figure><p>After</p>",
                    ),
            )
        val blocks = mapper.article(value).content.blocks

        assertEquals(3, blocks.size)
        assertIs<UiArticleBlock.Text>(blocks[0])
        assertEquals("https://remote.example/image.png", assertIs<UiArticleBlock.Image>(blocks[1]).media.url)
        assertIs<UiArticleBlock.Text>(blocks[2])
    }

    @Test
    fun richTextRejectsLocalResourcesAndUnsafeLinks() {
        listOf(
            "file:///private/tmp/private.png",
            "content://private/image",
            "data:image/png;base64,AAA",
            "http://example.com/image",
        ).forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) {
                mapper.post(post.copy(content = RichTextV1(RichTextFormatV1.Html, "<p><img src='$url'></p>")))
            }
        }
        listOf("javascript:alert(1)", "file:///private/tmp/private.png", "intent://example").forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) {
                mapper.post(post.copy(content = RichTextV1(RichTextFormatV1.Html, "<a href='$url'>link</a>")))
            }
        }
    }

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
    fun linkPostsOpenTheExplicitBrowserTargetForAccountsAndGuests() {
        val url = "https://remote.example/posts/post-1?source=plugin#content"
        val value = post.copy(detailType = PostDetailTypeV1.Link, url = url)
        listOf(createMapper(MicroBlogKey("me", "local.example"), mediaNetwork = false), createMapper(null, mediaNetwork = false)).forEach {
            val mapped = assertIs<UiTimelineV2.TimelinePostItem>(it.post(value)).post
            assertEquals(ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly(url)), mapped.clickEvent)
            assertEquals(UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent, mapped.mediaClickPolicy)
        }
    }

    @Test
    fun linkPostsKeepIndependentAuthorAndActionClicks() {
        val value = post.copy(url = "https://remote.example/post", actions = listOf(ActionDescriptorV1(SemanticActionV1.Favourite)))
        val internal = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(value)).post
        val linked = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(value.copy(detailType = PostDetailTypeV1.Link))).post
        assertIs<ClickEvent.Deeplink>(linked.user?.clickEvent)
        assertEquals(internal.user?.clickEvent, linked.user?.clickEvent)
        assertEquals(internal.actions, linked.actions)
        assertNotEquals(internal.clickEvent, linked.clickEvent)
    }

    @Test
    fun nonPostPreviewsUseTheSelectedClickInMediaGrids() {
        val previews =
            listOf(
                post,
                post.copy(detailType = PostDetailTypeV1.Article),
                post.copy(detailType = PostDetailTypeV1.Gallery),
                post.copy(detailType = PostDetailTypeV1.Link, url = "https://remote.example/post"),
            )

        assertEquals(
            listOf(
                UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia,
                UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent,
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
    fun linkUrlUpdatesInvalidateCachedTimelineRows() {
        val value = post.copy(detailType = PostDetailTypeV1.Link, url = "https://remote.example/first")
        val first = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(value))
        val second = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(value.copy(url = "https://remote.example/second")))
        assertEquals(first.stableItemKey, second.stableItemKey)
        assertNotEquals(first.renderHash, second.renderHash)
        assertNotEquals(first.post.renderHash, second.post.renderHash)
    }

    @Test
    fun repostCanOpenALinkWhileTheParentKeepsPostDetail() {
        val url = "https://remote.example/reposted"
        val nested = post.copy(key = EntityKeyV1("linked", "remote.example"), detailType = PostDetailTypeV1.Link, url = url)
        val mapped = assertIs<UiTimelineV2.TimelinePostItem>(mapper.post(post.copy(repost = nested)))
        assertEquals(
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    MicroBlogKey(post.key.id, post.key.host),
                    AccountType.Specific(MicroBlogKey("me", "local.example")),
                ),
            ),
            mapped.post.clickEvent,
        )
        assertEquals(ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly(url)), mapped.presentation.repost?.clickEvent)
        assertEquals(UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent, mapped.presentation.repost?.mediaClickPolicy)
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

    private fun createMapper(
        accountKey: MicroBlogKey?,
        mediaNetwork: Boolean = true,
        postMutationAvailable: Boolean = true,
    ): PluginWireMapperV1 =
        PluginWireMapperV1(
            pluginId = "test.plugin",
            mediaNetwork = mediaNetwork,
            platformId = "Test",
            accountKey = accountKey,
            originHost = "local.example",
            capabilities =
                PluginSessionCapabilitiesV1(
                    declarations = emptyMap(),
                    operations =
                        PluginAbiV1.knownCapabilityOperations.mapValues { (id, operations) ->
                            if (id == PluginAbiV1.Capabilities.POST && !postMutationAvailable) operations - "mutate" else operations
                        },
                    authenticated = accountKey != null,
                ),
        )
}
