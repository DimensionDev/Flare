@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareWidget
import platform.AppKit.NSStackView
import platform.AppKit.NSView
import platform.Foundation.setValue

/** Strong type token for AppKit renderer plugins. */
public data object AppKitBackend : FlareBackend

/** Renderer contract implemented by AppKit-backed primitive plugins. */
public interface AppKitNativeWidget : FlareWidget {
    public val view: NSView
}

public abstract class AbstractAppKitWidget<V : NSView>(
    final override val view: V,
) : AbstractFlareWidget(),
    AppKitNativeWidget {
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

public class AppKitChildren(
    private val parent: NSStackView,
) : FlareChildren {
    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        parent.insertArrangedSubview(
            view = widget.requireAppKitWidget().view,
            atIndex = index.toLong(),
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
                parent.arrangedSubviews[fromIndex + offset] as NSView
            }
        moved.forEach(::removeChild)
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

    private fun FlareWidget.requireAppKitWidget(): AppKitNativeWidget =
        this as? AppKitNativeWidget
            ?: error("AppKit backend received non-AppKit widget $this.")
}

private const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
