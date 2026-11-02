package dev.dimension.flare.ui.component.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusContentLineLimitTest {
    @Test
    fun collapsesOnlyAfterFifteenVisualLines() {
        assertFalse(shouldCollapseRichText(fullHeight = 300, lineHeight = 20, collapseThresholdLines = 15))
        assertTrue(shouldCollapseRichText(fullHeight = 301, lineHeight = 20, collapseThresholdLines = 15))
        assertEquals(
            300,
            collapsedRichTextHeight(
                fullHeight = 300,
                lineHeight = 20,
                lineLimit = 5,
                collapseThresholdLines = 15,
            ),
        )
        assertEquals(
            100,
            collapsedRichTextHeight(
                fullHeight = 301,
                lineHeight = 20,
                lineLimit = 5,
                collapseThresholdLines = 15,
            ),
        )
    }

    @Test
    fun explicitShortPreviewClipsTheWholeContent() {
        assertEquals(60, collapsedRichTextHeight(fullHeight = 200, lineHeight = 20, lineLimit = 3, collapseThresholdLines = 3))
        assertEquals(40, collapsedRichTextHeight(fullHeight = 40, lineHeight = 20, lineLimit = 3, collapseThresholdLines = 3))
    }

    @Test
    fun largerLineLimitDoesNotCollapseContentThatAlreadyFits() {
        assertEquals(320, collapsedRichTextHeight(fullHeight = 320, lineHeight = 20, lineLimit = 20, collapseThresholdLines = 15))
        assertEquals(400, collapsedRichTextHeight(fullHeight = 420, lineHeight = 20, lineLimit = 20, collapseThresholdLines = 15))
    }

    @Test
    fun veryLargeLineLimitsDoNotOverflow() {
        assertEquals(
            240,
            collapsedRichTextHeight(fullHeight = 240, lineHeight = 20, lineLimit = Int.MAX_VALUE, collapseThresholdLines = 15),
        )
    }
}
