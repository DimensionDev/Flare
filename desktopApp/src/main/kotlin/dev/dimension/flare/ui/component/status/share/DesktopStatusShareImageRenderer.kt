package dev.dimension.flare.ui.component.status.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.image.BufferedImage
import javax.swing.JWindow

internal suspend fun renderDesktopStatusShareImage(content: @Composable () -> Unit): ImageBitmap =
    withContext(Dispatchers.Swing) {
        val window = JWindow()
        val panel = ComposePanel()
        val capturedImage = CompletableDeferred<ImageBitmap>()
        try {
            panel.setContent {
                val graphicsLayer = rememberGraphicsLayer()
                LaunchedEffect(graphicsLayer) {
                    delay(200)
                    withFrameNanos { }
                    withFrameNanos { }
                    runCatching {
                        graphicsLayer.toImageBitmap()
                    }.onSuccess {
                        capturedImage.complete(it)
                    }.onFailure {
                        capturedImage.completeExceptionally(it)
                    }
                }
                androidx.compose.foundation.layout.Box(
                    modifier =
                        Modifier.drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
                        },
                ) {
                    content()
                }
            }
            window.contentPane.add(panel)
            window.pack()
            window.setLocation(-20_000, -20_000)
            window.isVisible = true
            withTimeout(5_000) { capturedImage.await() }.also { image ->
                check(image.width in 1..4096 && image.height in 1..16_384) {
                    "Invalid reference image size: ${image.width}x${image.height}"
                }
            }
        } finally {
            window.isVisible = false
            window.dispose()
        }
    }

internal fun ImageBitmap.toBufferedImage(): BufferedImage {
    val awt = toAwtImage()
    return BufferedImage(awt.getWidth(null), awt.getHeight(null), BufferedImage.TYPE_INT_ARGB).also { image ->
        image.createGraphics().use { graphics ->
            graphics.drawImage(awt, 0, 0, null)
        }
    }
}

private inline fun <T : java.awt.Graphics> T.use(block: (T) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
}
