package dev.dimension.flare.ui.component.status

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusContentLineLimitTest {
    @Test
    fun explicitLineLimitWinsOverDefaultExpansion() {
        assertEquals(
            3,
            resolveStatusContentMaxLines(
                explicitMaxLines = 3,
                defaultMaxLines = 5,
                shouldExpandTextByDefault = true,
            ),
        )
    }
}
