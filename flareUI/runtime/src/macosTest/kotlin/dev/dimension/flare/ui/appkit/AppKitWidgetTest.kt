@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.FlareModifier
import kotlinx.cinterop.useContents
import platform.AppKit.NSButton
import platform.AppKit.NSStackView
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.CoreGraphics.CGRectMake
import platform.Foundation.valueForKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies the AppKit widget adapter independently of the demo application. */
public class AppKitWidgetTest {
    @Test
    public fun testTagSetsAndClearsAccessibilityIdentifierOnButton() {
        val button = NSButton()
        val widget = TestButtonWidget(button)

        widget.updateModifier(FlareModifier(testTag = "demo-button"))
        assertEquals(
            expected = "demo-button",
            actual = button.valueForKey(ACCESSIBILITY_IDENTIFIER_KEY),
        )

        widget.updateModifier(FlareModifier.None)
        assertNull(button.valueForKey(ACCESSIBILITY_IDENTIFIER_KEY))
    }

    @Test
    public fun hierarchyOperationsApplyDirectly() {
        val stack = NSStackView()
        val children = AppKitChildren(stack)
        val first = TestButtonWidget(NSButton())
        val second = TestButtonWidget(NSButton())

        children.insert(0, first)
        children.insert(1, second)

        assertEquals(2, stack.arrangedSubviews.size)
        assertEquals(first.view, stack.arrangedSubviews[0])
        assertEquals(second.view, stack.arrangedSubviews[1])

        children.move(fromIndex = 0, toIndex = 2, count = 1)
        assertEquals(second.view, stack.arrangedSubviews[0])
        assertEquals(first.view, stack.arrangedSubviews[1])

        children.remove(index = 0, count = 1)
        assertEquals(listOf(first.view), stack.arrangedSubviews)
    }

    @Test
    public fun fixedAndFillSizesBecomeNativeConstraints() {
        val stack =
            NSStackView(frame = CGRectMake(0.0, 0.0, 200.0, 100.0)).apply {
                orientation = NSUserInterfaceLayoutOrientationVertical
            }
        val widget = TestButtonWidget(NSButton())
        widget.updateModifier(FlareModifier.None.fillMaxWidth().height(32f))

        AppKitChildren(stack).insert(0, widget)
        stack.layoutSubtreeIfNeeded()

        widget.view.frame.useContents {
            assertEquals(200.0, size.width, absoluteTolerance = 0.5)
            assertEquals(32.0, size.height, absoluteTolerance = 0.5)
        }
    }

    private class TestButtonWidget(
        view: NSButton,
    ) : AbstractAppKitWidget<NSButton>(
            view = view,
        )

    private companion object {
        const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
    }
}
