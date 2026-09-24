package dev.dimension.flare.ui.component.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusContentLineLimitTest {
    @Test
    fun collapsesOnlyAfterTenVisualLines() {
        assertFalse(shouldCollapseRichText(fullHeight = 200, lineHeight = 20, collapseThresholdLines = 10))
        assertTrue(shouldCollapseRichText(fullHeight = 201, lineHeight = 20, collapseThresholdLines = 10))
        assertEquals(
            100,
            collapsedRichTextHeight(
                fullHeight = 201,
                lineHeight = 20,
                lineLimit = 5,
                collapseThresholdLines = 10,
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
        assertEquals(240, collapsedRichTextHeight(fullHeight = 240, lineHeight = 20, lineLimit = 15, collapseThresholdLines = 10))
        assertEquals(300, collapsedRichTextHeight(fullHeight = 320, lineHeight = 20, lineLimit = 15, collapseThresholdLines = 10))
    }

    @Test
    fun veryLargeLineLimitsDoNotOverflow() {
        assertEquals(
            240,
            collapsedRichTextHeight(fullHeight = 240, lineHeight = 20, lineLimit = Int.MAX_VALUE, collapseThresholdLines = 10),
        )
    }
}
