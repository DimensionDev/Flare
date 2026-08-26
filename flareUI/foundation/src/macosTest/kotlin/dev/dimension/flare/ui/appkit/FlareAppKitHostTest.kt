@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.appkit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.awaitAppleUi
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.Text
import platform.AppKit.NSApplication
import platform.AppKit.NSBackingStoreBuffered
import platform.AppKit.NSStackView
import platform.AppKit.NSTextField
import platform.AppKit.NSView
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowStyleMaskBorderless
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

public class FlareAppKitHostTest {
    @Test
    public fun hostRecomposesAndRecreatesContentAcrossWindowAttachment() {
        assertTrue(NSThread.isMainThread)
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 240.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val rootView = NSView(frame = window.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 240.0))
        window.contentView = rootView
        val host = FlareAppKitHost(createAppKitWidgetSystem())
        var incrementCount: (() -> Unit)? = null

        try {
            host.setContent {
                var count by remember { mutableIntStateOf(0) }
                incrementCount = { count += 1 }
                Column {
                    Text("Count $count")
                }
            }
            assertTrue(host.view.arrangedSubviews.isEmpty())

            rootView.addSubview(host.view)
            val initialColumn = host.awaitColumn()
            val initialLabel = initialColumn.arrangedSubviews[0] as NSTextField
            assertEquals("Count 0", initialLabel.stringValue)

            checkNotNull(incrementCount).invoke()
            awaitAppleUi("AppKit host did not apply the state update.") {
                initialLabel.stringValue == "Count 1"
            }

            host.view.removeFromSuperview()
            awaitAppleUi("AppKit host did not dispose content after detaching.") {
                host.view.arrangedSubviews.isEmpty()
            }

            rootView.addSubview(host.view)
            val recreatedColumn = host.awaitColumn()
            val recreatedLabel = recreatedColumn.arrangedSubviews[0] as NSTextField
            assertNotSame(initialColumn, recreatedColumn)
            assertEquals("Count 0", recreatedLabel.stringValue)
        } finally {
            host.dispose()
            window.close()
        }
    }

    private fun FlareAppKitHost.awaitColumn(): NSStackView {
        awaitAppleUi("AppKit host did not create its native hierarchy after attaching.") {
            view.arrangedSubviews.size == 1
        }
        return view.arrangedSubviews.single() as NSStackView
    }
}
