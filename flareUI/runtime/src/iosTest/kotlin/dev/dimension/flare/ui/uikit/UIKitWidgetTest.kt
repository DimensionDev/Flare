@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareModifier
import platform.Foundation.valueForKey
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIStackView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies the UIKit widget bridge independently of the demo application. */
public class UIKitWidgetTest {
    @Test
    public fun testTagSetsAndClearsAccessibilityIdentifierOnButton() {
        val button = UIButton.buttonWithType(UIButtonTypeSystem)
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
        val stack = UIStackView()
        val children = UIKitChildren(stack)
        val first = TestButtonWidget(UIButton.buttonWithType(UIButtonTypeSystem))
        val second = TestButtonWidget(UIButton.buttonWithType(UIButtonTypeSystem))

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

    private class TestButtonWidget(
        view: UIButton,
    ) : AbstractUIKitWidget<UIButton>(
            view = view,
        )

    private companion object {
        const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
    }
}
