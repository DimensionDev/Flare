package dev.dimension.flare.ui.screen.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalMediaSharedElementGroup
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.route.Route
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
class MediaSharedTransitionRenderingTest {
    @get:Rule
    val composeRule =
        createComposeRule(
            ComposeUiTestConfig(
                effectContext =
                    object : MotionDurationScale {
                        override val scaleFactor = 1f
                    },
            ),
        )
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val stack = mutableStateListOf<NavKey>(Route.Home)
    private val medias = mutableStateOf<UiState<ImmutableList<UiMedia>>>(UiState.Loading())
    private lateinit var host: MediaSharedTransitionHostState
    private lateinit var uri: String

    @Test
    fun landscapeImageMovesContinuouslyInBothDirections() = captureTransition(800, 450, false)

    @Test
    fun tallImageMovesContinuouslyInBothDirections() = captureTransition(300, 1200, false)

    @Test
    fun loadingImageMovesContinuouslyInBothDirections() = captureTransition(800, 450, true)

    @Test
    fun tallPreviewKeepsItsLayoutWhenMetadataAndOriginalArrive() = captureTransition(300, 1200, true)

    private fun captureTransition(
        width: Int,
        height: Int,
        startLoading: Boolean,
    ) {
        uri = fixture(width, height)
        val fullImage = if (startLoading) fixture(width * 2, height * 2) else uri
        val loaded: UiState<ImmutableList<UiMedia>> =
            UiState.Success(
                persistentListOf<UiMedia>(UiMedia.Image(fullImage, uri, "rendering fixture", height.toFloat(), width.toFloat(), false)),
            )
        if (!startLoading) medias.value = loaded
        setContent()
        composeRule.waitForIdle()
        // Let the actual image decoder populate Coil's memory cache before the entrance.
        composeRule.waitUntil(5_000) { frameBounds().width > 20 }
        composeRule.mainClock.autoAdvance = false
        val folder =
            File(
                instrumentation.targetContext.cacheDir,
                "shared-frames-$width-$height-$startLoading",
            ).apply { mkdirs() }
        val rows = mutableListOf<String>()
        val opening = mutableListOf<FrameBounds>()
        val closing = mutableListOf<FrameBounds>()
        try {
            composeRule.onNodeWithTag("render_source").performTouchInput { click() }
            for (index in 0 until 90) {
                if (startLoading && index == 8) composeRule.runOnUiThread { medias.value = loaded }
                composeRule.mainClock.advanceTimeByFrame()
                val bounds = frameBounds(File(folder, "open-$index.png"))
                opening += bounds
                rows += "open,$index,${composeRule.mainClock.currentTime},$bounds"
                if (index >= 20 &&
                    host.presentations
                        .single()
                        .visibility.isIdle && !host.shared.scope.isTransitionActive
                ) {
                    break
                }
            }
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            opening += frameBounds(File(folder, "open-settled.png"))
            composeRule.mainClock.autoAdvance = false
            composeRule.runOnUiThread { host.presentations.single().dismiss() }
            for (index in 0 until 90) {
                composeRule.mainClock.advanceTimeByFrame()
                val bounds = frameBounds(File(folder, "close-$index.png"))
                closing += bounds
                rows += "close,$index,${composeRule.mainClock.currentTime},$bounds"
                if (host.presentations.isEmpty()) break
            }
        } finally {
            File(
                folder,
                "bounds.csv",
            ).writeText("phase,frame,time,left,top,right,bottom,redComponents,edgeSpread\n" + rows.joinToString("\n"))
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        assertTrue("The viewer should be removed after the return transition", host.presentations.isEmpty())
        assertTrue("The image should stay visible during the transition", (opening + closing).all { it.width > 0 && it.height > 0 })
        val duplicates = (opening + closing).filter { it.components > 1 || it.edgeSpread > 12 }
        assertTrue("The frame contains overlapping or separate copies: $duplicates", duplicates.isEmpty())
        val reversals = opening.zipWithNext().filter { (a, b) -> a.width > 0 && b.width > 0 && b.width < a.width - 12 }
        assertTrue("The opening image width jumps backwards: $reversals", reversals.isEmpty())
        val jumps = closing.zipWithNext().filter { (a, b) -> a.height > 0 && b.height > a.height + 32 }
        assertTrue("The closing image suddenly grows: $jumps", jumps.isEmpty())
    }

    private fun setContent() {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        composeRule.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalGlobalAppearance provides GlobalAppearance.Default) {
                    MediaSharedTransitionHost {
                        host = checkNotNull(LocalMediaSharedTransitionHost.current)
                        NavDisplay(
                            backStack = stack,
                            onBack = { stack.removeLastOrNull() },
                            sceneStrategies = remember(host) { listOf(MediaSharedSceneStrategy(host)) },
                            entryDecorators =
                                listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                            entryProvider =
                                entryProvider {
                                    entry<Route.Home> {
                                        Box(Modifier.fillMaxSize().background(Color.White).padding(start = 32.dp, top = 160.dp)) {
                                            CompositionLocalProvider(LocalMediaSharedElementGroup provides 7L) {
                                                NetworkImage(
                                                    model = uri,
                                                    contentDescription = "source fixture",
                                                    modifier =
                                                        Modifier
                                                            .size(160.dp, 120.dp)
                                                            .clipToBounds()
                                                            .testTag("render_source")
                                                            .clickable { stack.add(Route.Media.Image(uri, uri)) },
                                                )
                                            }
                                        }
                                    }
                                    entry<Route.Media.Image>(metadata = { mapOf(MEDIA_SHARED_ROUTE to it) }) {
                                        MediaViewerScreen(
                                            medias = medias.value,
                                            initialIndex = 0,
                                            preview = uri,
                                            onDismiss = checkNotNull(LocalMediaSharedDismiss.current),
                                            toAltText = {},
                                            uriHandler = LocalUriHandler.current,
                                            fileName = MediaFileNamePolicy::rawMediaFileName,
                                            fileNames = MediaFileNamePolicy::rawMediaFileNames,
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }
    }

    private data class FrameBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val components: Int,
        val edgeSpread: Int,
    ) {
        val width get() = right - left
        val height get() = bottom - top

        override fun toString() = "$left,$top,$right,$bottom,$components,$edgeSpread"
    }

    private fun frameBounds(file: File? = null): FrameBounds {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file?.outputStream()?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val step = 4
        val width = bitmap.width / step
        val height = bitmap.height / step
        val mask = BooleanArray(width * height)
        var left = width
        var right = -1
        var top = height
        var bottom = -1
        val rowWidths = mutableListOf<Int>()
        for (y in 0 until height) {
            var rowLeft = width
            var rowRight = -1
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x * step, y * step)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                if (red > 80 && red > green * 2 && red > blue * 2) {
                    mask[y * width + x] = true
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                    rowLeft = minOf(rowLeft, x)
                    rowRight = maxOf(rowRight, x)
                }
            }
            // Exclude system-bar glyphs when checking that the image has one rectangular outline.
            if (rowRight - rowLeft > 30 && y > height * .06f && y < height * .94f) rowWidths += (rowRight - rowLeft) * step
        }
        var components = 0
        val queue = IntArray(mask.size)
        for (index in mask.indices) {
            if (!mask[index]) continue
            var start = 0
            var end = 1
            queue[0] = index
            mask[index] = false
            while (start < end) {
                val current = queue[start++]
                val x = current % width
                val y = current / width
                val neighbors =
                    intArrayOf(
                        if (x > 0) current - 1 else -1,
                        if (x + 1 < width) current + 1 else -1,
                        if (y > 0) current - width else -1,
                        if (y + 1 < height) current + width else -1,
                    )
                for (next in neighbors) {
                    if (next >= 0 && mask[next]) {
                        mask[next] = false
                        queue[end++] = next
                    }
                }
            }
            if (end > 25) components++
        }
        bitmap.recycle()
        val interior = rowWidths.drop(2).dropLast(2)
        val spread = if (interior.isEmpty()) 0 else interior.max() - interior.min()
        return FrameBounds(left * step, top * step, right * step, bottom * step, components, spread)
    }

    private fun fixture(
        width: Int,
        height: Int,
    ): String {
        val file = File(instrumentation.targetContext.cacheDir, "shared-render-$width-$height.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        Canvas(bitmap).drawRect(
            width * .4f,
            height * .5f - minOf(width, height) * .08f,
            width * .6f,
            height * .5f + minOf(width, height) * .08f,
            Paint().apply { color = android.graphics.Color.GREEN },
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }
}
