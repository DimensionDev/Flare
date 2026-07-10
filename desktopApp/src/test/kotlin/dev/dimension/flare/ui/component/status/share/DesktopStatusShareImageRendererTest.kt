package dev.dimension.flare.ui.component.status.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopStatusShareImageRendererTest {
    @Test
    fun rendersComposePixelsInsteadOfSwingBackground() {
        if (GraphicsEnvironment.isHeadless()) return

        val image =
            runBlocking {
                renderDesktopStatusShareImage {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .background(Color.Red),
                    )
                }
            }.toBufferedImage()

        assertTrue(image.width in 1..256)
        assertTrue(image.height in 1..256)
        val center = image.getRGB(image.width / 2, image.height / 2)
        val red = center shr 16 and 0xFF
        val green = center shr 8 and 0xFF
        val blue = center and 0xFF
        assertTrue(red > 200 && green < 50 && blue < 50)
    }
}
