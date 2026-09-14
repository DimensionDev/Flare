@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.appkit

import dev.dimension.compose.nativekit.AbstractNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitBackend
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitSize
import dev.dimension.compose.nativekit.NativeKitWidget
import platform.AppKit.NSLayoutConstraint
import platform.AppKit.NSStackView
import platform.AppKit.NSView
import platform.AppKit.heightAnchor
import platform.AppKit.translatesAutoresizingMaskIntoConstraints
import platform.AppKit.widthAnchor
import platform.Foundation.setValue

/** Strong type token for AppKit renderer plugins. */
public data object AppKitBackend : NativeKitBackend

/** Renderer contract implemented by AppKit-backed primitive plugins. */
public interface AppKitNativeWidget : NativeKitWidget {
    public val view: NSView
}

public abstract class AbstractAppKitWidget<V : NSView>(
    final override val view: V,
) : AbstractNativeKitWidget(),
    AppKitNativeWidget {
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

public class AppKitChildren(
    private val parent: NSStackView,
) : NativeKitChildren {
    override fun insert(
        index: Int,
        widget: NativeKitWidget,
    ) {
        val child = widget.requireAppKitWidget().view
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.insertArrangedSubview(
            view = child,
            atIndex = index.toLong(),
        )
        (widget as? AbstractAppKitWidget<*>)?.refreshSizingConstraints()
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        if (fromIndex == toIndex || count == 0) return
        val moved =
            List(count) { offset ->
                parent.arrangedSubviews[fromIndex + offset] as NSView
            }
        moved.forEach(parent::removeArrangedSubview)
        val destination = if (fromIndex > toIndex) toIndex else toIndex - count
        moved.forEachIndexed { offset, child ->
            parent.insertArrangedSubview(child, atIndex = (destination + offset).toLong())
        }
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        repeat(count) {
            removeChild(parent.arrangedSubviews[index] as NSView)
        }
    }

    private fun removeChild(view: NSView) {
        parent.removeArrangedSubview(view)
        view.removeFromSuperview()
    }

    private fun NativeKitWidget.requireAppKitWidget(): AppKitNativeWidget =
        this as? AppKitNativeWidget
            ?: error("AppKit backend received non-AppKit widget $this.")
}

private fun NativeKitSize.toConstraint(
    view: NSView,
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
