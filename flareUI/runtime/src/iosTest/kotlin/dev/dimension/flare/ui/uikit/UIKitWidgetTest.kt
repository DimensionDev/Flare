@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareModifier
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.valueForKey
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    public fun fixedAndFillSizesBecomeNativeConstraints() {
        val stack =
            UIStackView(frame = CGRectMake(0.0, 0.0, 200.0, 100.0)).apply {
                axis = UILayoutConstraintAxisVertical
                alignment = UIStackViewAlignmentFill
            }
        val widget = TestButtonWidget(UIButton.buttonWithType(UIButtonTypeSystem))
        widget.updateModifier(FlareModifier.None.fillMaxWidth().height(32f))

        UIKitChildren(stack).insert(0, widget)
        stack.layoutIfNeeded()

        widget.view.frame.useContents {
            assertEquals(200.0, size.width, absoluteTolerance = 0.5)
        }
        assertTrue(
            widget.view.constraints.filterIsInstance<NSLayoutConstraint>().any { constraint ->
                constraint.active && constraint.constant == 32.0
            },
            "The fixed height must be represented by an active native constraint.",
        )
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
