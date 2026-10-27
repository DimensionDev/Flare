package dev.dimension.flare.ui.render

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaImageLinkRenderingTest {
    @Test
    fun imageLinkRendersAsASignedAnchor() {
        val image = UiMedia.Image("https://cdn.example/image.jpg", proxy = ProxyRef.Account(MicroBlogKey("viewer", "weibo.cn")))
        val text = parseHtml("""<a href="${image.url}">查看图片</a>""").toUi(imageLinks = mapOf(image.url to image))
        val block = assertIs<RenderContent.Text>(text.renderRuns.single())
        val link = requireNotNull(assertIs<RenderRun.Text>(block.runs.single()).style.resolvedLink)
        assertTrue(text.platformText.contains("""<a href="$link""""))
    }
}
