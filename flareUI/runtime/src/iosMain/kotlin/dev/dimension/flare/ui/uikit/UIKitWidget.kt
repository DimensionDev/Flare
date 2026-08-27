@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareSize
import dev.dimension.flare.ui.FlareWidget
import platform.Foundation.setValue
import platform.UIKit.NSLayoutConstraint
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
    private var widthConstraint: NSLayoutConstraint? = null
    private var heightConstraint: NSLayoutConstraint? = null

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
        if (previous.width != current.width || previous.height != current.height) {
            refreshSizingConstraints()
        }
    }

    internal fun refreshSizingConstraints() {
        NSLayoutConstraint.deactivateConstraints(listOfNotNull(widthConstraint, heightConstraint))
        widthConstraint = modifier.width.toConstraint(view, isWidth = true)
        heightConstraint = modifier.height.toConstraint(view, isWidth = false)
        NSLayoutConstraint.activateConstraints(listOfNotNull(widthConstraint, heightConstraint))
    }
}

public class UIKitChildren(
    private val parent: UIStackView,
) : FlareChildren {
    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        val child = widget.requireUIKitWidget().view
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.insertArrangedSubview(
            view = child,
            atIndex = index.toULong(),
        )
        (widget as? AbstractUIKitWidget<*>)?.refreshSizingConstraints()
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
        moved.forEach(parent::removeArrangedSubview)
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

private fun FlareSize.toConstraint(
    view: UIView,
    isWidth: Boolean,
): NSLayoutConstraint? =
    when (this) {
        FlareSize.Wrap -> {
            null
        }

        FlareSize.Fill -> {
            val parent = view.superview ?: return null
            if (isWidth) {
                view.widthAnchor.constraintEqualToAnchor(parent.widthAnchor)
            } else {
                view.heightAnchor.constraintEqualToAnchor(parent.heightAnchor)
            }
        }

        is FlareSize.Fixed -> {
            if (isWidth) {
                view.widthAnchor.constraintEqualToConstant(value.toDouble())
            } else {
                view.heightAnchor.constraintEqualToConstant(value.toDouble())
            }
        }
    }

private const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
