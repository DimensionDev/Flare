package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.model.VideoAutoplay
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineAppearanceTest {
    @Test
    fun sharePreviewDefaultsOnlyOverrideRenderSpecificValues() {
        val appearance =
            TimelineAppearance(
                expandContentWarning = false,
                videoAutoplay = VideoAutoplay.ALWAYS,
                lineLimit = 42,
                showTranslateButton = true,
            )

        assertEquals(
            appearance.copy(
                expandContentWarning = true,
                videoAutoplay = VideoAutoplay.NEVER,
                showTranslateButton = false,
            ),
            appearance.withSharePreviewDefaults(),
        )
    }
}
