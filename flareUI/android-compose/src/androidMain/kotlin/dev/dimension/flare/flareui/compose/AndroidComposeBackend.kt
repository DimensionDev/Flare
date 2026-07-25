package dev.dimension.flare.flareui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import dev.dimension.flare.flareui.FlareUiApplier
import dev.dimension.flare.flareui.FlareUiContent
import dev.dimension.flare.flareui.ProvideWidgetRegistry
import dev.dimension.flare.flareui.WidgetNode
import dev.dimension.flare.flareui.WidgetRegistry
import dev.dimension.flare.flareui.WidgetType

/**
 * Hosts the Flare UI composition and renders its small observable tree with Compose UI.
 */
@Composable
@UiComposable
public fun FlareComposeContent(content: FlareUiContent) {
    val parent = rememberCompositionContext()
    val root = remember { ComposeTreeNode(type = null) }
    val registry = remember { composeWidgetRegistry() }
    val currentContent by rememberUpdatedState(content)

    DisposableEffect(parent, root, registry) {
        val composition =
            createFlareComposition(
                root = root,
                parent = parent,
                registry = registry,
                content = { currentContent() },
            )
        onDispose(composition::dispose)
    }

    root.children.forEach { child ->
        key(child) {
            renderGeneratedComposeNode(child)
        }
    }
}

private fun createFlareComposition(
    root: ComposeTreeNode,
    parent: CompositionContext,
    registry: WidgetRegistry,
    content: FlareUiContent,
): Composition =
    Composition(
        applier = FlareUiApplier(root),
        parent = parent,
    ).apply {
        setContent {
            ProvideWidgetRegistry(
                registry = registry,
                content = { content() },
            )
        }
    }

private fun composeWidgetRegistry(): WidgetRegistry = generatedComposeWidgetRegistry()

internal class ComposeTreeNode(
    type: WidgetType<*>?,
) : WidgetNode(type) {
    internal var value: Any? by mutableStateOf(null)
    internal val children: MutableList<ComposeTreeNode> = mutableStateListOf()

    override fun insert(
        index: Int,
        child: WidgetNode,
    ) {
        children.add(index, child.requireComposeTreeNode())
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        if (from == to || count == 0) return

        val moved = children.subList(from, from + count).toList()
        children.subList(from, from + count).clear()
        val destination = if (from > to) to else to - count
        children.addAll(destination, moved)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        children.subList(index, index + count).clear()
    }

    override fun clear() {
        children.clear()
    }

    private fun WidgetNode.requireComposeTreeNode(): ComposeTreeNode =
        this as? ComposeTreeNode
            ?: error("Cannot mix Compose tree nodes with another backend")
}

@Composable
@UiComposable
internal fun renderComposeChildren(children: List<ComposeTreeNode>) {
    children.forEach { child ->
        key(child) {
            renderGeneratedComposeNode(child)
        }
    }
}

internal inline fun <reified P : Any> ComposeTreeNode.requireValue(): P =
    value as? P
        ?: error("Invalid props for ${type?.debugName}: ${value?.let { it::class.simpleName }}")
