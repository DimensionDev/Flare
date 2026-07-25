@file:Suppress("ktlint:standard:annotation")

package dev.dimension.flare.flareui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableTargetMarker
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

@Retention(AnnotationRetention.BINARY)
@ComposableTargetMarker(description = "Flare UI")
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
)
public annotation class FlareUiComposable

public typealias FlareUiContent = @Composable @FlareUiComposable () -> Unit

/**
 * A stable, typed identifier for one host widget.
 *
 * Adding a widget creates another [WidgetType]; it does not add a method to a central interface.
 */
public abstract class WidgetType<P : Any>(
    public val debugName: String,
)

/**
 * A node managed by [FlareUiApplier]. Native backends can wrap their real platform widget here.
 */
public abstract class WidgetNode(
    public val type: WidgetType<*>?,
) {
    public open fun insert(
        index: Int,
        child: WidgetNode,
    ): Unit = unsupportedChildren()

    public open fun move(
        from: Int,
        to: Int,
        count: Int,
    ): Unit = unsupportedChildren()

    public open fun remove(
        index: Int,
        count: Int,
    ): Unit = unsupportedChildren()

    public open fun clear(): Unit = unsupportedChildren()

    private fun unsupportedChildren(): Nothing {
        error("${type?.debugName ?: "Root"} cannot contain children")
    }
}

/**
 * The one Applier target used by all shared Flare UI declarations.
 */
public class FlareUiApplier(
    root: WidgetNode,
) : AbstractApplier<WidgetNode>(root) {
    override fun insertTopDown(
        index: Int,
        instance: WidgetNode,
    ) {
        current.insert(index, instance)
    }

    override fun insertBottomUp(
        index: Int,
        instance: WidgetNode,
    ): Unit = Unit

    override fun remove(
        index: Int,
        count: Int,
    ) {
        current.remove(index, count)
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        current.move(from, to, count)
    }

    override fun onClear() {
        root.clear()
    }
}

/**
 * An immutable, typed link table from widget descriptors to backend node factories.
 */
public class WidgetRegistry private constructor(
    private val entries: Map<WidgetType<*>, Entry>,
) {
    public class Builder {
        private val entries = mutableMapOf<WidgetType<*>, Entry>()

        @Suppress("UNCHECKED_CAST")
        public fun <P : Any, N : WidgetNode> bind(
            type: WidgetType<P>,
            create: () -> N,
            update: N.(P) -> Unit,
        ) {
            val entry =
                Entry(
                    create = create,
                    update = { node, props -> update(node as N, props as P) },
                )
            check(entries.put(type, entry) == null) {
                "A factory for ${type.debugName} is already registered"
            }
        }

        internal fun build(): WidgetRegistry = WidgetRegistry(entries.toMap())
    }

    public companion object {
        public fun build(configure: Builder.() -> Unit): WidgetRegistry = Builder().apply(configure).build()
    }

    internal fun <P : Any> create(type: WidgetType<P>): WidgetNode = entry(type).create()

    internal fun <P : Any> update(
        type: WidgetType<P>,
        node: WidgetNode,
        props: P,
    ) {
        entry(type).update(node, props)
    }

    private fun entry(type: WidgetType<*>): Entry = entries[type] ?: error("No factory registered for ${type.debugName}")

    private class Entry(
        val create: () -> WidgetNode,
        val update: (WidgetNode, Any) -> Unit,
    )
}

private val LocalWidgetRegistry =
    staticCompositionLocalOf<WidgetRegistry> {
        error("No WidgetRegistry was provided")
    }

/**
 * Installs one immutable backend registry for [content].
 */
@Composable
@FlareUiComposable
public fun ProvideWidgetRegistry(
    registry: WidgetRegistry,
    content: FlareUiContent,
) {
    CompositionLocalProvider(
        LocalWidgetRegistry provides registry,
        content = { content() },
    )
}

@Composable
@FlareUiComposable
internal fun <P : Any> EmitWidget(
    type: WidgetType<P>,
    props: P,
    content: FlareUiContent,
) {
    val registry = LocalWidgetRegistry.current
    ComposeNode<WidgetNode, FlareUiApplier>(
        factory = { registry.create(type) },
        update = {
            set(props) {
                registry.update(type, this, it)
            }
        },
        content = { content() },
    )
}
