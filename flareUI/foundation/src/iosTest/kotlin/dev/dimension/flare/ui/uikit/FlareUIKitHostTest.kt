@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.awaitAppleUi
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.Text
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import platform.UIKit.UILabel
import platform.UIKit.UIStackView
import platform.UIKit.UIWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

public class FlareUIKitHostTest {
    @Test
    public fun hostUsesLatestContentAcrossWindowAttachment() {
        assertTrue(NSThread.isMainThread)
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 240.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem())

        try {
            host.setContent {
                Column {
                    Text("Initial")
                }
            }
            assertTrue(host.view.arrangedSubviews.isEmpty())

            window.addSubview(host.view)
            val initialColumn = host.awaitColumn()
            val initialLabel = initialColumn.arrangedSubviews[0] as UILabel
            assertEquals("Initial", initialLabel.text)

            host.view.removeFromSuperview()
            awaitAppleUi("UIKit host did not dispose content after detaching.") {
                host.view.arrangedSubviews.isEmpty()
            }
            host.setContent {
                Column {
                    Text("Updated while detached")
                }
            }

            window.addSubview(host.view)
            val recreatedColumn = host.awaitColumn()
            val recreatedLabel = recreatedColumn.arrangedSubviews[0] as UILabel
            assertNotSame(initialColumn, recreatedColumn)
            assertEquals("Updated while detached", recreatedLabel.text)
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    private fun FlareUIKitHost.awaitColumn(): UIStackView {
        awaitAppleUi("UIKit host did not create its native hierarchy after attaching.") {
            view.arrangedSubviews.size == 1
        }
        return view.arrangedSubviews.single() as UIStackView
    }
}
