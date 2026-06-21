package dev.dimension.flare.ui.model

import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.render.RenderBlockStyle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class UiArticleTest {
    @Test
    fun serializes_article_content_blocks() {
        val article =
            UiArticle(
                key = "article:1",
                title = "Title",
                cover =
                    UiMedia.Image(
                        url = "https://example.com/cover.jpg",
                        previewUrl = "https://example.com/cover.jpg",
                        description = null,
                        width = 1200f,
                        height = 630f,
                        sensitive = false,
                    ),
                author =
                    UiArticleAuthor.Rss(
                        siteName = "Example",
                        byline = "Author",
                        iconUrl = "https://example.com/favicon.ico",
                    ),
                sourceUrl = "https://example.com/article",
                publishDate = Instant.parse("2024-01-02T03:04:05Z").toUi(),
                content =
                    uiArticleContentOf(
                        blocks =
                            listOf(
                                UiArticleBlock.Text(
                                    key = "text:0",
                                    content =
                                        RenderContent.Text(
                                            runs = persistentListOf(RenderRun.Text("Hello")),
                                            block = RenderBlockStyle(headingLevel = 1),
                                        ),
                                ),
                                UiArticleBlock.Image(
                                    key = "image:1",
                                    media =
                                        UiMedia.Image(
                                            url = "https://example.com/image.jpg",
                                            previewUrl = "https://example.com/image.jpg",
                                            description = "Alt",
                                            width = 300f,
                                            height = 200f,
                                            sensitive = false,
                                        ),
                                ),
                                UiArticleBlock.Video(
                                    key = "video:2",
                                    media =
                                        UiMedia.Video(
                                            url = "https://example.com/video.mp4",
                                            thumbnailUrl = "",
                                            description = "Video",
                                            width = 16f,
                                            height = 9f,
                                        ),
                                ),
                                UiArticleBlock.File(
                                    key = "file:3",
                                    name = "sample.pdf",
                                    url = "https://example.com/sample.pdf",
                                    sizeBytes = 1024L,
                                    extension = "pdf",
                                ),
                                UiArticleBlock.Embed(
                                    key = "embed:4",
                                    url = "https://example.com/embed",
                                    title = "Embed",
                                    description = "Description",
                                ),
                                UiArticleBlock.ContentGate(
                                    key = "gate:5",
                                    reason =
                                        UiArticleContentGateReason.SubscriptionRequired(
                                            platformType = PlatformType.Fanbox,
                                            feeRequired = 500,
                                        ),
                                    actionUrl = "https://example.com/support",
                                ),
                            ),
                    ),
            )

        val decoded = Json.decodeFromString<UiArticle>(Json.encodeToString(article))

        assertEquals(article.key, decoded.key)
        assertEquals("https://example.com/cover.jpg", decoded.cover?.url)
        assertEquals(1200f / 630f, decoded.cover?.aspectRatio)
        assertEquals(Instant.parse("2024-01-02T03:04:05Z"), decoded.publishDate?.value)
        assertEquals("https://example.com/article", decoded.sourceUrl)
        assertEquals("Hello\nAlt\nsample.pdf\nEmbed\nDescription\nhttps://example.com/embed", decoded.content.rawText)
        val author = assertIs<UiArticleAuthor.Rss>(decoded.author)
        assertEquals("Example", author.siteName)
        assertEquals("Author", author.byline)

        val text = assertIs<UiArticleBlock.Text>(decoded.content.blocks[0])
        assertEquals(1, text.content.block.headingLevel)
        assertEquals("Hello", assertIs<RenderRun.Text>(text.content.runs.single()).text)

        val image = assertIs<UiArticleBlock.Image>(decoded.content.blocks[1])
        assertEquals(1.5f, image.media.aspectRatio)
        assertEquals("Alt", image.media.description)

        val video = assertIs<UiArticleBlock.Video>(decoded.content.blocks[2])
        assertEquals(16f / 9f, video.media.aspectRatio)
        assertEquals("https://example.com/video.mp4", video.media.url)

        val gate = assertIs<UiArticleBlock.ContentGate>(decoded.content.blocks[5])
        val reason = assertIs<UiArticleContentGateReason.SubscriptionRequired>(gate.reason)
        assertEquals(PlatformType.Fanbox, reason.platformType)
        assertEquals(500, reason.feeRequired)
        assertEquals("https://example.com/support", gate.actionUrl)
    }
}
