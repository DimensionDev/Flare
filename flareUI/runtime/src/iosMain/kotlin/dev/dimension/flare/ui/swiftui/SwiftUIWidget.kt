@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.swiftui

import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareBackendWidget
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareComponentType
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.testTagOrNull
import platform.Foundation.NSThread

/** Strong type token for renderer plugins which expose live nodes to SwiftUI. */
public class SwiftUIBackend internal constructor(
    internal val tree: FlareSwiftUITree,
) : FlareBackend {
    override fun toString(): String = "SwiftUIBackend"
}

/**
 * Swift-owned renderer-node plugin.
 *
 * Implementations normally live in Swift, subclass [FlareSwiftUINode], implement a generated
 * widget interface, and register the node factory through [FlareSwiftUINodeRegistrar].
 */
public interface FlareSwiftUINodePlugin {
    public fun install(registrar: FlareSwiftUINodeRegistrar)
}

/**
 * Swift-friendly registration seam for live node factories.
 *
 * Objective-C export erases the covariance of `FlareComponentType<W>`, so accepting the token as
 * [Any] keeps generated component tokens directly callable from Swift. The cast is validated and
 * contained here instead of requiring every Swift plugin to use `as!`.
 */
public class FlareSwiftUINodeRegistrar internal constructor(
    private val registrar: FlareWidgetRegistrar<SwiftUIBackend>,
) {
    public fun register(
        component: Any,
        factory: (FlareSwiftUITree) -> FlareSwiftUINode,
    ) {
        checkSwiftUIMainThread()
        require(component is FlareComponentType<*>) {
            "SwiftUI node plugins must register a FlareComponentType."
        }

        registrar.register(component) { backend ->
            factory(backend.tree)
        }
    }
}

/**
 * Swift implements this observer on its Observation model.
 *
 * Property changes identify one live node, while structural changes identify one child slot.
 * Flare does not allocate or serialize an immutable snapshot tree.
 */
public interface FlareSwiftUITreeObserver {
    public fun nodeDidChange(node: FlareSwiftUINode)

    public fun childrenDidChange(children: FlareSwiftUIChildren)
}

/**
 * Mutable node tree shared by the Compose Runtime applier and the SwiftUI host.
 *
 * Changes are deduplicated during one Compose apply transaction, then delivered synchronously at
 * the transaction boundary. The Swift host installs a proxy whose callbacks weakly reference its
 * Observation model, avoiding an ownership cycle and an extra run-loop hop.
 */
public class FlareSwiftUITree {
    internal val root: FlareSwiftUIChildren = FlareSwiftUIChildren(this)

    private var observer: FlareSwiftUITreeObserver? = null
    private val dirtyNodes = linkedSetOf<FlareSwiftUINode>()
    private val dirtyChildren = linkedSetOf<FlareSwiftUIChildren>()
    private var changeDepth: Int = 0
    private var notifying: Boolean = false
    private var disposed: Boolean = false

    internal fun setObserver(value: FlareSwiftUITreeObserver?) {
        checkSwiftUIMainThread()
        check(!disposed) { "FlareSwiftUITree is already disposed." }
        dirtyNodes.clear()
        dirtyChildren.clear()
        observer = value
        if (value != null) {
            invalidate(root)
        }
    }

    internal fun invalidate(node: FlareSwiftUINode) {
        checkSwiftUIMainThread()
        if (disposed || observer == null) return
        dirtyNodes += node
        flushChanges()
    }

    internal fun invalidate(children: FlareSwiftUIChildren) {
        checkSwiftUIMainThread()
        if (disposed || observer == null) return
        dirtyChildren += children
        flushChanges()
    }

    internal fun beginChanges() {
        checkSwiftUIMainThread()
        check(!disposed) { "FlareSwiftUITree is already disposed." }
        changeDepth += 1
    }

    internal fun endChanges() {
        checkSwiftUIMainThread()
        check(changeDepth > 0) { "FlareSwiftUITree received an unmatched endChanges call." }
        changeDepth -= 1
        flushChanges()
    }

    private fun flushChanges() {
        if (changeDepth != 0 || notifying) return
        val currentObserver = observer ?: return
        notifying = true
        try {
            while (dirtyChildren.isNotEmpty() || dirtyNodes.isNotEmpty()) {
                val changedChildren = dirtyChildren.toList()
                val changedNodes = dirtyNodes.toList()
                dirtyChildren.clear()
                dirtyNodes.clear()
                changedChildren.forEach(currentObserver::childrenDidChange)
                changedNodes.forEach(currentObserver::nodeDidChange)
            }
        } finally {
            notifying = false
        }
    }

    internal fun dispose() {
        checkSwiftUIMainThread()
        if (disposed) return
        disposed = true
        observer = null
        dirtyNodes.clear()
        dirtyChildren.clear()
    }
}

/**
 * Stable live node identity rendered by SwiftUI.
 *
 * Component properties stay on concrete subclasses. The common base only exposes modifier state
 * which every Swift renderer can apply consistently.
 */
public abstract class FlareSwiftUINode protected constructor(
    private val tree: FlareSwiftUITree,
) : AbstractFlareWidget(),
    FlareBackendWidget<SwiftUIBackend> {
    private var currentTestTag: String? = null

    public val testTag: String?
        get() = currentTestTag

    final override fun onModifierChanged(
        previous: FlareModifier,
        current: FlareModifier,
    ) {
        val value = current.testTagOrNull()
        if (currentTestTag == value) return
        currentTestTag = value
        invalidate()
    }

    protected fun invalidate() {
        tree.invalidate(this)
    }
}

/** Ordered child slot read directly by SwiftUI container renderers. */
public class FlareSwiftUIChildren(
    private val tree: FlareSwiftUITree,
) : FlareChildren {
    private val widgets = mutableListOf<FlareSwiftUINode>()

    public val nodes: List<FlareSwiftUINode>
        get() = widgets

    override fun onBeginChanges() {
        tree.beginChanges()
    }

    override fun onEndChanges() {
        tree.endChanges()
    }

    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        widgets.add(index, widget.requireSwiftUINode())
        tree.invalidate(this)
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        if (fromIndex == toIndex || count == 0) return
        val moved = widgets.subList(fromIndex, fromIndex + count).toList()
        widgets.subList(fromIndex, fromIndex + count).clear()
        val destination = if (fromIndex > toIndex) toIndex else toIndex - count
        widgets.addAll(destination, moved)
        tree.invalidate(this)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        widgets.subList(index, index + count).clear()
        tree.invalidate(this)
    }

    private fun FlareWidget.requireSwiftUINode(): FlareSwiftUINode =
        this as? FlareSwiftUINode
            ?: error("SwiftUI backend received non-SwiftUI widget $this.")
}

private fun checkSwiftUIMainThread() {
    check(NSThread.isMainThread) {
        "Flare SwiftUI nodes must be used from the Apple main thread."
    }
}
