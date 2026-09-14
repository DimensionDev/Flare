@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.uikit

import dev.dimension.compose.nativekit.AbstractNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitBackend
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitSize
import dev.dimension.compose.nativekit.NativeKitWidget
import platform.Foundation.setValue
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIStackView
import platform.UIKit.UIView

/** Strong type token for UIKit renderer plugins. */
public data object UIKitBackend : NativeKitBackend

/** Renderer contract implemented by UIKit-backed primitive plugins. */
public interface UIKitNativeWidget : NativeKitWidget {
    public val view: UIView
}

public abstract class AbstractUIKitWidget<V : UIView>(
    final override val view: V,
) : AbstractNativeKitWidget(),
    UIKitNativeWidget {
    private var widthConstraint: NSLayoutConstraint? = null
    private var heightConstraint: NSLayoutConstraint? = null

    override fun onModifierChanged(
        previous: NativeKitModifier,
        current: NativeKitModifier,
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
) : NativeKitChildren {
    override fun insert(
        index: Int,
        widget: NativeKitWidget,
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

    private fun NativeKitWidget.requireUIKitWidget(): UIKitNativeWidget =
        this as? UIKitNativeWidget
            ?: error("UIKit backend received non-UIKit widget $this.")
}

private fun NativeKitSize.toConstraint(
    view: UIView,
    isWidth: Boolean,
): NSLayoutConstraint? =
    when (this) {
        NativeKitSize.Wrap -> {
            null
        }

        NativeKitSize.Fill -> {
            val parent = view.superview ?: return null
            if (isWidth) {
                view.widthAnchor.constraintEqualToAnchor(parent.widthAnchor)
            } else {
                view.heightAnchor.constraintEqualToAnchor(parent.heightAnchor)
            }
        }

        is NativeKitSize.Fixed -> {
            if (isWidth) {
                view.widthAnchor.constraintEqualToConstant(value.toDouble())
            } else {
                view.heightAnchor.constraintEqualToConstant(value.toDouble())
            }
        }
    }

private const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
