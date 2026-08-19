package dev.dimension.flare.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineCarouselLayoutTest {
    @Test
    fun twoSquareItemsFillTheContentWidth() {
        val spec = TimelineCarouselLayout.spec(2, 1f, 1f)

        assertEquals(0.5f, spec.widthMultiplier)
        assertEquals(-0.5f, spec.spacingMultiplier)
        assertEquals(281f, spec.height(566f, 0f, 4f))
    }

    @Test
    fun twoPortraitItemsFillTheContentWidth() {
        val spec = TimelineCarouselLayout.spec(2, 0.75f, 0.75f)

        assertEquals(374.66666f, spec.height(566f, 0f, 4f), absoluteTolerance = 0.001f)
    }

    @Test
    fun threeItemsShowPartOfTheSecondItem() {
        val spec = TimelineCarouselLayout.spec(3, 0.75f, 0.75f)

        assertEquals(448.7026f, spec.height(566f, 0f, 4f), absoluteTolerance = 0.001f)
    }

    @Test
    fun fourPortraitItemsUseOnlyTheLeadingPairForHeight() {
        val spec = TimelineCarouselLayout.spec(4, 2f / 3f, 2f / 3f)

        assertEquals(504.7904f, spec.height(566f, 0f, 4f), absoluteTolerance = 0.001f)
    }

    @Test
    fun leadingLandscapeItemsUseWideFallback() {
        val spec = TimelineCarouselLayout.spec(3, 1.5f, 1.5f)

        assertEquals(0.68f, spec.widthMultiplier)
        assertEquals(0f, spec.spacingMultiplier)
        assertEquals(384.88f, spec.height(566f, 0f, 4f), absoluteTolerance = 0.001f)
        assertEquals(
            452.8f,
            spec.itemWidth(contentWidth = 566f, height = 384.88f, aspectRatio = 1.5f),
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun invalidRatiosKeepThePreviousDefaultHeight() {
        val spec = TimelineCarouselLayout.spec(2, 0f, Float.NaN)

        assertEquals(10f / 16f, spec.widthMultiplier)
        assertEquals(0f, spec.spacingMultiplier)
    }

    @Test
    fun horizontalInsetsDoNotIncreaseContentHeight() {
        val spec = TimelineCarouselLayout.spec(2, 1f, 1f)

        assertEquals(
            281f,
            spec.height(viewportWidth = 598f, horizontalInsets = 32f, itemSpacing = 4f),
        )
    }
}
