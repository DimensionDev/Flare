package dev.dimension.flare.data.network.rss.model

import dev.dimension.flare.common.Xml
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeedTest {
    @Test
    fun deserializesAtomFeed() {
        val xml =
            """
            <feed>
                <id>urn:uuid:60a7</id>
                <title>Example Feed</title>
                <updated>2025-11-20T10:00:00Z</updated>
                <author>
                    <name>John Doe</name>
                    <email>john@example.com</email>
                </author>
                <link href="https://example.com/" />
                <entry>
                    <id>urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a</id>
                    <title>Atom-Powered Robots Run Amok</title>
                    <updated>2025-11-19T10:00:00Z</updated>
                    <content type="html">&lt;p&gt;Some content.&lt;/p&gt;</content>
                    <link href="https://example.com/entries/1" />
                </entry>
            </feed>
            """.trimIndent()

        val feed = Xml.decodeFromString<Feed>(xml)

        assertTrue(feed is Feed.Atom)
        assertEquals("urn:uuid:60a7", feed.id)
        assertEquals("Example Feed", feed.title.value)
        assertEquals("2025-11-20T10:00:00Z", feed.updated)
        assertEquals("John Doe", feed.authors.first().name)
        assertEquals("https://example.com/", feed.links.first().href)
        assertEquals(1, feed.entries.size)

        val entry = feed.entries.first()
        assertEquals("urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a", entry.id)
        assertEquals("Atom-Powered Robots Run Amok", entry.title?.value)
        assertEquals("2025-11-19T10:00:00Z", entry.updated)
        assertEquals("<p>Some content.</p>", entry.content?.value)
        assertEquals("https://example.com/entries/1", entry.links.first().href)
    }

    @Test
    fun deserializesRss20Feed() {
        val xml =
            """
            <rss version="2.0">
              <channel>
                <title>RSS Title</title>
                <link>https://example.com/</link>
                <description>This is an example RSS feed</description>
                <language>en-us</language>
                <item>
                  <title>Example entry</title>
                  <link>https://example.com/item1</link>
                  <description>This is an example description</description>
                  <author>editor@example.com</author>
                  <pubDate>Thu, 20 Nov 2025 10:00:00 +0000</pubDate>
                  <guid isPermaLink="false">abc123</guid>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val feed = Xml.decodeFromString<Feed>(xml)

        assertTrue(feed is Feed.Rss20)
        assertEquals("2.0", feed.version)
        assertEquals("RSS Title", feed.channel.title)
        assertEquals("https://example.com/", feed.channel.link)
        assertEquals("This is an example RSS feed", feed.channel.description)
        assertEquals("en-us", feed.channel.language)
        assertEquals(1, feed.channel.items.size)

        val item = feed.channel.items.first()
        assertEquals("Example entry", item.title)
        assertEquals("https://example.com/item1", item.link)
        assertEquals("This is an example description", item.description)
        assertEquals("editor@example.com", item.author)
        assertEquals("Thu, 20 Nov 2025 10:00:00 +0000", item.pubDate)
        assertNotNull(item.guid)
        assertEquals("abc123", item.guid.value)
        assertEquals(false, item.guid.isPermaLink)
    }

    @Test
    fun deserializesRdfFeed() {
        val xml =
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Example RDF Feed</title>
                <link>https://example.com/</link>
                <description>RDF example</description>
                <dc:date>2025-11-20</dc:date>
              </channel>
              <item>
                <title>Example item</title>
                <link>https://example.com/item</link>
                <description>Item description</description>
                <dc:date>2025-11-19</dc:date>
              </item>
            </rdf:RDF>
            """.trimIndent()

        val feed = Xml.decodeFromString<Feed>(xml)

        assertTrue(feed is Feed.RDF)
        assertEquals("Example RDF Feed", feed.channel.title)
        assertEquals("https://example.com/", feed.channel.link)
        assertEquals("RDF example", feed.channel.description)
        assertEquals("2025-11-20", feed.channel.date)
        assertEquals(1, feed.items.size)

        val item = feed.items.first()
        assertEquals("Example item", item.title)
        assertEquals("https://example.com/item", item.link)
        assertEquals("Item description", item.description)
        assertEquals("2025-11-19", item.date)
    }

    @Test
    fun deserializesRss20WithMediaNamespace() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:webfeeds="http://webfeeds.org/rss/1.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>#catsofmastodon</title>
                <description>#catsofmastodon 标签下的公开嘟文</description>
                <link>https://mastodon.ml/tags/catsofmastodon</link>
                <lastBuildDate>Fri, 05 Dec 2025 03:00:05 +0000</lastBuildDate>
                <generator>Mastodon v4.4.5</generator>
                <item>
                  <guid isPermaLink="true">https://piaille.fr/@CatEveryHour/115664722695684204</guid>
                  <link>https://piaille.fr/@CatEveryHour/115664722695684204</link>
                  <pubDate>Fri, 05 Dec 2025 03:00:05 +0000</pubDate>
                  <description>&lt;p&gt;✨ c a t ✨  &lt;a href="https://piaille.fr/tags/Cats" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;Cats&lt;/span&gt;&lt;/a&gt; &lt;a href="https://piaille.fr/tags/MastoCats" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;MastoCats&lt;/span&gt;&lt;/a&gt; &lt;a href="https://piaille.fr/tags/catsofmastodon" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;catsofmastodon&lt;/span&gt;&lt;/a&gt;&lt;/p&gt;</description>
                  <media:content url="https://mastodon.ml/system/cache/media_attachments/files/115/664/722/791/925/863/original/d39cd2aa0c15dc12.jpeg" type="image/jpeg" fileSize="62897" medium="image">
                    <media:rating scheme="urn:simple">nonadult</media:rating>
                  </media:content>
                  <category>cats</category>
                  <category>mastocats</category>
                  <category>catsofmastodon</category>
                </item>
                <item>
                  <guid isPermaLink="true">https://mastodon.social/@EricIndiana/115664612315832148</guid>
                  <link>https://mastodon.social/@EricIndiana/115664612315832148</link>
                  <pubDate>Fri, 05 Dec 2025 02:32:01 +0000</pubDate>
                  <description>&lt;p&gt;&lt;a href="https://mastodon.social/tags/love" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;love&lt;/span&gt;&lt;/a&gt; &lt;a href="https://mastodon.social/tags/lovers" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;lovers&lt;/span&gt;&lt;/a&gt; &lt;a href="https://mastodon.social/tags/friends" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;friends&lt;/span&gt;&lt;/a&gt; &lt;a href="https://mastodon.social/tags/buddies" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;buddies&lt;/span&gt;&lt;/a&gt; &lt;a href="https://mastodon.social/tags/cats" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;cats&lt;/span&gt;&lt;/a&gt; &lt;a href="https://mastodon.social/tags/catsofmastodon" class="mention hashtag" rel="nofollow noopener" target="_blank"&gt;#&lt;span&gt;catsofmastodon&lt;/span&gt;&lt;/a&gt;&lt;/p&gt;</description>
                  <media:content url="https://mastodon.ml/system/cache/media_attachments/files/115/664/612/428/936/969/original/2b37809a4e6ab1d7.jpeg" type="image/jpeg" fileSize="723819" medium="image">
                    <media:rating scheme="urn:simple">nonadult</media:rating>
                    <media:description type="plain">a black cat and a white cat cuddle</media:description>
                  </media:content>
                  <media:content url="https://mastodon.ml/system/cache/media_attachments/files/115/664/612/451/363/856/original/5dc47d7bf683fce5.jpeg" type="image/jpeg" fileSize="750455" medium="image">
                    <media:rating scheme="urn:simple">nonadult</media:rating>
                    <media:description type="plain">a black cat and a white cat cuddle and smile</media:description>
                  </media:content>
                  <category>love</category>
                  <category>lovers</category>
                  <category>friends</category>
                  <category>buddies</category>
                  <category>cats</category>
                  <category>catsofmastodon</category>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val feed = Xml.decodeFromString<Feed>(xml)

        assertTrue(feed is Feed.Rss20)
        assertEquals("2.0", feed.version)
        assertEquals("#catsofmastodon", feed.channel.title)
        assertEquals("https://mastodon.ml/tags/catsofmastodon", feed.channel.link)
        assertEquals("#catsofmastodon 标签下的公开嘟文", feed.channel.description)
        assertEquals("Fri, 05 Dec 2025 03:00:05 +0000", feed.channel.lastBuildDate)
        assertEquals("Mastodon v4.4.5", feed.channel.generator)
        assertEquals(2, feed.channel.items.size)

        val first = feed.channel.items[0]
        assertEquals("https://piaille.fr/@CatEveryHour/115664722695684204", first.guid?.value)
        assertEquals(true, first.guid?.isPermaLink)
        assertEquals("https://piaille.fr/@CatEveryHour/115664722695684204", first.link)
        assertEquals("Fri, 05 Dec 2025 03:00:05 +0000", first.pubDate)
        assertTrue(first.description?.contains("catsofmastodon") == true)

        val second = feed.channel.items[1]
        assertEquals("https://mastodon.social/@EricIndiana/115664612315832148", second.guid?.value)
        assertEquals(true, second.guid?.isPermaLink)
        assertEquals("https://mastodon.social/@EricIndiana/115664612315832148", second.link)
        assertEquals("Fri, 05 Dec 2025 02:32:01 +0000", second.pubDate)
        assertTrue(second.description?.contains("catsofmastodon") == true)
    }
}
