package dev.dimension.flare.data.network.xqt

import kotlin.test.Test
import kotlin.test.assertEquals

class XQTClientLanguageTest {
    @Test
    fun usesXWebLanguageCodes() {
        assertEquals("ja", xTwitterClientLanguage("ja-JP"))
        assertEquals("en-GB", xTwitterClientLanguage("en_GB"))
        assertEquals("zh-cn", xTwitterClientLanguage("zh"))
        assertEquals("zh-cn", xTwitterClientLanguage("zh-Hans-CN"))
        assertEquals("zh-tw", xTwitterClientLanguage("zh-Hant-TW"))
        assertEquals("zh-tw", xTwitterClientLanguage("zh-HK"))
        assertEquals("msa", xTwitterClientLanguage("ms-MY"))
        assertEquals("no", xTwitterClientLanguage("nb-NO"))
        assertEquals("tl", xTwitterClientLanguage("fil-PH"))
        assertEquals("en", xTwitterClientLanguage(""))
    }
}
