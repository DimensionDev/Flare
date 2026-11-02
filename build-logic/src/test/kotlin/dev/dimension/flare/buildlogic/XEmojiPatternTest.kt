package dev.dimension.flare.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XEmojiPatternTest {
    @Test
    fun convertsUtf16ClassesRangesAndPairsWithoutChangingBranches() {
        assertEquals(
            "[👨👩]|[🏻🏼🏽🏾🏿]|👨\\u200d👩",
            portableXEmojiPattern("""\ud83d[\udc68\udc69]|\ud83c[\udffb-\udfff]|\ud83d\udc68\u200d\ud83d\udc69"""),
        )
    }

    @Test
    fun rejectsUnpairedSurrogatesAndUnexpectedClasses() {
        assertFailsWith<IllegalStateException> { portableXEmojiPattern("""\ud83d""") }
        assertFailsWith<IllegalStateException> { portableXEmojiPattern("""\ud83d[abc]""") }
    }
}
