package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.network.tumblr.TumblrBlog
import dev.dimension.flare.data.network.tumblr.TumblrNote
import dev.dimension.flare.data.network.tumblr.TumblrNpfAttribution
import dev.dimension.flare.data.network.tumblr.TumblrNpfBlock
import dev.dimension.flare.data.network.tumblr.TumblrNpfFormatting
import dev.dimension.flare.data.network.tumblr.TumblrNpfLayout
import dev.dimension.flare.data.network.tumblr.TumblrNpfLayoutDisplay
import dev.dimension.flare.data.network.tumblr.TumblrNpfMedia
import dev.dimension.flare.data.network.tumblr.TumblrPost
import dev.dimension.flare.data.network.tumblr.TumblrTrailItem
import dev.dimension.flare.data.network.tumblr.TumblrTrailPost
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.postEventOrNull
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TumblrMapperTest {
    private val accountKey = MicroBlogKey(id = "me", host = "tumblr.com")

    @Test
    fun npfTextImageAndLinkAreMappedToTimelinePost() {
        val post =
            TumblrPost(
                idString = "123",
                blogName = "Staff",
                blog = TumblrBlog(name = "Staff", title = "Tumblr Staff", posts = 10),
                postUrl = "https://www.tumblr.com/staff/123",
                timestampEpochSeconds = 1234,
                content =
                    listOf(
                        textBlock("Hello Tumblr"),
                        imageBlock(
                            url = "https://64.media.tumblr.com/image.jpg",
                            width = 800,
                            height = 600,
                            altText = "image alt",
                        ),
                        linkBlock(
                            url = "https://example.com/story",
                            title = "Example Story",
                            description = "A link card",
                            posterUrl = "https://example.com/poster.jpg",
                        ),
                    ),
                reblogKey = "abc123",
                noteCount = 7,
                liked = false,
                canLike = true,
                canReblog = true,
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val timelinePost = rendered.post

        assertEquals(PlatformType.Tumblr, timelinePost.platformType)
        assertEquals(tumblrPostKey("staff", "123"), timelinePost.statusKey)
        assertEquals(AccountType.Specific(accountKey), timelinePost.accountType)
        assertEquals("Hello Tumblr\nExample Story", timelinePost.content.raw)
        val inlineImageCount =
            timelinePost.content.renderRuns
                .filterIsInstance<RenderContent.BlockImage>()
                .size
        assertEquals(0, inlineImageCount)
        assertEquals("Tumblr Staff", timelinePost.user?.name?.raw)
        assertEquals("staff", timelinePost.user?.handle?.raw)
        assertEquals(1, timelinePost.images.size)

        val image = assertIs<UiMedia.Image>(timelinePost.images.first())
        assertEquals("https://64.media.tumblr.com/image.jpg", image.url)
        assertEquals("image alt", image.description)
        assertEquals(800f, image.width)
        assertEquals(600f, image.height)

        val card = assertNotNull(timelinePost.card)
        assertEquals("Example Story", card.title)
        assertEquals("A link card", card.description)
        assertEquals("https://example.com/story", card.url)
        assertEquals("https://example.com/poster.jpg", card.media?.url)
        assertEquals(3, timelinePost.actions.size)
    }

    @Test
    fun actionMenusExposeSupportedRepostQuoteLikeAndMoreActions() {
        val post =
            TumblrPost(
                idString = "123",
                blogName = "staff",
                blog = TumblrBlog(name = "staff", title = "Tumblr Staff"),
                postUrl = "https://www.tumblr.com/staff/123",
                content = listOf(textBlock("Hello")),
                reblogKey = "abc123",
                noteCount = 7,
                replyCount = 2,
                reblogCount = 3,
                likeCount = 5,
                notes =
                    listOf(
                        TumblrNote(type = "reply"),
                        TumblrNote(type = "comment"),
                        TumblrNote(type = "reblog"),
                        TumblrNote(type = "reblogged"),
                        TumblrNote(type = "posted"),
                        TumblrNote(type = "like"),
                        TumblrNote(type = "liked"),
                        TumblrNote(type = "like"),
                        TumblrNote(type = "like"),
                        TumblrNote(type = "like"),
                    ),
                liked = false,
                canLike = true,
                canReblog = true,
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        val actions = timelinePost.actions

        assertEquals(3, actions.size)

        val repostGroup = assertIs<ActionMenu.Group>(actions[0])
        val repostDisplay = assertIs<ActionMenu.Item>(repostGroup.displayItem)
        assertEquals(PostActionFamily.Repost, repostDisplay.actionFamily)
        assertEquals(3L, repostDisplay.count?.value)
        assertEquals("", repostDisplay.updateKey)
        assertIs<PostEvent.Tumblr.Repost>(repostDisplay.clickEvent.postEventOrNull()?.postEvent)
        val repostActions = repostGroup.actions
        val repost = assertIs<ActionMenu.Item>(repostActions[0])
        assertEquals(PostActionFamily.Repost, repost.actionFamily)
        assertEquals(3L, repost.count?.value)
        assertEquals("", repost.updateKey)
        val quote = assertIs<ActionMenu.Item>(repostActions[1])
        assertEquals(PostActionFamily.Quote, quote.actionFamily)
        val quoteRoute = DeeplinkRoute.parse(assertIs<ClickEvent.Deeplink>(quote.clickEvent).url)
        assertIs<DeeplinkRoute.Compose.Quote>(quoteRoute)

        val like = assertIs<ActionMenu.Item>(actions[1])
        assertEquals(PostActionFamily.Like, like.actionFamily)
        assertEquals(5L, like.count?.value)

        val more = assertIs<ActionMenu.Group>(actions[2])
        val overflowFamilies =
            more.actions
                .filterIsInstance<ActionMenu.Item>()
                .mapNotNull { it.actionFamily }
        assertEquals(
            listOf(
                PostActionFamily.Comment,
                PostActionFamily.Share,
                PostActionFamily.MuteUser,
                PostActionFamily.BlockUser,
                PostActionFamily.Report,
            ),
            overflowFamilies,
        )
    }

    @Test
    fun legacyPhotoIsUsedWhenNpfMediaIsMissing() {
        val post =
            TumblrPost(
                idString = "photo-1",
                blogName = "staff",
                photos =
                    listOf(
                        dev.dimension.flare.data.network.tumblr.TumblrLegacyPhoto(
                            caption = "legacy alt",
                            originalSize =
                                dev.dimension.flare.data.network.tumblr.TumblrLegacyPhotoSize(
                                    url = "https://64.media.tumblr.com/legacy.jpg",
                                    width = 1024,
                                    height = 768,
                                ),
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        val image = assertIs<UiMedia.Image>(timelinePost.images.single())

        assertEquals("https://64.media.tumblr.com/legacy.jpg", image.url)
        assertEquals("legacy alt", image.description)
        assertEquals(1024f, image.width)
        assertEquals(768f, image.height)
    }

    @Test
    fun linkCardUsesUrlAsDescriptionFallback() {
        val post =
            TumblrPost(
                idString = "link-no-description",
                blogName = "staff",
                content =
                    listOf(
                        linkBlock(
                            url = "https://example.com/story",
                            title = "Example Story",
                            description = "",
                            posterUrl = "https://example.com/poster.jpg",
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        val card = assertNotNull(timelinePost.card)

        assertEquals("Example Story", card.title)
        assertEquals("https://example.com/story", card.description)
        assertEquals("https://example.com/story", card.url)
    }

    @Test
    fun independentTagsAreAppendedToContent() {
        val post =
            TumblrPost(
                idString = "tags-1",
                blogName = "staff",
                content = listOf(textBlock("Hello Tumblr")),
                tags = listOf("Tumblr", "#KMP", "two words", " "),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post

        assertEquals("Hello Tumblr\n#Tumblr #KMP #two words", timelinePost.content.raw)
    }

    @Test
    fun videoBlockWithMediaObjectIsMappedToTimelineMedia() {
        val post =
            TumblrPost(
                idString = "video-1",
                blogName = "staff",
                content =
                    listOf(
                        videoBlock(
                            url = "https://va.media.tumblr.com/tumblr_video.mp4",
                            posterUrl = "https://64.media.tumblr.com/video-poster.jpg",
                            width = 1280,
                            height = 720,
                            title = "Demo video",
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        val video = assertIs<UiMedia.Video>(timelinePost.images.single())

        assertEquals("https://va.media.tumblr.com/tumblr_video.mp4", video.url)
        assertEquals("https://64.media.tumblr.com/video-poster.jpg", video.thumbnailUrl)
        assertEquals("Demo video", video.description)
        assertEquals(1280f, video.width)
        assertEquals(720f, video.height)
    }

    @Test
    fun externalVideoEmbedIsMappedToCardInsteadOfTimelineMedia() {
        val post =
            TumblrPost(
                idString = "video-embed-1",
                blogName = "staff",
                content =
                    listOf(
                        externalVideoBlock(
                            url = "https://www.youtube.com/watch?v=demo",
                            posterUrl = "https://img.youtube.com/vi/demo/hqdefault.jpg",
                            title = "External video",
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post

        assertEquals(0, timelinePost.images.size)
        val card = assertNotNull(timelinePost.card)
        assertEquals("External video", card.title)
        assertEquals("https://www.youtube.com/watch?v=demo", card.url)
        assertEquals("https://img.youtube.com/vi/demo/hqdefault.jpg", card.media?.url)
    }

    @Test
    fun imageBlockUsesBestMediaVariantOnly() {
        val post =
            TumblrPost(
                idString = "photo-variant-1",
                blogName = "staff",
                content =
                    listOf(
                        imageBlock(
                            url = "https://64.media.tumblr.com/small.jpg",
                            width = 540,
                            height = 405,
                            altText = "image alt",
                            additionalMedia =
                                listOf(
                                    TestImageMedia(
                                        url = "https://64.media.tumblr.com/large.jpg",
                                        width = 1280,
                                        height = 960,
                                    ),
                                ),
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        val image = assertIs<UiMedia.Image>(timelinePost.images.single())

        assertEquals("https://64.media.tumblr.com/large.jpg", image.url)
        assertEquals(1280f, image.width)
        assertEquals(960f, image.height)
    }

    @Test
    fun allImagesAreInlineWhenAnyImageAppearsBetweenTextBlocks() {
        val post =
            TumblrPost(
                idString = "inline-images-1",
                blogName = "staff",
                content =
                    listOf(
                        imageBlock(
                            url = "https://64.media.tumblr.com/leading.jpg",
                            width = 320,
                            height = 240,
                            altText = "leading",
                        ),
                        textBlock("Before"),
                        imageBlock(
                            url = "https://64.media.tumblr.com/middle.jpg",
                            width = 640,
                            height = 480,
                            altText = "middle",
                        ),
                        textBlock("After"),
                        imageBlock(
                            url = "https://64.media.tumblr.com/trailing.jpg",
                            width = 800,
                            height = 600,
                            altText = "trailing",
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        val inlineImages = timelinePost.content.renderRuns.filterIsInstance<RenderContent.BlockImage>()

        assertEquals("Before\nAfter", timelinePost.content.raw)
        assertEquals(0, timelinePost.images.size)
        assertEquals(
            listOf(
                "https://64.media.tumblr.com/leading.jpg",
                "https://64.media.tumblr.com/middle.jpg",
                "https://64.media.tumblr.com/trailing.jpg",
            ),
            inlineImages.map { it.url },
        )
    }

    @Test
    fun textFormattingIsMappedToRichTextRuns() {
        val post =
            TumblrPost(
                idString = "rich-text-1",
                blogName = "staff",
                content =
                    listOf(
                        textBlock(
                            text = "Bold italic link",
                            formatting =
                                listOf(
                                    TumblrNpfFormatting(type = "bold", start = 0, end = 4),
                                    TumblrNpfFormatting(type = "italic", start = 4, end = 11),
                                    TumblrNpfFormatting(type = "link", start = 11, end = 16, url = "https://example.com"),
                                ),
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        val textContent = assertIs<RenderContent.Text>(timelinePost.content.renderRuns.single())
        val runs = textContent.runs.filterIsInstance<RenderRun.Text>()

        assertEquals("Bold", runs[0].text)
        assertTrue(runs[0].style.bold)
        assertEquals(" italic", runs[1].text)
        assertTrue(runs[1].style.italic)
        assertEquals(" link", runs[2].text)
        assertEquals("https://example.com", runs[2].style.link)
    }

    @Test
    fun reblogWithCommentMapsOriginalTrailPostAsQuote() {
        val post =
            TumblrPost(
                idString = "reblog-1",
                blogName = "me",
                blog = TumblrBlog(name = "me", title = "My Blog"),
                content = listOf(textBlock("My reblog comment")),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "original", title = "Original Blog"),
                            post = TumblrTrailPost(id = "original-1"),
                            content =
                                listOf(
                                    textBlock("Original text"),
                                    imageBlock(
                                        url = "https://64.media.tumblr.com/original.jpg",
                                        width = 640,
                                        height = 480,
                                        altText = "original alt",
                                    ),
                                ),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val quote = rendered.presentation.quotes.single()

        assertEquals("My reblog comment", rendered.post.content.raw)
        assertEquals(tumblrPostKey("original", "original-1"), quote.statusKey)
        assertEquals("Original Blog", quote.user?.name?.raw)
        assertEquals("Original text", quote.content.raw)
        val quoteImage = assertIs<UiMedia.Image>(quote.images.single())
        assertEquals("https://64.media.tumblr.com/original.jpg", quoteImage.url)
        val reference = rendered.post.references.single()
        assertEquals(
            ReferenceType.Retweet,
            reference.type,
        )
    }

    @Test
    fun reblogWithCommentAppendsOriginalTrailTagsToQuote() {
        val post =
            TumblrPost(
                idString = "reblog-tags",
                blogName = "me",
                blog = TumblrBlog(name = "me", title = "My Blog"),
                content = listOf(textBlock("My reblog comment")),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "original", title = "Original Blog"),
                            post = TumblrTrailPost(id = "original-tags", tags = listOf("fallback")),
                            content = listOf(textBlock("Original text")),
                            tags = listOf("Original Tag", "#NPF"),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val quote = rendered.presentation.quotes.single()

        assertEquals("Original text\n#Original Tag #NPF", quote.content.raw)
    }

    @Test
    fun reblogWithCommentFallsBackToTrailPostTagsForQuote() {
        val post =
            TumblrPost(
                idString = "reblog-post-tags",
                blogName = "me",
                blog = TumblrBlog(name = "me", title = "My Blog"),
                content = listOf(textBlock("My reblog comment")),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "original", title = "Original Blog"),
                            post = TumblrTrailPost(id = "original-post-tags", tags = listOf("Post Tag")),
                            content = listOf(textBlock("Original text")),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val quote = rendered.presentation.quotes.single()

        assertEquals("Original text\n#Post Tag", quote.content.raw)
    }

    @Test
    fun reblogWithCommentDoesNotDuplicateQuoteMediaOnRootPost() {
        val post =
            TumblrPost(
                idString = "reblog-duplicate-image",
                blogName = "me",
                blog = TumblrBlog(name = "me", title = "My Blog"),
                content =
                    listOf(
                        textBlock("My reblog comment"),
                        imageBlock(
                            url = "https://64.media.tumblr.com/original.jpg",
                            width = 640,
                            height = 480,
                            altText = "original alt",
                        ),
                    ),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "original", title = "Original Blog"),
                            post = TumblrTrailPost(id = "original-duplicate-image"),
                            content =
                                listOf(
                                    imageBlock(
                                        url = "https://64.media.tumblr.com/original.jpg",
                                        width = 640,
                                        height = 480,
                                        altText = "original alt",
                                    ),
                                ),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val quote = rendered.presentation.quotes.single()

        assertEquals(0, rendered.post.images.size)
        assertEquals("https://64.media.tumblr.com/original.jpg", assertIs<UiMedia.Image>(quote.images.single()).url)
    }

    @Test
    fun pureReblogDoesNotMapTrailPostAsQuote() {
        val post =
            TumblrPost(
                idString = "reblog-2",
                blogName = "me",
                blog = TumblrBlog(name = "me", title = "My Blog"),
                content = emptyList(),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "original", title = "Original Blog"),
                            post = TumblrTrailPost(id = "original-2"),
                            content = listOf(textBlock("Original text")),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val message = assertNotNull(rendered.presentation.message)
        val repost = assertNotNull(rendered.presentation.repost)

        assertEquals(0, rendered.presentation.quotes.size)
        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, assertIs<UiTimelineV2.Message.Type.Localized>(message.type).data)
        assertEquals(tumblrPostKey("me", "reblog-2"), message.statusKey)
        assertEquals(tumblrPostKey("original", "original-2"), repost.statusKey)
        assertEquals("Original text", repost.content.raw)
        assertEquals(1, rendered.post.references.size)
    }

    @Test
    fun pureReblogWithTagsMovesTagsToQuote() {
        val post =
            TumblrPost(
                idString = "reblog-tags-only",
                blogName = "me",
                blog = TumblrBlog(name = "me", title = "My Blog"),
                content = emptyList(),
                tags = listOf("Quoted Tag", "#NPF"),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "original", title = "Original Blog"),
                            post = TumblrTrailPost(id = "original-tags-only"),
                            content = listOf(textBlock("Original text")),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val quote = rendered.presentation.quotes.single()

        assertEquals("", rendered.post.content.raw)
        assertEquals("Original text\n#Quoted Tag #NPF", quote.content.raw)
    }

    @Test
    fun pureImageReblogDoesNotMapTrailPostAsQuote() {
        val post =
            TumblrPost(
                idString = "reblog-image",
                blogName = "me",
                blog = TumblrBlog(name = "me", title = "My Blog"),
                content =
                    listOf(
                        imageBlock(
                            url = "https://64.media.tumblr.com/original.jpg",
                            width = 640,
                            height = 480,
                            altText = "original alt",
                        ),
                    ),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "original", title = "Original Blog"),
                            post = TumblrTrailPost(id = "original-image"),
                            content =
                                listOf(
                                    imageBlock(
                                        url = "https://64.media.tumblr.com/original.jpg",
                                        width = 640,
                                        height = 480,
                                        altText = "original alt",
                                    ),
                                ),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        val repost = assertNotNull(rendered.presentation.repost)

        assertEquals(0, rendered.presentation.quotes.size)
        assertEquals("https://64.media.tumblr.com/original.jpg", assertIs<UiMedia.Image>(repost.images.single()).url)
    }

    @Test
    fun externalAudioPrefersPlayableMediaUrl() {
        val post =
            TumblrPost(
                idString = "audio-external",
                blogName = "staff",
                content =
                    listOf(
                        TumblrNpfBlock(
                            type = "audio",
                            provider = "soundcloud",
                            title = "Track",
                            url = "https://soundcloud.com/example/track",
                            media = listOf(TumblrNpfMedia(type = "audio/mp3", url = "https://cdn.example.com/track.mp3")),
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        assertEquals("https://cdn.example.com/track.mp3", assertIs<UiMedia.Audio>(timelinePost.images.single()).url)
    }

    @Test
    fun formattingOffsetsUseUnicodeCodePoints() {
        val post =
            TumblrPost(
                idString = "unicode-formatting",
                blogName = "staff",
                content =
                    listOf(
                        textBlock(
                            text = "😀a link",
                            formatting =
                                listOf(
                                    TumblrNpfFormatting(type = "link", start = 3, end = 7, url = "https://example.com"),
                                ),
                        ),
                    ),
            )

        val content = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post.content
        val runs = assertIs<RenderContent.Text>(content.renderRuns.single()).runs.filterIsInstance<RenderRun.Text>()
        assertEquals("😀a ", runs[0].text)
        assertEquals("link", runs[1].text)
        assertEquals("https://example.com", runs[1].style.link)
    }

    @Test
    fun completeReblogTrailIsPreservedInOrderWithRichText() {
        val post =
            TumblrPost(
                idString = "trail-chain",
                blogName = "me",
                content = emptyList(),
                trail =
                    listOf(
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "root"),
                            post = TumblrTrailPost(id = "root-id"),
                            content =
                                listOf(
                                    textBlock(
                                        "Root text",
                                        formatting = listOf(TumblrNpfFormatting(type = "bold", start = 0, end = 4)),
                                    ),
                                ),
                        ),
                        TumblrTrailItem(
                            blog = TumblrBlog(name = "parent"),
                            post = TumblrTrailPost(id = "parent-id"),
                            content = listOf(textBlock("Parent comment")),
                        ),
                    ),
            )

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey))
        assertEquals("Parent comment", assertNotNull(rendered.presentation.repost).content.raw)
        assertEquals(
            "Root text",
            rendered.presentation.inlineParents
                .single()
                .content.raw,
        )
        val rootRun =
            assertIs<RenderContent.Text>(
                rendered.presentation.inlineParents
                    .single()
                    .content.renderRuns
                    .single(),
            ).runs
                .filterIsInstance<RenderRun.Text>()
                .first()
        assertTrue(rootRun.style.bold)
    }

    @Test
    fun brokenTrailAndRowsVisibilityArePreserved() {
        val post =
            TumblrPost(
                idString = "broken-trail",
                blogName = "me",
                content = listOf(textBlock("My comment")),
                trail =
                    listOf(
                        TumblrTrailItem(
                            brokenBlogName = "gone-blog",
                            content = listOf(textBlock("First"), textBlock("Hidden"), textBlock("Third")),
                            layout =
                                listOf(
                                    TumblrNpfLayout(
                                        type = "rows",
                                        display = listOf(TumblrNpfLayoutDisplay(blocks = listOf(2, 0))),
                                    ),
                                ),
                        ),
                    ),
            )

        val quote = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).presentation.quotes.single()
        assertEquals("gone-blog", quote.user?.handle?.raw)
        assertEquals("Third\nFirst", quote.content.raw)
        assertIs<ClickEvent.Noop>(quote.clickEvent)
    }

    @Test
    fun imageCaptionAskGroupingAndHtmlDescriptionAreMapped() {
        val post =
            TumblrPost(
                idString = "ask-caption",
                blogName = "staff",
                content =
                    listOf(
                        imageBlock(
                            url = "https://64.media.tumblr.com/ask.jpg",
                            width = 640,
                            height = 480,
                            altText = "ask",
                        ).copy(caption = "Image caption"),
                        textBlock("Question"),
                        textBlock("Answer"),
                    ),
                layout =
                    listOf(
                        TumblrNpfLayout(
                            type = "ask",
                            blocks = listOf(1),
                            attribution = TumblrNpfAttribution(type = "blog", blog = TumblrBlog(name = "asker")),
                        ),
                    ),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post
        assertEquals("Image caption\nQuestion\nAnswer", timelinePost.content.raw)
        assertTrue(
            timelinePost.content.renderRuns
                .filterIsInstance<RenderContent.Text>()
                .any { it.block.isBlockQuote },
        )

        val profile = TumblrBlog(name = "staff", description = "<p>Hello <strong>Tumblr</strong> &amp; friends</p>").toUiProfile(accountKey)
        assertEquals("Hello Tumblr & friends", profile.description?.raw)
    }

    @Test
    fun missingBlogNameIsResolvedFromTumblrSubdomainUrl() {
        val profile = TumblrBlog(url = "https://staff.tumblr.com/").toUiProfile(accountKey)

        assertEquals(tumblrUserKey("staff"), profile.key)
    }

    @Test
    fun noteMetadataIsNotUsedAsAnActionTotal() {
        val post =
            TumblrPost(
                idString = "notes",
                blogName = "staff",
                reblogKey = "key",
                noteCount = 42,
                notes = listOf(TumblrNote(type = "like")),
            )

        val actions = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post.actions
        assertEquals(0, assertIs<ActionMenu.Item>(assertIs<ActionMenu.Group>(actions[0]).displayItem).count?.value)
        assertEquals(0, assertIs<ActionMenu.Item>(actions[1]).count?.value)
        val overflow = assertIs<ActionMenu.Group>(actions[2]).actions.filterIsInstance<ActionMenu.Item>()
        val notes = overflow.single { it.actionFamily == PostActionFamily.Comment }
        assertEquals(42L, notes.count?.value)
    }

    @Test
    fun privatePostUsesPrivateVisibility() {
        val post =
            TumblrPost(
                idString = "private-post",
                blogName = "staff",
                state = "private",
                content = listOf(textBlock("Private content")),
            )

        val timelinePost = assertIs<UiTimelineV2.TimelinePostItem>(post.toUiTimeline(accountKey)).post

        assertEquals(UiTimelineV2.Post.Visibility.Private, timelinePost.visibility)
    }

    private fun textBlock(
        text: String,
        formatting: List<TumblrNpfFormatting> = emptyList(),
        subtype: String? = null,
    ) = TumblrNpfBlock(
        type = "text",
        subtype = subtype,
        text = text,
        formatting = formatting,
    )

    private fun imageBlock(
        url: String,
        width: Int,
        height: Int,
        altText: String,
        additionalMedia: List<TestImageMedia> = emptyList(),
    ) = TumblrNpfBlock(
        type = "image",
        altText = altText,
        media =
            (listOf(TestImageMedia(url, width, height)) + additionalMedia).map { media ->
                TumblrNpfMedia(
                    url = media.url,
                    width = media.width,
                    height = media.height,
                )
            },
    )

    private data class TestImageMedia(
        val url: String,
        val width: Int,
        val height: Int,
    )

    private fun videoBlock(
        url: String,
        posterUrl: String,
        width: Int,
        height: Int,
        title: String,
    ) = TumblrNpfBlock(
        type = "video",
        title = title,
        poster = listOf(TumblrNpfMedia(url = posterUrl)),
        media =
            listOf(
                TumblrNpfMedia(
                    url = url,
                    type = "video/mp4",
                    width = width,
                    height = height,
                ),
            ),
    )

    private fun externalVideoBlock(
        url: String,
        posterUrl: String,
        title: String,
    ) = TumblrNpfBlock(
        type = "video",
        provider = "youtube",
        url = url,
        title = title,
        poster = listOf(TumblrNpfMedia(url = posterUrl)),
        media =
            listOf(
                TumblrNpfMedia(
                    url = url,
                    type = "text/html",
                ),
            ),
    )

    private fun linkBlock(
        url: String,
        title: String,
        description: String,
        posterUrl: String,
    ) = TumblrNpfBlock(
        type = "link",
        url = url,
        title = title,
        description = description,
        poster = listOf(TumblrNpfMedia(url = posterUrl)),
    )
}
