package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.network.fanbox.FanboxPostDetailBody
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.render.RenderRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
