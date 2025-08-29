package dev.dimension.flare.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ScrollDistanceThreshold: Dp = 40.dp

/**
 * This [Modifier] tracks vertical scroll events on the scrolling container that a floating
 * toolbar appears above. It then calls [onExpand] and [onCollapse] to adjust the toolbar's
 * state based on the scroll direction and distance.
 *
 * Essentially, it expands the toolbar when you scroll down past a certain threshold and
 * collapses it when you scroll back up. You can customize the expand and collapse thresholds
 * through the [expandScrollDistanceThreshold] and [collapseScrollDistanceThreshold].
 *
 * @param expanded the current expanded state of the floating toolbar
 * @param onExpand callback to be invoked when the toolbar should expand
 * @param onCollapse callback to be invoked when the toolbar should collapse
 * @param expandScrollDistanceThreshold the scroll distance (in dp) required to trigger an
 *   [onExpand]
 * @param collapseScrollDistanceThreshold the scroll distance (in dp) required to trigger an
 *   [onCollapse]
 * @param reverseLayout indicates that the scrollable content has a reversed scrolling direction
 */
fun Modifier.floatingToolbarVerticalNestedScroll(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    expandScrollDistanceThreshold: Dp = ScrollDistanceThreshold,
    collapseScrollDistanceThreshold: Dp = ScrollDistanceThreshold,
    reverseLayout: Boolean = false,
): Modifier =
    this then
        VerticalNestedScrollExpansionElement(
            expanded = expanded,
            onExpand = onExpand,
            onCollapse = onCollapse,
            reverseLayout = reverseLayout,
            expandScrollThreshold = expandScrollDistanceThreshold,
            collapseScrollThreshold = collapseScrollDistanceThreshold,
        )

internal class VerticalNestedScrollExpansionElement(
    val expanded: Boolean,
    val onExpand: () -> Unit,
    val onCollapse: () -> Unit,
    val reverseLayout: Boolean,
    val expandScrollThreshold: Dp,
    val collapseScrollThreshold: Dp,
) : ModifierNodeElement<VerticalNestedScrollExpansionNode>() {
    override fun create() =
        VerticalNestedScrollExpansionNode(
            expanded = expanded,
            onExpand = onExpand,
            onCollapse = onCollapse,
            reverseLayout = reverseLayout,
            expandScrollThreshold = expandScrollThreshold,
            collapseScrollThreshold = collapseScrollThreshold,
        )

    override fun update(node: VerticalNestedScrollExpansionNode) {
        node.updateNode(
            expanded,
            onExpand,
            onCollapse,
            reverseLayout,
            expandScrollThreshold,
            collapseScrollThreshold,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "floatingToolbarVerticalNestedScroll"
        properties["expanded"] = expanded
        properties["expandScrollThreshold"] = expandScrollThreshold
        properties["collapseScrollThreshold"] = collapseScrollThreshold
        properties["reverseLayout"] = reverseLayout
        properties["onExpand"] = onExpand
        properties["onCollapse"] = onCollapse
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerticalNestedScrollExpansionElement) return false

        if (expanded != other.expanded) return false
        if (reverseLayout != other.reverseLayout) return false
        if (onExpand !== other.onExpand) return false
        if (onCollapse !== other.onCollapse) return false
        if (expandScrollThreshold != other.expandScrollThreshold) return false
        if (collapseScrollThreshold != other.collapseScrollThreshold) return false

        return true
    }

    override fun hashCode(): Int {
        var result = expanded.hashCode()
        result = 31 * result + reverseLayout.hashCode()
        result = 31 * result + onExpand.hashCode()
        result = 31 * result + onCollapse.hashCode()
        result = 31 * result + expandScrollThreshold.hashCode()
        result = 31 * result + collapseScrollThreshold.hashCode()
        return result
    }
}

internal class VerticalNestedScrollExpansionNode(
    var expanded: Boolean,
    var onExpand: () -> Unit,
    var onCollapse: () -> Unit,
    var reverseLayout: Boolean,
    var expandScrollThreshold: Dp,
    var collapseScrollThreshold: Dp,
) : DelegatingNode(),
    CompositionLocalConsumerModifierNode,
    NestedScrollConnection {
    private var expandScrollThresholdPx = 0f
    private var collapseScrollThresholdPx = 0f
    private var contentOffset = 0f
    private var threshold = 0f

    // In reverse layouts, scrolling direction is flipped. We will use this factor to flip some
    // of the values we read on the onPostScroll to ensure consistent behavior regardless of
    // scroll direction.
    private var reverseLayoutFactor = if (reverseLayout) -1 else 1

    override val shouldAutoInvalidate: Boolean
        get() = false

    private var nestedScrollNode: DelegatableNode =
        nestedScrollModifierNode(connection = this, dispatcher = null)

    override fun onAttach() {
        delegate(nestedScrollNode)
        with(nestedScrollNode.requireDensity()) {
            expandScrollThresholdPx = expandScrollThreshold.toPx()
            collapseScrollThresholdPx = collapseScrollThreshold.toPx()
        }
        updateThreshold()
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val scrollDelta = consumed.y * reverseLayoutFactor
        contentOffset += scrollDelta

        if (scrollDelta < 0 && contentOffset <= threshold) {
            threshold = contentOffset + expandScrollThresholdPx
            onCollapse()
        } else if (scrollDelta > 0 && contentOffset >= threshold) {
            threshold = contentOffset - collapseScrollThresholdPx
            onExpand()
        }
        return Offset.Zero
    }

    fun updateNode(
        expanded: Boolean,
        onExpand: () -> Unit,
        onCollapse: () -> Unit,
        reverseLayout: Boolean,
        expandScrollThreshold: Dp,
        collapseScrollThreshold: Dp,
    ) {
        if (
            this.expandScrollThreshold != expandScrollThreshold ||
            this.collapseScrollThreshold != collapseScrollThreshold
        ) {
            this.expandScrollThreshold = expandScrollThreshold
            this.collapseScrollThreshold = collapseScrollThreshold
            with(nestedScrollNode.requireDensity()) {
                expandScrollThresholdPx = expandScrollThreshold.toPx()
                collapseScrollThresholdPx = collapseScrollThreshold.toPx()
            }
            updateThreshold()
        }
        if (this.reverseLayout != reverseLayout) {
            this.reverseLayout = reverseLayout
            reverseLayoutFactor = if (this.reverseLayout) -1 else 1
        }

        this.onExpand = onExpand
        this.onCollapse = onCollapse

        if (this.expanded != expanded) {
            this.expanded = expanded
            updateThreshold()
        }
    }

    private fun updateThreshold() {
        threshold =
            if (expanded) {
                contentOffset - collapseScrollThresholdPx
            } else {
                contentOffset + expandScrollThresholdPx
            }
    }
}
