package dev.dimension.flare.ui.humanizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HumanizerTest {
    @Test
    fun percentage_rounds_to_two_decimals() {
        assertEquals("50.0%", 0.5f.humanizePercentage())
    }

    @Test
    fun duration_formats_video_style_string() {
        assertEquals("01:02:03", (1.hours + 2.minutes + 3.seconds).humanize())
    }
}
