package dev.dimension.flare.data.network.rss

import dev.dimension.flare.data.network.rss.model.Feed
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RssServiceRegressionTest {
    @Test
    fun decodesGbkXmlUsingXmlDeclarationWhenHeaderHasNoCharset() {
        val xml =
            """
            <?xml version="1.0" encoding="gbk"?>
            <rss version="2.0">
              <channel>
                <title>测试订阅</title>
                <link>https://example.com/forum.php</link>
                <description>示例数据</description>
                <item>
                  <title>第一条</title>
                  <link>https://example.com/thread-1-1-1.html</link>
                  <description><![CDATA[这是一条用于回归测试的本地数据]]></description>
                  <pubDate>Tue, 07 Apr 2026 01:29:20 +0000</pubDate>
                </item>
              </channel>
            </rss>
            """.trimIndent()
        val bytes = xml.toByteArray(Charset.forName("GBK"))

        val decoded = decodeResponseBody(bytes, headerCharset = null)
        val feed = parseFeedText(decoded)

        assertIs<Feed.Rss20>(feed)
        assertEquals("测试订阅", feed.channel.title)
        assertEquals("https://example.com/forum.php", feed.channel.link)
        assertEquals("第一条", feed.channel.items.first().title)
        assertTrue(feed.channel.items.first().description?.contains("本地数据") == true)
    }

    @Test
    fun prefersCharsetFromHeaderWhenProvided() {
        val xml =
            """
            <rss version="2.0">
              <channel>
                <title>Header Charset</title>
                <link>https://example.com/</link>
              </channel>
            </rss>
            """.trimIndent()
        val bytes = xml.toByteArray(Charsets.UTF_8)

        val decoded = decodeResponseBody(bytes, headerCharset = "utf-8")

        assertTrue(decoded.contains("Header Charset"))
    }
}
