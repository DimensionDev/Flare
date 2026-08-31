package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.fanbox.FanboxPostDetailBody
import dev.dimension.flare.data.network.fanbox.FanboxPostDetailResponse
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.render.RenderRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FanboxMapperTest {
    @Test
    fun articleTextParsesHttpLinks() {
        val article =
            FanboxPostDetailBody(
                body =
                    FanboxPostDetailBody.BodyContent(
                        blocks =
                            listOf(
                                FanboxPostDetailBody.Block(
                                    type = "p",
                                    text = "See http://example.com and https://example.org/path.",
                                ),
                            ),
                    ),
                creatorId = "creator",
                id = "post",
                publishedDatetime = "2024-01-02T03:04:05+00:00",
                title = "Title",
            ).toUiArticle(accountKey = MicroBlogKey(id = "1", host = "fanbox.cc"))

        val block = assertIs<UiArticleBlock.Text>(article.content.blocks.single())
        val runs = block.content.runs.map { assertIs<RenderRun.Text>(it) }

        assertEquals("See ", runs[0].text)
        assertEquals("http://example.com", runs[1].text)
        assertEquals("http://example.com", runs[1].style.link)
        assertEquals(" and ", runs[2].text)
        assertEquals("https://example.org/path", runs[3].text)
        assertEquals("https://example.org/path", runs[3].style.link)
        assertEquals(".", runs[4].text)
    }

    @Test
    fun articlePreservesTextMetadataAndEmbeds() {
        val article = decodeArticle(MODERN_ARTICLE_RESPONSE)
        val blocks = article.content.blocks

        val textBlock = assertIs<UiArticleBlock.Text>(blocks[0])
        val runs = textBlock.content.runs.map { assertIs<RenderRun.Text>(it) }
        val linkedRun = runs.single { it.text == "this" }
        val clampedRun = runs.single { it.text == "link" }
        assertTrue(linkedRun.style.bold)
        assertEquals("https://example.com/linked", linkedRun.style.link)
        assertTrue(clampedRun.style.italic)

        val urlEmbed = assertIs<UiArticleBlock.Embed>(blocks[1])
        assertEquals("https://example.com/article", urlEmbed.url)

        val providerEmbed = assertIs<UiArticleBlock.Embed>(blocks[2])
        assertEquals("YouTube", providerEmbed.title)
        assertEquals("https://www.youtube.com/watch?v=abc123", providerEmbed.url)

        val unknownProviderEmbed = assertIs<UiArticleBlock.Embed>(blocks[3])
        assertEquals("new service", unknownProviderEmbed.title)
        assertEquals("https://www.fanbox.cc/@creator/posts/post", unknownProviderEmbed.url)
    }

    @Test
    fun legacyVideoPostMapsProviderEmbed() {
        val article = decodeArticle(LEGACY_VIDEO_RESPONSE)
        val block = assertIs<UiArticleBlock.Embed>(article.content.blocks.single())

        assertEquals("Vimeo", block.title)
        assertEquals("https://vimeo.com/12345", block.url)
    }

    @Test
    fun legacyEntryPostUsesHtmlRenderer() {
        val article = decodeArticle(LEGACY_ENTRY_RESPONSE)
        val block = assertIs<UiArticleBlock.Text>(article.content.blocks.single())
        val runs = block.content.runs.map { assertIs<RenderRun.Text>(it) }

        assertEquals("Hello world.", runs.joinToString(separator = "") { it.text })
        assertTrue(runs.single { it.text == "world" }.style.bold)
    }

    private fun decodeArticle(response: String) =
        JSON
            .decodeFromString(FanboxPostDetailResponse.serializer(), response)
            .body
            .toUiArticle(accountKey = MicroBlogKey(id = "1", host = "fanbox.cc"))

    private companion object {
        val MODERN_ARTICLE_RESPONSE =
            """
            {
              "body": {
                "post": {
                  "id": "post",
                  "creatorId": "creator",
                  "publishedDatetime": "2024-01-02T03:04:05+00:00",
                  "type": "article",
                  "body": {
                    "blocks": [
                      {
                        "type": "p",
                        "text": "Read this link",
                        "styles": [
                          { "type": "bold", "offset": 5, "length": 4 },
                          { "type": "italic", "offset": 10, "length": 100 },
                          { "type": "underline", "offset": 100, "length": 1 }
                        ],
                        "links": [
                          { "offset": 5, "length": 4, "url": "https://example.com/linked" }
                        ]
                      },
                      {
                        "type": "url_embed",
                        "urlEmbedId": "url-1",
                        "styles": null,
                        "links": null
                      },
                      { "type": "embed", "embedId": "embed-1" },
                      { "type": "embed", "embedId": "embed-2" }
                    ],
                    "urlEmbedMap": {
                      "url-1": {
                        "id": "url-1",
                        "type": "html.card",
                        "url": "https://example.com/article"
                      }
                    },
                    "embedMap": {
                      "embed-1": {
                        "serviceProvider": "youtube",
                        "videoId": "abc123"
                      },
                      "embed-2": {
                        "serviceProvider": "new_service",
                        "contentId": "opaque-id"
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val LEGACY_VIDEO_RESPONSE =
            """
            {
              "body": {
                "post": {
                  "id": "video-post",
                  "creatorId": "creator",
                  "publishedDatetime": "2024-01-02T03:04:05+00:00",
                  "type": "video",
                  "body": {
                    "video": {
                      "serviceProvider": "vimeo",
                      "videoId": "12345"
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val LEGACY_ENTRY_RESPONSE =
            """
            {
              "body": {
                "post": {
                  "id": "entry-post",
                  "creatorId": "creator",
                  "publishedDatetime": "2024-01-02T03:04:05+00:00",
                  "type": "entry",
                  "body": {
                    "html": "<p>Hello <strong>world</strong>.</p>"
                  }
                }
              }
            }
            """.trimIndent()
    }
}
