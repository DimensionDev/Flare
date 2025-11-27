package dev.dimension.flare.data.network.rss.model

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

        val opml = decodeOpml(xml)

        assertEquals("2.0", opml.version)
        assertEquals("My Feeds", opml.head?.title)
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

        val opml = decodeOpml(xml)

        assertEquals("1.0", opml.version)
        assertEquals("walterlv", opml.head?.title)
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

    @Test
    fun testOpml() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
                <head>
                    <title>RSS Feeds Export</title>
                    <dateCreated>2025-11-20 14:46:05</dateCreated>
                </head>
                <body>
                    <outline text="Rolling Stone" title="Rolling Stone" type="rss" description="Music, Film, TV and Political News Coverage" htmlUrl="https://www.rollingstone.com" xmlUrl="https://www.rollingstone.com/music/feed/"/>
                    <outline text="New York Times" title="New York Times" type="rss" description="Comprehensive news coverage spanning global events, politics, culture, and opinion, with in-depth reporting and analysis." htmlUrl="https://www.nytimes.com" xmlUrl="https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"/>
                    <outline text="Fast Company" title="Fast Company" type="rss" description="Fast Company inspires a new breed of innovative and creative thought leaders who are actively inventing the future of business." htmlUrl="https://www.fastcompany.com/" xmlUrl="https://www.fastcompany.com/latest/rss"/>
                    <outline text="NPR News (Latest)" title="NPR News (Latest)" type="rss" description="U.S. and world news from NPR, America’s public radio network (hourly updated headlines)." htmlUrl="https://www.npr.org/sections/news/" xmlUrl="https://feeds.npr.org/1001/rss.xml"/>
                    <outline text="BBC News – World" title="BBC News – World" type="rss" description="Global news from the BBC (British public broadcaster) covering major international stories." htmlUrl="https://www.bbc.co.uk/news/world" xmlUrl="https://feeds.bbci.co.uk/news/world/rss.xml"/>
                </body>
            </opml>
            """.trimIndent()

        val opml = decodeOpml(xml)

        assertEquals("2.0", opml.version)
        assertEquals("RSS Feeds Export", opml.head?.title)
        assertEquals("2025-11-20 14:46:05", opml.head?.dateCreated)
        assertEquals(5, opml.body.outlines.size)

        val outlines = opml.body.outlines

        val rollingStone = outlines[0]
        assertEquals("Rolling Stone", rollingStone.text)
        assertEquals("https://www.rollingstone.com/music/feed/", rollingStone.xmlUrl)
        assertEquals("https://www.rollingstone.com", rollingStone.htmlUrl)
        assertEquals("Music, Film, TV and Political News Coverage", rollingStone.description)

        val nyTimes = outlines[1]
        assertEquals("New York Times", nyTimes.text)
        assertEquals("https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", nyTimes.xmlUrl)
        assertEquals("https://www.nytimes.com", nyTimes.htmlUrl)
        assertEquals(
            "Comprehensive news coverage spanning global events, politics, culture, and opinion, with in-depth reporting and analysis.",
            nyTimes.description,
        )

        val fastCompany = outlines[2]
        assertEquals("Fast Company", fastCompany.text)
        assertEquals("https://www.fastcompany.com/latest/rss", fastCompany.xmlUrl)
        assertEquals("https://www.fastcompany.com/", fastCompany.htmlUrl)
        assertEquals(
            "Fast Company inspires a new breed of innovative and creative thought leaders who are actively inventing the future of business.",
            fastCompany.description,
        )

        val npr = outlines[3]
        assertEquals("NPR News (Latest)", npr.text)
        assertEquals("https://feeds.npr.org/1001/rss.xml", npr.xmlUrl)
        assertEquals("https://www.npr.org/sections/news/", npr.htmlUrl)
        assertEquals(
            "U.S. and world news from NPR, America’s public radio network (hourly updated headlines).",
            npr.description,
        )

        val bbc = outlines[4]
        assertEquals("BBC News – World", bbc.text)
        assertEquals("https://feeds.bbci.co.uk/news/world/rss.xml", bbc.xmlUrl)
        assertEquals("https://www.bbc.co.uk/news/world", bbc.htmlUrl)
        assertEquals(
            "Global news from the BBC (British public broadcaster) covering major international stories.",
            bbc.description,
        )
    }

    @Test
    fun testFeedsOpml() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
              <opml version="2.0">
                <body>
                  <outline text="example" type="rss" xmlUrl="https://example.com" htmlUrl="http://example.com" category="example" />                
                </body>
              </opml>
            """.trimIndent()

        val opml = decodeOpml(xml)

        assertEquals("2.0", opml.version)
        assertEquals(1, opml.body.outlines.size)

        val first = opml.body.outlines[0]
        assertEquals("example", first.text)
        assertEquals("https://example.com", first.xmlUrl)
        assertEquals("http://example.com", first.htmlUrl)
        assertEquals("example", first.category)
    }

    @Test
    fun testFeedsOpml2() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
              <opml version="2.0">
                <body>
                  <outline text="example" type="rss" xmlUrl="https://example.com?a=a&b=b" htmlUrl="https://example.com?a=a&b=b" category="example" />                
                </body>
              </opml>
            """.trimIndent()

        val opml = decodeOpml(xml)

        assertEquals("2.0", opml.version)
        assertEquals(1, opml.body.outlines.size)

        val first = opml.body.outlines[0]
        assertEquals("example", first.text)
        assertEquals("https://example.com?a=a&b=b", first.xmlUrl)
        assertEquals("https://example.com?a=a&b=b", first.htmlUrl)
        assertEquals("example", first.category)
    }

    @Test
    fun testFeedsOpml3() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
              <opml version="2.0">
                <body>
                  <outline text="example" type="rss" xmlUrl="https://example.com?a=a&amp;b=b" htmlUrl="https://example.com?a=a&amp;b=b" category="example" />                
                </body>
              </opml>
            """.trimIndent()

        val opml = decodeOpml(xml)

        assertEquals("2.0", opml.version)
        assertEquals(1, opml.body.outlines.size)

        val first = opml.body.outlines[0]
        assertEquals("example", first.text)
        assertEquals("https://example.com?a=a&b=b", first.xmlUrl)
        assertEquals("https://example.com?a=a&b=b", first.htmlUrl)
        assertEquals("example", first.category)
    }
}
