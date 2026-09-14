@file:Suppress("ktlint:standard:annotation")

package dev.dimension.compose.nativekit

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableTargetMarker
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReusableComposition
import androidx.compose.runtime.Updater
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.reflect.KClass

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is a low-level NativeKit renderer API.",
)
@Retention(AnnotationRetention.BINARY)
public annotation class LowLevelNativeKitApi

@Retention(AnnotationRetention.BINARY)
@ComposableTargetMarker(description = "Compose NativeKit")
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
)
public annotation class NativeKitComposable

public typealias NativeKitContent = @Composable @NativeKitComposable () -> Unit

/** Creates independently disposable NativeKit compositions which share the current renderer context. */
@LowLevelNativeKitApi
public interface NativeKitSubcompositionFactory {
    public fun create(root: NativeKitChildren): NativeKitSubcomposition
}

/** One independently disposable NativeKit composition created for deferred content such as a list item. */
@LowLevelNativeKitApi
public interface NativeKitSubcomposition {
    public fun setContent(content: NativeKitContent)

    /** Stops observations and remembered effects while preserving the emitted widget tree. */
    public fun deactivate()

    public fun dispose()
}

private val LocalNativeKitWidgetFactory =
    staticCompositionLocalOf<BoundNativeKitWidgetFactory> {
        error("No NativeKitWidgetSystem was provided.")
    }

private interface BoundNativeKitWidgetFactory {
    fun <W : NativeKitWidget> create(componentType: KClass<W>): W
}

@OptIn(LowLevelNativeKitApi::class)
private class DefaultBoundNativeKitWidgetFactory<B : NativeKitBackend>(
    private val widgetSystem: NativeKitWidgetSystem<B>,
    private val backend: B,
) : BoundNativeKitWidgetFactory {
    override fun <W : NativeKitWidget> create(componentType: KClass<W>): W = widgetSystem.create(backend, componentType)
}

/**
 * Owns one Compose Runtime composition which mutates widgets supplied by the selected backend.
 *
 * The platform host owns the [parent] recomposer and is responsible for its frame clock and thread.
 */
public class NativeKitComposition<B : NativeKitBackend>(
    root: NativeKitChildren,
    widgetSystem: NativeKitWidgetSystem<B>,
    backend: B,
    parent: CompositionContext,
) {
    private val delegate =
        DefaultNativeKitSubcomposition(
            root = root,
            widgetFactory = DefaultBoundNativeKitWidgetFactory(widgetSystem, backend),
            parent = parent,
        )

    public fun setContent(content: NativeKitContent) {
        delegate.setContent(content)
    }

    public fun dispose() {
        delegate.dispose()
    }
}

/** Remembers an owner for deferred child compositions and closes every child with its parent. */
@LowLevelNativeKitApi
@Composable
@NativeKitComposable
public fun rememberNativeKitSubcompositionFactory(): NativeKitSubcompositionFactory {
    val parent = rememberCompositionContext()
    val widgetFactory = LocalNativeKitWidgetFactory.current
    val factory =
        remember(parent, widgetFactory) {
            DefaultNativeKitSubcompositionFactory(
                parent = parent,
                widgetFactory = widgetFactory,
            )
        }
    DisposableEffect(factory) {
        onDispose(factory::dispose)
    }
    return factory
}

@OptIn(LowLevelNativeKitApi::class)
private class DefaultNativeKitSubcompositionFactory(
    private val parent: CompositionContext,
    private val widgetFactory: BoundNativeKitWidgetFactory,
) : NativeKitSubcompositionFactory {
    private val compositions = mutableSetOf<DefaultNativeKitSubcomposition>()
    private var disposed: Boolean = false

    override fun create(root: NativeKitChildren): NativeKitSubcomposition {
        check(!disposed) { "NativeKitSubcompositionFactory is already disposed." }
        lateinit var result: DefaultNativeKitSubcomposition
        result =
            DefaultNativeKitSubcomposition(
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
        current.forEach(DefaultNativeKitSubcomposition::dispose)
    }
}

@OptIn(LowLevelNativeKitApi::class)
private class DefaultNativeKitSubcomposition(
    root: NativeKitChildren,
    private val widgetFactory: BoundNativeKitWidgetFactory,
    parent: CompositionContext,
    private val onDisposed: () -> Unit = {},
) : NativeKitSubcomposition {
    private val rootNode = RootRuntimeNode(root)
    private val composition: ReusableComposition =
        ReusableComposition(
            applier = NativeKitApplier(rootNode),
            parent = parent,
        )
    private var disposed: Boolean = false

    override fun setContent(content: NativeKitContent) {
        check(!disposed) { "NativeKitSubcomposition is already disposed." }
        composition.setContent {
            CompositionLocalProvider(LocalNativeKitWidgetFactory provides widgetFactory) {
                content()
            }
        }
    }

    override fun deactivate() {
        check(!disposed) { "NativeKitSubcomposition is already disposed." }
        composition.deactivate()
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
@LowLevelNativeKitApi
public class NativeKitWidgetUpdater<W : NativeKitWidget> internal constructor(
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

    internal fun setModifier(modifier: NativeKitModifier) {
        updater.set(modifier) {
            requireWidgetNode().setModifier(it)
        }
    }
}

/**
 * Emits one renderer-provided primitive. Normally called only by primitive APIs.
 */
@LowLevelNativeKitApi
@Composable
@NativeKitComposable
public fun <W : NativeKitWidget> EmitNativeKitWidget(
    componentType: KClass<W>,
    modifier: NativeKitModifier = NativeKitModifier.None,
    update: NativeKitWidgetUpdater<W>.() -> Unit = {},
    content: NativeKitContent? = null,
) {
    val widgetFactory = LocalNativeKitWidgetFactory.current
    ComposeNode<RuntimeNode, NativeKitApplier>(
        factory = {
            WidgetRuntimeNode(
                widget = widgetFactory.create(componentType),
            )
        },
        update = {
            NativeKitWidgetUpdater<W>(this).apply {
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

private class NativeKitApplier(
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
