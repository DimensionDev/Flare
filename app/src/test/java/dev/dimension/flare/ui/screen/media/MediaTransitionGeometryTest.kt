package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransitionGeometryTest {
    @Test
    fun expressiveMediaSpringConvergesWithoutOvershootingInEitherDirection() {
        val motion = MediaViewerMotion(MotionScheme.expressive())
        for ((start, end) in listOf(0f to 1f, 1f to 0f, 0.36f to 0f, 0.44f to 1f)) {
            val animation = TargetBasedAnimation(motion.spatial, Float.VectorConverter, start, end)
            var previous = start
            for (millis in 0L..2_000L step 16) {
                val value = animation.getValueFromNanos(millis * 1_000_000)
                assertTrue("Progress must stay inside the current endpoints", value in minOf(start, end)..maxOf(start, end))
                assertTrue("The image must always move toward its destination", if (end > start) value >= previous else value <= previous)
                previous = value
            }
            assertEquals(end, previous, 0f)
        }
    }

    @Test
    fun expandingViewportDoesNotEnlargeTheImagePastItsFinalSize() {
        val image = Size(1600f, 900f)
        val source = Size(100f, 100f)
        val target = Size(400f, 800f)
        val initialScale = ContentScale.Crop.computeScaleFactor(image, source).scaleX
        val finalScale = ContentScale.Fit.computeScaleFactor(image, target).scaleX
        var previousScale = initialScale
        for (step in 0..100) {
            val fraction = step / 100f
            val viewport =
                Size(
                    source.width + (target.width - source.width) * fraction,
                    source.height + (target.height - source.height) * fraction,
                )
            val scale =
                InterpolatedContentScale(ContentScale.Crop, ContentScale.Fit, source, target, fraction)
                    .computeScaleFactor(image, viewport)
            assertTrue("The image shrank at progress $fraction", scale.scaleX >= previousScale - 0.0001f)
            assertTrue(
                "The image exceeded its final size at progress $fraction: ${scale.scaleX} > $finalScale",
                scale.scaleX <= finalScale + 0.0001f,
            )
            previousScale = scale.scaleX
        }
    }

    @Test
    fun cropToFitPreservesImageAspectRatioAtEveryFrame() {
        val image = Size(1600f, 900f)
        val viewport = Size(400f, 800f)
        for (step in 0..10) {
            val fraction = step / 10f
            val scale =
                InterpolatedContentScale(
                    ContentScale.Crop,
                    ContentScale.Fit,
                    viewport,
                    viewport,
                    fraction,
                ).computeScaleFactor(image, viewport)
            assertEquals(scale.scaleX, scale.scaleY, 0.0001f)
            assertEquals(800f / 900f * (1 - fraction) + 400f / 1600f * fraction, scale.scaleX, 0.0001f)
        }
    }

    @Test
    fun portraitSquareAndLongImagesStayWithinTheirEndpointScalesInEitherDirection() {
        val target = Size(400f, 800f)
        for (image in listOf(Size(900f, 1600f), Size(800f, 800f), Size(800f, 4800f))) {
            for (source in listOf(Size(100f, 100f), Size(600f, 600f))) {
                for (targetScale in listOf(ContentScale.Fit, ContentScale.FillWidth)) {
                    val initial = ContentScale.Crop.computeScaleFactor(image, source).scaleX
                    val final = targetScale.computeScaleFactor(image, target).scaleX
                    var previous = initial
                    for (step in 0..100) {
                        val fraction = step / 100f
                        val viewport =
                            Size(
                                source.width + (target.width - source.width) * fraction,
                                source.height + (target.height - source.height) * fraction,
                            )
                        val scale =
                            InterpolatedContentScale(
                                ContentScale.Crop,
                                targetScale,
                                source,
                                target,
                                fraction,
                            ).computeScaleFactor(image, viewport)
                        assertEquals(scale.scaleX, scale.scaleY, 0.0001f)
                        assertTrue(scale.scaleX >= minOf(initial, final) - 0.0001f)
                        assertTrue(scale.scaleX <= maxOf(initial, final) + 0.0001f)
                        assertTrue(if (final >= initial) scale.scaleX >= previous - 0.0001f else scale.scaleX <= previous + 0.0001f)
                        previous = scale.scaleX
                    }
                    assertEquals(final, previous, 0.0001f)
                }
            }
        }
    }

    @Test
    fun returnStartsAtDraggedImageWithinItsPane() {
        val imagePane = Rect(0f, 0f, 800f, 600f)
        val moved = draggedBounds(imagePane, Size(1200f, 600f), offsetY = 120f, scale = 0.8f)
        assertEquals(Rect(120f, 180f, 760f, 660f), moved)
    }
}
