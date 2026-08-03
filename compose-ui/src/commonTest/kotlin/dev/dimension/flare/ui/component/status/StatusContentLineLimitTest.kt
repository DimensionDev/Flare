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
}
