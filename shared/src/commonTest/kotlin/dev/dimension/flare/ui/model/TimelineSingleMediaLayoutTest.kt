package dev.dimension.flare.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineSingleMediaLayoutTest {
    @Test
    fun mediaFitsBothTheContentWidthAndViewportHeight() {
        val maximumHeight = TimelineSingleMediaLayout.maximumHeight(800f)
        assertEquals(400f, maximumHeight)
        for ((ratio, expectedWidth) in listOf(16f / 9f to 360f, 1f to 360f, 9f / 16f to 225f)) {
            val width = TimelineSingleMediaLayout.width(360f, ratio, maximumHeight)
            assertEquals(expectedWidth, width, 0.001f)
            assertEquals(minOf(360f / ratio, 400f), width / ratio, 0.001f)
        }
        assertEquals(420f, TimelineSingleMediaLayout.maximumHeight(1200f))
    }

    @Test
    fun onlyTallImagePreviewsCropWhileVideoKeepsItsRatio() {
        val imageRatio = TimelineSingleMediaLayout.aspectRatio(1f / 5f, cropTallMedia = true)
        assertEquals(9f / 21f, imageRatio)
        assertEquals(9f / 16f, TimelineSingleMediaLayout.aspectRatio(9f / 16f, cropTallMedia = true))
        val videoRatio = TimelineSingleMediaLayout.aspectRatio(1f / 5f, cropTallMedia = false)
        assertEquals(1f / 5f, videoRatio)
        assertEquals(80f, TimelineSingleMediaLayout.width(360f, videoRatio, 400f), 0.001f)
    }

    @Test
    fun missingDimensionsHaveBoundedFallbacksAndEmptyConstraintsStayEmpty() {
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(420f, TimelineSingleMediaLayout.maximumHeight(invalid))
            assertEquals(1f, TimelineSingleMediaLayout.aspectRatio(invalid, cropTallMedia = true))
            assertEquals(0f, TimelineSingleMediaLayout.width(invalid, 1f, 400f))
            assertEquals(0f, TimelineSingleMediaLayout.width(360f, 1f, invalid))
        }
    }
}
