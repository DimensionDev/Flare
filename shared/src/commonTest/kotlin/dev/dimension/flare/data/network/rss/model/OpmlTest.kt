package dev.dimension.flare.data.network.rss.model

import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import kotlin.test.Test
import kotlin.test.assertEquals

class OpmlTest {
    @Test
    fun testOpmlDeserialization() {
        val xml =
            """
            <opml version="2.0">
                <head>
                    <title>My Feeds</title>
                </head>
                <body>
                    <outline text="Tech" title="Tech">
                        <outline type="rss" text="The Verge" title="The Verge" xmlUrl="https://www.theverge.com/rss/index.xml" htmlUrl="https://www.theverge.com/"/>
                    </outline>
                    <outline type="rss" text="Daring Fireball" title="Daring Fireball" xmlUrl="https://daringfireball.net/feeds/main" htmlUrl="https://daringfireball.net/"/>
                </body>
            </opml>
            """.trimIndent()

        val opml = XML.decodeFromString<Opml>(xml)

        assertEquals("2.0", opml.version)
        assertEquals("My Feeds", opml.head.title)
        assertEquals(2, opml.body.outlines.size)

        val techOutline = opml.body.outlines[0]
        assertEquals("Tech", techOutline.text)
        assertEquals(1, techOutline.outlines.size)

        val vergeOutline = techOutline.outlines[0]
        assertEquals("The Verge", vergeOutline.text)
        assertEquals("https://www.theverge.com/rss/index.xml", vergeOutline.xmlUrl)

        val dfOutline = opml.body.outlines[1]
        assertEquals("Daring Fireball", dfOutline.text)
        assertEquals("https://daringfireball.net/feeds/main", dfOutline.xmlUrl)
    }

    @Test
    fun testWalterlvOpml() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <head>
                <title>walterlv</title>
              </head>
              <body>
                <outline text="walterlv" title="walterlv" type="rss" xmlUrl="https://blog.walterlv.com/feed.xml" htmlUrl="https://blog.walterlv.com/" />

                <outline title="Team" text="Team">
                  <outline text="林德熙" title="林德熙" type="rss" xmlUrl="https://blog.lindexi.com/feed.xml" htmlUrl="https://blog.lindexi.com/" />
                </outline>

                <outline title="Microsoft" text="Microsoft">
                  <outline text="Microsoft .NET Blog" title="Microsoft .NET Blog" type="rss" xmlUrl="https://blogs.msdn.microsoft.com/dotnet/feed/"/>
                  <outline text="Microsoft The Visual Studio Blog" title="Microsoft The Visual Studio Blog" type="rss" xmlUrl="https://blogs.msdn.microsoft.com/visualstudio/feed/"/>
                </outline>
              </body>
            </opml>
            """.trimIndent()

        val opml = XML.decodeFromString<Opml>(xml)

        assertEquals("1.0", opml.version)
        assertEquals("walterlv", opml.head.title)
        assertEquals(3, opml.body.outlines.size)

        val walterlv = opml.body.outlines[0]
        assertEquals("walterlv", walterlv.text)
        assertEquals("https://blog.walterlv.com/feed.xml", walterlv.xmlUrl)

        val team = opml.body.outlines[1]
        assertEquals("Team", team.text)
        assertEquals(1, team.outlines.size)
        assertEquals("林德熙", team.outlines[0].text)
        assertEquals("https://blog.lindexi.com/feed.xml", team.outlines[0].xmlUrl)

        val microsoft = opml.body.outlines[2]
        assertEquals("Microsoft", microsoft.text)
        assertEquals(2, microsoft.outlines.size)
        assertEquals("Microsoft .NET Blog", microsoft.outlines[0].text)
        assertEquals("Microsoft The Visual Studio Blog", microsoft.outlines[1].text)
    }
}
