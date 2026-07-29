@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareBackendWidget
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.testTagOrNull
import platform.Foundation.setValue
import platform.UIKit.UIStackView
import platform.UIKit.UIView

/** Strong type token for UIKit renderer plugins. */
public class UIKitBackend : FlareBackend {
    internal val mutations: UIKitMutationCoordinator = UIKitMutationCoordinator()

    override fun toString(): String = "UIKitBackend"
}

/** Renderer contract implemented by UIKit-backed primitive plugins. */
public interface UIKitNativeWidget : FlareBackendWidget<UIKitBackend> {
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
        val previousTestTag = previous.testTagOrNull()
        val currentTestTag = current.testTagOrNull()
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
    backend: UIKitBackend,
) : FlareChildren {
    private val mutations = backend.mutations
    private val views: MutableList<UIView> =
        parent.arrangedSubviews.map { view -> view as UIView }.toMutableList()

    override fun onBeginChanges() {
        mutations.beginChanges()
    }

    override fun onEndChanges() {
        mutations.endChanges()
    }

    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        val child = widget.requireUIKitWidget().view
        views.add(index, child)
        mutations.invalidate(this)
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        if (fromIndex == toIndex || count == 0) return
        val moved = views.subList(fromIndex, fromIndex + count).toList()
        views.subList(fromIndex, fromIndex + count).clear()
        val destination = if (fromIndex > toIndex) toIndex else toIndex - count
        views.addAll(destination, moved)
        mutations.invalidate(this)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        views.subList(index, index + count).clear()
        mutations.invalidate(this)
    }

    internal fun applyHierarchy() {
        parent.arrangedSubviews
            .map { view -> view as UIView }
            .filterNot { current -> views.any { desired -> desired === current } }
            .forEach(::removeChild)

        views.forEachIndexed { index, child ->
            val current = parent.arrangedSubviews.getOrNull(index) as? UIView
            if (current === child) return@forEachIndexed

            if (parent.arrangedSubviews.any { view -> view === child }) {
                removeChild(child)
            }
            parent.insertArrangedSubview(child, atIndex = index.toULong())
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

internal class UIKitMutationCoordinator {
    private var changeDepth: Int = 0
    private val dirtyChildren = linkedSetOf<UIKitChildren>()

    fun beginChanges() {
        changeDepth += 1
    }

    fun endChanges() {
        check(changeDepth > 0) { "UIKit mutations received an unmatched endChanges call." }
        changeDepth -= 1
        if (changeDepth == 0) {
            flush()
        }
    }

    fun invalidate(children: UIKitChildren) {
        dirtyChildren += children
        if (changeDepth == 0) {
            flush()
        }
    }

    private fun flush() {
        while (dirtyChildren.isNotEmpty()) {
            val pending = dirtyChildren.toList()
            dirtyChildren.clear()
            pending.forEach(UIKitChildren::applyHierarchy)
        }
    }
}

private const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
