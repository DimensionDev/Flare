package dev.dimension.flare.ui.screen.media

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.ui.model.UiMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SdkSuppress(minSdkVersion = 31)
class ImageTranslationLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun translationBlurAnimatesReversesAndRestoresTheOriginalImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("translation-loading", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = file.toURI().toString()
        val media = UiMedia.Image(uri, uri, "Image fixture", 64f, 64f, false)
        var translating by mutableStateOf(false)
        try {
            compose.setContent {
                MaterialExpressiveTheme {
                    Box(Modifier.size(300.dp, 200.dp).background(Color.Black).testTag("viewer")) {
                        ImageItem(media, "Image fixture", {}, {}, {}, Modifier.fillMaxSize(), translating = translating)
                    }
                }
            }
            compose.waitUntil(5_000) {
                val pixels = compose.onNodeWithTag("viewer").captureToImage().toPixelMap()
                pixels[pixels.width / 2, pixels.height / 4].red > 0.95f
            }

            fun softenedPixels(name: String): Int {
                val image = compose.onNodeWithTag("viewer").captureToImage()
                File(context.externalCacheDir, "image-translation-$name.png").outputStream().use {
                    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val pixels = image.toPixelMap()
                return (0 until pixels.width).count { pixels[it, pixels.height / 4].red in 0.1f..0.9f }
            }

            val original = softenedPixels("original")
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { translating = true }
            compose.mainClock.advanceTimeBy(48)
            val entering = softenedPixels("entering")
            compose.mainClock.advanceTimeBy(1_000)
            val loading = softenedPixels("loading")
            assertTrue("The image must blur progressively", entering > original && entering < loading)
            assertTrue("The loaded image must visibly blur", loading > original + 8)
            compose.onNodeWithContentDescription("Translating image").assertExists()
            assertEquals(1, compose.onAllNodesWithContentDescription("Translating image").fetchSemanticsNodes().size)

            compose.runOnIdle { translating = false }
            compose.mainClock.advanceTimeBy(48)
            val leaving = softenedPixels("leaving")
            assertTrue("Finishing must gradually restore clarity", leaving > original && leaving < loading)
            compose.runOnIdle { translating = true }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithContentDescription("Translating image").assertExists()
            compose.runOnIdle { translating = false }
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(original, softenedPixels("restored"))
            compose.onNodeWithContentDescription("Translating image").assertDoesNotExist()
        } finally {
            file.delete()
        }
    }
}
