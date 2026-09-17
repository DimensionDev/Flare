package dev.dimension.flare.ui.screen.media

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTransitionGeometryTest {
    @Test
    fun cropToFitPreservesImageAspectRatioAtEveryFrame() {
        val image = Size(1600f, 900f)
        val viewport = Size(400f, 800f)
        for (step in 0..10) {
            val fraction = step / 10f
            val scale = InterpolatedContentScale(ContentScale.Crop, ContentScale.Fit, fraction).computeScaleFactor(image, viewport)
            assertEquals(scale.scaleX, scale.scaleY, 0.0001f)
            assertEquals(800f / 900f * (1 - fraction) + 400f / 1600f * fraction, scale.scaleX, 0.0001f)
        }
    }

    @Test
    fun returnStartsAtDraggedImageWithinItsPane() {
        val imagePane = Rect(0f, 0f, 800f, 600f)
        val moved = draggedBounds(imagePane, Size(1200f, 600f), offsetY = 120f, scale = 0.8f)
        assertEquals(Rect(120f, 180f, 760f, 660f), moved)
    }
}
