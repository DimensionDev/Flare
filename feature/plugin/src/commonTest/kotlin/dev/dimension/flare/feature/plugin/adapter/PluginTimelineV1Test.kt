package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.wire.ActionDescriptorV1
import dev.dimension.flare.feature.plugin.wire.CardV1
import dev.dimension.flare.feature.plugin.wire.EmojiReactionV1
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.FeedSourceV1
import dev.dimension.flare.feature.plugin.wire.FeedV1
import dev.dimension.flare.feature.plugin.wire.GalleryV1
import dev.dimension.flare.feature.plugin.wire.HostIconV1
import dev.dimension.flare.feature.plugin.wire.MediaTypeV1
import dev.dimension.flare.feature.plugin.wire.MediaV1
import dev.dimension.flare.feature.plugin.wire.MessageKindV1
import dev.dimension.flare.feature.plugin.wire.MessageV1
import dev.dimension.flare.feature.plugin.wire.MutationResultV1
import dev.dimension.flare.feature.plugin.wire.NavigationTargetV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PollOptionV1
import dev.dimension.flare.feature.plugin.wire.PollV1
import dev.dimension.flare.feature.plugin.wire.PostPresentationV1
import dev.dimension.flare.feature.plugin.wire.PostReferenceTypeV1
import dev.dimension.flare.feature.plugin.wire.PostReferenceV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.RelationV1
import dev.dimension.flare.feature.plugin.wire.RichTextFormatV1
import dev.dimension.flare.feature.plugin.wire.RichTextV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.feature.plugin.wire.SourceChannelV1
import dev.dimension.flare.feature.plugin.wire.TimelineItemV1
import dev.dimension.flare.feature.plugin.wire.TimelineUserListV1
import dev.dimension.flare.feature.plugin.wire.TimelineUserV1
import dev.dimension.flare.feature.plugin.wire.UgoiraFrameV1
import dev.dimension.flare.feature.plugin.wire.UgoiraMetadataV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import dev.dimension.flare.feature.plugin.wire.requireUserMutationResult
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.PluginMediaPermissions
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.bindMediaProxy
import dev.dimension.flare.ui.model.postEventOrNull
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.serialization.encodeToString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginTimelineV1Test {
    private val key = EntityKeyV1("same-id", "example.org")
    private val date = "2026-10-30T00:00:00Z"
    private val profile = ProfileV1(key, "alice", "Alice")
    private val post = PostV1(key, createdAt = date, content = RichTextV1(value = "Body"))
    private val message = MessageV1(key, date, HostIconV1.Heart, profile, kind = MessageKindV1.Favourite)
    private val account = MicroBlogKey("me", "example.org")

    private fun mapper(
        media: Boolean = true,
        authenticated: Boolean = true,
        operations: Map<String, Set<String>> = PluginAbiV1.knownCapabilityOperations,
    ) = PluginWireMapperV1(
        "test.plugin",
        "Test",
        account.takeIf { authenticated },
        "example.org",
        PluginSessionCapabilitiesV1(emptyMap(), operations, authenticated),
        mediaNetwork = media,
    )

    @AfterTest
    fun cleanup() {
        PluginMediaPermissions.replace(emptyMap())
    }

    @Test
    fun everyBusinessItemRoundTripsAndRetainsIndependentIdentity() {
        PluginMediaPermissions.register("test.plugin", true)
        val items =
            listOf(
                TimelineItemV1.Post(
                    post,
                    PostPresentationV1(
                        message = message,
                        notificationKey = key,
                        inlineParents = listOf(TimelineItemV1.Post(post.copy(key = key.copy(id = "parent")))),
                        quotes = listOf(post.copy(key = key.copy(id = "quote"))),
                        repost = post.copy(key = key.copy(id = "repost")),
                    ),
                ),
                TimelineItemV1.Feed(FeedV1("https://example.org/feed", date, FeedSourceV1("Source"), title = "Title")),
                TimelineItemV1.User(
                    TimelineUserV1(
                        key,
                        date,
                        profile,
                        message,
                        listOf(ActionDescriptorV1(SemanticActionV1.AcceptFollowRequest, actionToken = "accept", label = "Accept")),
                    ),
                ),
                TimelineItemV1.UserList(TimelineUserListV1(key, date, listOf(profile), message, post)),
                TimelineItemV1.Message(message),
            )
        val page = PageV1<TimelineItemV1>(items, olderCursor = "older", newerCursor = "newer")
        val decoded = PluginJsonV1.decodeFromString<PageV1<TimelineItemV1>>(PluginJsonV1.encodeToString(page))
        decoded.requireValid { it.requireValid() }
        assertEquals(page, decoded)
        val mapped = decoded.items.map(mapper()::timelineItem)
        assertEquals(5, mapped.map { it.stableItemKey }.distinct().size)
        mapped.forEach {
            val restored = PluginJsonV1.decodeFromString<UiTimelineV2>(PluginJsonV1.encodeToString<UiTimelineV2>(it)).bindMediaProxy()
            assertEquals(it, restored)
            assertEquals(it.stableItemKey, restored.stableItemKey)
        }
        val timeline = assertIs<UiTimelineV2.TimelinePostItem>(mapped[0])
        assertEquals(
            "parent",
            timeline.presentation.inlineParents
                .single()
                .statusKey.id,
        )
        assertEquals(
            "quote",
            timeline.presentation.quotes
                .single()
                .statusKey.id,
        )
        assertNull(timeline.presentation.repost!!.user)
        assertEquals(mapper().profile(profile).clickEvent, timeline.presentation.message!!.clickEvent)
        val another =
            mapper().timelineItem(
                (items[0] as TimelineItemV1.Post).copy(
                    presentation =
                        (items[0] as TimelineItemV1.Post).presentation.copy(notificationKey = key.copy(id = "second")),
                ),
            )
        assertNotEquals(timeline.stableItemKey, another.stableItemKey)
        assertEquals(
            ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly("https://example.org/feed")),
            assertIs<UiTimelineV2.Feed>(mapped[1]).clickEvent,
        )
        val user = assertIs<UiTimelineV2.User>(mapped[2])
        val action =
            assertIs<PostEvent.PluginAction>(
                user.button
                    .single()
                    .clickEvent
                    .postEventOrNull()!!
                    .postEvent,
            )
        assertEquals(user.statusKey, action.postKey)
        assertEquals(profile.key.id, action.entityKey.id)
        assertEquals("accept", action.actionToken)
        assertTrue(action.profileAction)
    }

    @Test
    fun nestedInlineParentsBecomeAnOrderedNativeChain() {
        fun ancestor(
            id: String,
            vararg parents: TimelineItemV1.Post,
        ) = TimelineItemV1.Post(
            post.copy(key = key.copy(id = id)),
            PostPresentationV1(
                inlineParents = parents.toList(),
                quotes = listOf(post.copy(key = key.copy(id = "$id-quote"))),
            ),
        )

        val oldest = ancestor("oldest")
        val older = ancestor("older", oldest)
        val grandparent = ancestor("grandparent", older)
        val parent = ancestor("parent", grandparent)
        val value = ancestor("root", parent, ancestor("sibling", older))
        value.requireValid()

        val mapped = assertIs<UiTimelineV2.TimelinePostItem>(mapper().timelineItem(value))
        val parents = mapped.presentation.inlineParents
        assertEquals(listOf("oldest", "older", "grandparent", "parent", "sibling"), parents.map { it.statusKey.id })
        assertTrue(parents.all { it.presentation.inlineParents.isEmpty() })
        assertEquals(
            parents.map { "${it.statusKey.id}-quote" },
            parents.map {
                it.presentation.quotes
                    .single()
                    .statusKey.id
            },
        )
        assertEquals(
            "root-quote",
            mapped.presentation.quotes
                .single()
                .statusKey.id,
        )
    }

    @Test
    fun fullPostsUseExistingPollCardReactionAndMediaModels() {
        val value =
            post.copy(
                sourceLanguages = listOf("en"),
                sourceChannel = SourceChannelV1("channel", "Channel"),
                replyToHandle = "alice",
                visibility = VisibilityV1.Channel,
                references = listOf(PostReferenceV1(key.copy(id = "quoted"), PostReferenceTypeV1.Quote)),
                card = CardV1("Card", "https://example.org/card", media = MediaV1("cover", MediaTypeV1.Image, "https://example.org/cover")),
                poll = PollV1("poll", listOf(PollOptionV1("A", 1), PollOptionV1("B", 3)), actionToken = "vote"),
                emojiReactions = listOf(EmojiReactionV1("👍", count = 4, actionToken = "react")),
                media =
                    listOf(
                        MediaV1(
                            "animation",
                            MediaTypeV1.Ugoira,
                            "https://example.org/art",
                            previewUrl = "https://example.org/preview",
                            ugoiraKey = key,
                        ),
                    ),
            )
        val mapped = mapper().post(value).post
        assertEquals(UiTimelineV2.Post.Visibility.Channel, mapped.visibility)
        assertEquals("channel", mapped.sourceChannel!!.id)
        assertEquals("alice", mapped.replyToHandle)
        assertEquals(listOf("en"), mapped.sourceLanguages)
        assertEquals("Card", mapped.card!!.title)
        assertEquals(listOf(0.25f, 0.75f), mapped.poll!!.options.map { it.percentage })
        assertEquals(
            "25.0%",
            mapped.poll!!
                .options
                .first()
                .humanizedPercentage,
        )
        assertTrue(mapped.poll!!.canVote)
        val reaction =
            assertIs<PostEvent.PluginAction>(
                mapped.emojiReactions
                    .single()
                    .clickEvent
                    .postEventOrNull()!!
                    .postEvent,
            )
        assertEquals("React", reaction.action)
        assertEquals(mapOf("reaction" to "👍"), reaction.parameters)
        assertEquals("react", reaction.actionToken)
        assertEquals("https://example.org/art", assertIs<UiMedia.Ugoira>(mapped.images.single()).url)
        val changed =
            mapper()
                .post(
                    value.copy(emojiReactions = listOf(value.emojiReactions.single().copy(actionToken = "replacement"))),
                ).post
        assertNotEquals(mapped.renderHash, changed.renderHash)
        val disabled = mapper(authenticated = false).post(value.copy(media = emptyList())).post
        assertFalse(disabled.poll!!.canVote)
        assertEquals(ClickEvent.Noop, disabled.emojiReactions.single().clickEvent)
    }

    @Test
    fun galleryDetailsKeepAllExistingMediaTypesIncludingUgoira() {
        val media =
            MediaTypeV1.entries.map { type ->
                MediaV1(
                    type.name,
                    type,
                    "https://example.org/media",
                    previewUrl = "https://example.org/preview",
                    ugoiraKey = key.takeIf { type == MediaTypeV1.Ugoira },
                )
            }
        val gallery = GalleryV1(key, "Gallery", createdAt = date, url = "https://example.org/gallery", images = media)
        gallery.requireValid()
        val mapped = mapper().gallery(gallery)
        assertEquals(5, mapped.images.size)
        assertEquals(
            setOf(UiMedia.Image::class, UiMedia.Video::class, UiMedia.Audio::class, UiMedia.Gif::class, UiMedia.Ugoira::class),
            mapped.images.map { it::class }.toSet(),
        )
        assertFails { mapper(media = false).gallery(gallery) }
    }

    @Test
    fun actionTokensAndPermissionsRemainObservableWithoutChangingTheUi() {
        val poll = PollV1("poll", listOf(PollOptionV1("A"), PollOptionV1("B")), actionToken = "first")
        val first = mapper().post(post.copy(poll = poll)).post
        val next = mapper().post(post.copy(poll = poll.copy(actionToken = "next"))).post
        assertNotEquals(first.renderHash, next.renderHash)
        val action = ActionDescriptorV1(SemanticActionV1.Unfavourite, count = 42, enabled = false)
        val disabled =
            mapper()
                .post(
                    post.copy(actions = listOf(action)),
                ).post.actions
                .filterIsInstance<dev.dimension.flare.data.datasource.microblog.ActionMenu.Item>()
                .single()
        assertEquals(42, disabled.count!!.value)
        assertFalse(disabled.enabled)
        assertEquals(ClickEvent.Noop, disabled.clickEvent)
        val guest = mapper(authenticated = false).post(post.copy(actions = listOf(action.copy(enabled = true)))).post
        assertFalse(assertIs<dev.dimension.flare.data.datasource.microblog.ActionMenu.Item>(guest.actions.single()).enabled)
        val user =
            TimelineItemV1.User(
                TimelineUserV1(
                    key,
                    date,
                    profile,
                    buttons =
                        listOf(ActionDescriptorV1(SemanticActionV1.Follow, actionToken = "one", label = "Follow")),
                ),
            )
        val row = assertIs<UiTimelineV2.User>(mapper().timelineItem(user))
        val replacement =
            user.copy(
                user =
                    user.user.copy(
                        buttons =
                            listOf(
                                user.user.buttons
                                    .single()
                                    .copy(actionToken = "two"),
                            ),
                    ),
            )
        assertNotEquals(row.renderHash, mapper().timelineItem(replacement).renderHash)
        assertFalse(assertIs<UiTimelineV2.User>(mapper(authenticated = false).timelineItem(user)).button.single().enabled)
        val unavailable = mapper(operations = emptyMap())
        val target = message.copy(target = NavigationTargetV1.Post(key))
        assertEquals(ClickEvent.Noop, assertIs<UiTimelineV2.Message>(unavailable.timelineItem(TimelineItemV1.Message(target))).clickEvent)
    }

    @Test
    fun newMediaPathsKeepPermissionsAfterRestoration() {
        val unicode = mapper(media = false).post(post.copy(emojiReactions = listOf(EmojiReactionV1("👍")))).post
        PluginMediaPermissions.register("test.plugin", false)
        assertEquals(1, unicode.bindMediaProxy().emojiReactions.size)
        val imageReaction =
            post.copy(
                emojiReactions = listOf(EmojiReactionV1(":wave:", url = "https://cdn.example/wave", isUnicode = false)),
            )
        assertFailsWith<IllegalArgumentException> { mapper(media = false).post(imageReaction) }
        assertTrue(
            mapper()
                .post(imageReaction)
                .post
                .bindMediaProxy()
                .emojiReactions
                .isEmpty(),
        )
        assertFailsWith<IllegalArgumentException> {
            mapper(
                media = false,
            ).timelineItem(TimelineItemV1.Feed(FeedV1("https://example.org/a", date, FeedSourceV1("Source", "https://cdn.example/icon"))))
        }
        val feed =
            mapper().timelineItem(
                TimelineItemV1.Feed(
                    FeedV1(
                        "https://example.org/a",
                        date,
                        FeedSourceV1("Source"),
                        description = RichTextV1(RichTextFormatV1.Html, "<img src='https://cdn.example/image'>"),
                    ),
                ),
            )
        assertNull(assertIs<UiTimelineV2.Feed>(feed.bindMediaProxy()).descriptionHtml)
        val linkedFeed =
            mapper().timelineItem(
                TimelineItemV1.Feed(
                    FeedV1(
                        "https://example.org/a",
                        date,
                        FeedSourceV1("Source"),
                        description = RichTextV1(RichTextFormatV1.Html, "<a href='/relative'>Open</a>"),
                    ),
                ),
            )
        assertTrue(assertIs<UiTimelineV2.Feed>(linkedFeed).descriptionHtml!!.contains("https://example.org/relative"))
    }

    @Test
    fun unknownAndOldShapesInvalidTargetsAndExcessiveNestingAreRejected() {
        assertFails { PluginJsonV1.decodeFromString<TimelineItemV1>(PluginJsonV1.encodeToString(post)) }
        assertFails { PluginJsonV1.decodeFromString<TimelineItemV1>("""{"type":"gap"}""") }
        assertFails { message.copy(text = "both").requireValid() }
        val updated = MutationResultV1.UpdatedProfile(profile, RelationV1(key, following = true))
        updated.requireUserMutationResult(key)
        assertFails { updated.copy(relation = RelationV1(key.copy(id = "other"))).requireUserMutationResult(key) }
        assertFails { message.copy(target = NavigationTargetV1.Link("javascript:alert(1)")).requireValid() }
        assertFails { PollV1("poll", listOf(PollOptionV1("A"), PollOptionV1("B")), ownVotes = listOf(2)).requireValid() }
        var nested = TimelineItemV1.Post(post)
        repeat(6) { nested = TimelineItemV1.Post(post, PostPresentationV1(inlineParents = listOf(nested))) }
        assertFails { nested.requireValid() }
        assertEquals(
            UiTimelineV2.Message.Type.Localized.MessageId.entries
                .map { it.name },
            MessageKindV1.entries.map { it.name },
        )
        UgoiraMetadataV1("https://example.org/frames.zip", listOf(UgoiraFrameV1("0.jpg", 100))).requireValid()
        assertFails { UgoiraMetadataV1("https://example.org/frames.zip", listOf(UgoiraFrameV1("../0.jpg", 100))).requireValid() }
    }
}
