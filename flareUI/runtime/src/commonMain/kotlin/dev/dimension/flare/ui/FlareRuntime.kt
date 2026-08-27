@file:Suppress("ktlint:standard:annotation")

package dev.dimension.flare.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableTargetMarker
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Updater
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.reflect.KClass

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is a low-level Flare renderer API.",
)
@Retention(AnnotationRetention.BINARY)
public annotation class LowLevelFlareApi

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

public typealias FlareContent = @Composable @FlareUiComposable () -> Unit

/** Creates independently disposable Flare compositions which share the current renderer context. */
@LowLevelFlareApi
public interface FlareSubcompositionFactory {
    public fun create(root: FlareChildren): FlareSubcomposition
}

/** One independently disposable Flare composition created for deferred content such as a list item. */
@LowLevelFlareApi
public interface FlareSubcomposition {
    public fun setContent(content: FlareContent)

    public fun dispose()
}

private val LocalFlareWidgetFactory =
    staticCompositionLocalOf<BoundFlareWidgetFactory> {
        error("No FlareWidgetSystem was provided.")
    }

private interface BoundFlareWidgetFactory {
    fun <W : FlareWidget> create(componentType: KClass<W>): W
}

@OptIn(LowLevelFlareApi::class)
private class DefaultBoundFlareWidgetFactory<B : FlareBackend>(
    private val widgetSystem: FlareWidgetSystem<B>,
    private val backend: B,
) : BoundFlareWidgetFactory {
    override fun <W : FlareWidget> create(componentType: KClass<W>): W = widgetSystem.create(backend, componentType)
}

/**
 * Owns one Compose Runtime composition which mutates widgets supplied by the selected backend.
 *
 * The platform host owns the [parent] recomposer and is responsible for its frame clock and thread.
 */
public class FlareComposition<B : FlareBackend>(
    root: FlareChildren,
    widgetSystem: FlareWidgetSystem<B>,
    backend: B,
    parent: CompositionContext,
) {
    private val delegate =
        DefaultFlareSubcomposition(
            root = root,
            widgetFactory = DefaultBoundFlareWidgetFactory(widgetSystem, backend),
            parent = parent,
        )

    public fun setContent(content: FlareContent) {
        delegate.setContent(content)
    }

    public fun dispose() {
        delegate.dispose()
    }
}

/** Remembers an owner for deferred child compositions and closes every child with its parent. */
@LowLevelFlareApi
@Composable
@FlareUiComposable
public fun rememberFlareSubcompositionFactory(): FlareSubcompositionFactory {
    val parent = rememberCompositionContext()
    val widgetFactory = LocalFlareWidgetFactory.current
    val factory =
        remember(parent, widgetFactory) {
            DefaultFlareSubcompositionFactory(
                parent = parent,
                widgetFactory = widgetFactory,
            )
        }
    DisposableEffect(factory) {
        onDispose(factory::dispose)
    }
    return factory
}

@OptIn(LowLevelFlareApi::class)
private class DefaultFlareSubcompositionFactory(
    private val parent: CompositionContext,
    private val widgetFactory: BoundFlareWidgetFactory,
) : FlareSubcompositionFactory {
    private val compositions = mutableSetOf<DefaultFlareSubcomposition>()
    private var disposed: Boolean = false

    override fun create(root: FlareChildren): FlareSubcomposition {
        check(!disposed) { "FlareSubcompositionFactory is already disposed." }
        lateinit var result: DefaultFlareSubcomposition
        result =
            DefaultFlareSubcomposition(
                root = root,
                widgetFactory = widgetFactory,
                parent = parent,
                onDisposed = { compositions.remove(result) },
            )
        compositions += result
        return result
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        val current = compositions.toList()
        compositions.clear()
        current.forEach(DefaultFlareSubcomposition::dispose)
    }
}

@OptIn(LowLevelFlareApi::class)
private class DefaultFlareSubcomposition(
    root: FlareChildren,
    private val widgetFactory: BoundFlareWidgetFactory,
    parent: CompositionContext,
    private val onDisposed: () -> Unit = {},
) : FlareSubcomposition {
    private val rootNode = RootRuntimeNode(root)
    private val composition: Composition =
        Composition(
            applier = FlareApplier(rootNode),
            parent = parent,
        )
    private var disposed: Boolean = false

    override fun setContent(content: FlareContent) {
        check(!disposed) { "FlareSubcomposition is already disposed." }
        composition.setContent {
            CompositionLocalProvider(LocalFlareWidgetFactory provides widgetFactory) {
                content()
            }
        }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        try {
            composition.dispose()
        } finally {
            try {
                rootNode.clear()
            } finally {
                onDisposed()
            }
        }
    }
}

/**
 * Typed update surface consumed by primitive functions.
 *
 * It deliberately exposes typed values one at a time rather than passing an untyped props object
 * through the renderer registry.
 */
@LowLevelFlareApi
public class FlareWidgetUpdater<W : FlareWidget> internal constructor(
    private val updater: Updater<RuntimeNode>,
) {
    public fun <V> set(
        value: V,
        update: W.(V) -> Unit,
    ) {
        updater.set(value) {
            @Suppress("UNCHECKED_CAST")
            (requireWidgetNode().widget as W).update(it)
        }
    }

    internal fun setModifier(modifier: FlareModifier) {
        updater.set(modifier) {
            requireWidgetNode().setModifier(it)
        }
    }
}

/**
 * Emits one renderer-provided primitive. Normally called only by primitive APIs.
 */
@LowLevelFlareApi
@Composable
@FlareUiComposable
public fun <W : FlareWidget> EmitFlareWidget(
    componentType: KClass<W>,
    modifier: FlareModifier = FlareModifier.None,
    update: FlareWidgetUpdater<W>.() -> Unit = {},
    content: FlareContent? = null,
) {
    val widgetFactory = LocalFlareWidgetFactory.current
    ComposeNode<RuntimeNode, FlareApplier>(
        factory = {
            WidgetRuntimeNode(
                widget = widgetFactory.create(componentType),
            )
        },
        update = {
            FlareWidgetUpdater<W>(this).apply {
                setModifier(modifier)
                update()
            }
        },
        content = content ?: {},
    )
}

private fun RuntimeNode.requireWidgetNode(): WidgetRuntimeNode =
    this as? WidgetRuntimeNode
        ?: error("A primitive property update was applied to a non-widget runtime node.")

private class FlareApplier(
    private val rootNode: RootRuntimeNode,
) : AbstractApplier<RuntimeNode>(rootNode) {
    override fun onBeginChanges() {
        rootNode.onBeginChanges()
    }

    override fun onEndChanges() {
        rootNode.onEndChanges()
    }

    override fun insertTopDown(
        index: Int,
        instance: RuntimeNode,
    ) {
        current.prepareInsert(index, instance)
    }

    override fun insertBottomUp(
        index: Int,
        instance: RuntimeNode,
    ) {
        current.commitInsert(index, instance)
    }

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
