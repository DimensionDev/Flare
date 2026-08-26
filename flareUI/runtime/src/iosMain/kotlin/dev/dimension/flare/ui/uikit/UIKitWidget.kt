@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareWidget
import platform.Foundation.setValue
import platform.UIKit.UIStackView
import platform.UIKit.UIView

/** Strong type token for UIKit renderer plugins. */
public data object UIKitBackend : FlareBackend

/** Renderer contract implemented by UIKit-backed primitive plugins. */
public interface UIKitNativeWidget : FlareWidget {
    public val view: UIView
}

public abstract class AbstractUIKitWidget<V : UIView>(
    final override val view: V,
) : AbstractFlareWidget(),
    UIKitNativeWidget {
    override fun onModifierChanged(
        previous: FlareModifier,
        current: FlareModifier,
    ) {
        val previousTestTag = previous.testTag
        val currentTestTag = current.testTag
        if (previousTestTag != currentTestTag) {
            view.setValue(
                value = currentTestTag,
                forKey = ACCESSIBILITY_IDENTIFIER_KEY,
            )
        }
    }
}

public class UIKitChildren(
    private val parent: UIStackView,
) : FlareChildren {
    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        parent.insertArrangedSubview(
            view = widget.requireUIKitWidget().view,
            atIndex = index.toULong(),
        )
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        if (fromIndex == toIndex || count == 0) return
        val moved =
            List(count) { offset ->
                parent.arrangedSubviews[fromIndex + offset] as UIView
            }
        moved.forEach(::removeChild)
        val destination = if (fromIndex > toIndex) toIndex else toIndex - count
        moved.forEachIndexed { offset, child ->
            parent.insertArrangedSubview(child, atIndex = (destination + offset).toULong())
        }
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        repeat(count) {
            removeChild(parent.arrangedSubviews[index] as UIView)
        }
    }

    private fun removeChild(view: UIView) {
        parent.removeArrangedSubview(view)
        view.removeFromSuperview()
    }

    private fun FlareWidget.requireUIKitWidget(): UIKitNativeWidget =
        this as? UIKitNativeWidget
            ?: error("UIKit backend received non-UIKit widget $this.")
}

private const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
