package dev.dimension.flare.ui.component

import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyMarkdownTest {
    @Test
    fun inlineImageKeepsSurroundingTextAndAlternativeText() {
        val root =
            CommonmarkAstNodeParser().parse(
                "Before **bold** *italic* ~~strike~~ `code` [link](https://example.com) ![**image** alt](https://example.com/p.png) after",
            )
        assertEquals("Before bold italic strike code link image alt after", proxyMarkdownInline(root.links.firstChild!!).text)
    }

    @Test
    fun lineBreaksAndCodeContainingImageSyntaxStayLiteral() {
        val root =
            CommonmarkAstNodeParser().parse(
                "Before\nsoft  \nhard `![literal](https://example.com/code.png)` ![image](https://example.com/p.png)",
            )
        assertEquals("Before soft\nhard ![literal](https://example.com/code.png) image", proxyMarkdownInline(root.links.firstChild!!).text)
    }
}
