@file:Suppress("ktlint:standard:annotation")

package dev.dimension.flare.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableTargetMarker
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Updater
import androidx.compose.runtime.staticCompositionLocalOf

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is a low-level Flare renderer/code-generation API.",
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

private val LocalFlareWidgetFactory =
    staticCompositionLocalOf<BoundFlareWidgetFactory> {
        error("No FlareWidgetSystem was provided.")
    }

private interface BoundFlareWidgetFactory {
    fun <W : FlareWidget> create(componentType: FlareComponentType<W>): W
}

@OptIn(LowLevelFlareApi::class)
private class DefaultBoundFlareWidgetFactory<B : FlareBackend>(
    private val widgetSystem: FlareWidgetSystem<B>,
    private val backend: B,
) : BoundFlareWidgetFactory {
    override fun <W : FlareWidget> create(componentType: FlareComponentType<W>): W = widgetSystem.create(backend, componentType)
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
    private val rootNode = RootRuntimeNode(root)
    private val widgetFactory = DefaultBoundFlareWidgetFactory(widgetSystem, backend)
    private val composition: Composition =
        Composition(
            applier = FlareApplier(rootNode),
            parent = parent,
        )
    private var disposed: Boolean = false

    public fun setContent(content: FlareContent) {
        check(!disposed) { "FlareComposition is already disposed." }
        composition.setContent {
            CompositionLocalProvider(LocalFlareWidgetFactory provides widgetFactory) {
                content()
            }
        }
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        try {
            composition.dispose()
        } finally {
            rootNode.clear()
        }
    }
}

/**
 * Update surface consumed by generated primitive functions.
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
 * Emits one renderer-provided primitive. Normally called only by generated primitive APIs.
 */
@LowLevelFlareApi
@Composable
@FlareUiComposable
public fun <W : FlareWidget> EmitFlareWidget(
    componentType: FlareComponentType<W>,
    modifier: FlareModifier = FlareModifier,
    update: FlareWidgetUpdater<W>.() -> Unit = {},
    slots: FlareContent? = null,
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
        content = slots ?: {},
    )
}

/** Emits a named child group below the current primitive. */
@LowLevelFlareApi
@Composable
@FlareUiComposable
public fun FlareSlot(
    slotId: FlareSlotId,
    content: FlareContent,
) {
    ComposeNode<RuntimeNode, FlareApplier>(
        factory = { SlotRuntimeNode(slotId) },
        update = {},
        content = { content() },
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
