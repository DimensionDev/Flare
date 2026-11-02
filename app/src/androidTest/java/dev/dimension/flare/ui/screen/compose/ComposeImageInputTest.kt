package dev.dimension.flare.ui.screen.compose

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeImageInputTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun menuAndKeyboardPasteConsumeEachImageOnceAndPreserveIndependentText() {
        val text = TextFieldState()
        lateinit var media: ComposeMediaState
        lateinit var context: Context
        lateinit var view: View
        compose.setContent {
            context = LocalContext.current
            view = LocalView.current
            val scope = rememberCoroutineScope()
            media =
                remember {
                    ComposeMediaState(context, { ComposeConfig.Media(4, true, 500, true) }, scope = scope)
                }
            val receiver = remember { ComposeImageReceiver(context, media) }
            // A consumed image must not be imported again by an ancestor receiver.
            Box(Modifier.contentReceiver(receiver)) {
                BasicTextField(text, Modifier.testTag("body").contentReceiver(receiver))
            }
        }
        val gif = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)
        val file = File(context.cacheDir, "clipboard.bin").apply { writeBytes(gif) }
        compose.onNodeWithTag("body").performClick()
        compose.runOnIdle {
            val clip =
                ClipData(
                    "image and text",
                    arrayOf("image/gif", "text/plain"),
                    ClipData.Item(
                        "https://example.com/alternative.gif",
                        null,
                        FileProvider.getUriForFile(context, "${context.packageName}.provider", file),
                    ),
                )
            clip.addItem(ClipData.Item("independent text"))
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
        compose.onNodeWithTag("body").performSemanticsAction(SemanticsActions.PasteText) { it() }
        compose.waitUntil(5_000) { media.medias.size == 1 }
        compose.runOnIdle {
            assertEquals("independent text", text.text.toString())
            assertArrayEquals(gif, File(checkNotNull(media.medias[0].uri.path)).readBytes())
            assertTrue(
                media.medias[0]
                    .uri
                    .toString()
                    .endsWith(".gif"),
            )
            assertEquals(
                dev.dimension.flare.common.FileType.Image,
                dev.dimension.flare.common
                    .FileItem(context, media.medias[0].uri)
                    .type,
            )
        }
        compose.onNodeWithTag("body").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.V)
            keyUp(Key.CtrlLeft)
        }
        compose.waitUntil(5_000) { media.medias.size == 2 }
        compose.runOnIdle {
            assertEquals("independent textindependent text", text.text.toString())
            val connection = checkNotNull(view.onCreateInputConnection(EditorInfo()))
            assertTrue(
                connection.commitContent(
                    InputContentInfo(
                        FileProvider.getUriForFile(context, "${context.packageName}.provider", file),
                        ClipDescription("keyboard image", arrayOf("image/gif")),
                        Uri.parse("https://example.com/alternative.gif"),
                    ),
                    InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                    Bundle(),
                ),
            )
        }
        compose.waitUntil(5_000) { media.medias.size == 3 }
        compose.runOnIdle {
            assertEquals("independent textindependent text", text.text.toString())
            media.close()
            file.delete()
        }
    }

    @Test
    fun nativeDropIntoAttachmentsAndBodyImportsOnceAndKeepsIndependentText() {
        val text = TextFieldState("before after", TextRange(7))
        lateinit var clip: ClipData
        lateinit var media: ComposeMediaState
        lateinit var view: View
        lateinit var file: File
        compose.setContent {
            val context = LocalContext.current
            view = LocalView.current
            val scope = rememberCoroutineScope()
            clip =
                remember {
                    file =
                        File(context.cacheDir, "drop.gif").apply {
                            writeBytes(Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT))
                        }
                    ClipData(
                        "mixed drop",
                        arrayOf("image/gif", "text/plain"),
                        ClipData.Item(
                            "alternative URL",
                            null,
                            FileProvider.getUriForFile(context, "${context.packageName}.provider", file),
                        ),
                    ).apply {
                        addItem(ClipData.Item("independent"))
                    }
                }
            media = remember { ComposeMediaState(context, { ComposeConfig.Media(4, true, 500, true) }, scope = scope) }
            val bodyReceiver = remember { ComposeImageReceiver(context, media) }
            val attachmentReceiver =
                remember {
                    ComposeImageReceiver(context, media) { value ->
                        text.edit { replace(selection.min, selection.max, value) }
                    }
                }
            Column(Modifier.padding(top = 100.dp)) {
                Box(Modifier.testTag("source").size(100.dp))
                Box(Modifier.testTag("attachments").size(100.dp).contentReceiver(attachmentReceiver))
                BasicTextField(text, Modifier.testTag("body").size(300.dp, 100.dp).contentReceiver(bodyReceiver))
            }
        }
        val origin = IntArray(2)
        compose.runOnIdle { view.getLocationOnScreen(origin) }

        fun location(tag: String): Offset =
            compose
                .onNodeWithTag(tag)
                .fetchSemanticsNode()
                .boundsInRoot.center + Offset(origin[0].toFloat(), origin[1].toFloat())
        val startDrag = {
            val shadow =
                object : View.DragShadowBuilder(view) {
                    override fun onProvideShadowMetrics(
                        size: android.graphics.Point,
                        touch: android.graphics.Point,
                    ) {
                        size.set(20, 20)
                        touch.set(10, 10)
                    }

                    override fun onDrawShadow(canvas: android.graphics.Canvas) = Unit
                }
            assertTrue(view.startDragAndDrop(clip, shadow, null, 0))
        }
        drag(location("source"), location("attachments"), startDrag)
        compose.waitUntil(5_000) { media.medias.size == 1 }
        compose.runOnIdle { assertEquals("before independentafter", text.text.toString()) }
        drag(location("source"), location("body"), startDrag)
        compose.waitUntil(5_000) { media.medias.size == 2 }
        compose.runOnIdle {
            assertEquals(2, Regex("independent").findAll(text.text).count())
            assertTrue(!text.text.contains("alternative"))
            media.close()
            file.delete()
        }
    }

    private fun drag(
        from: Offset,
        to: Offset,
        startDrag: () -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val start = SystemClock.uptimeMillis()

        fun event(
            action: Int,
            position: Offset,
        ) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, position.x, position.y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        event(MotionEvent.ACTION_DOWN, from)
        try {
            compose.runOnIdle(startDrag)
            SystemClock.sleep(100)
            for (step in 1..10) {
                event(MotionEvent.ACTION_MOVE, from + (to - from) * (step / 10f))
                SystemClock.sleep(30)
            }
        } finally {
            event(MotionEvent.ACTION_UP, to)
        }
    }
}
