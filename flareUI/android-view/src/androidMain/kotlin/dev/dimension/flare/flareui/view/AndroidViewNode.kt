package dev.dimension.flare.flareui.view

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import dev.dimension.flare.flareui.WidgetNode
import dev.dimension.flare.flareui.WidgetType

internal class AndroidViewNode(
    type: WidgetType<*>?,
    internal val view: View,
) : WidgetNode(type) {
    override fun insert(
        index: Int,
        child: WidgetNode,
    ) {
        val parent = view.requireViewGroup()
        val childView = child.requireAndroidViewNode().view
        if (parent is LinearLayout) {
            parent.addView(
                childView,
                index,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        } else {
            parent.addView(childView, index)
        }
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        if (from == to || count == 0) return

        val parent = view.requireViewGroup()
        val moved = List(count) { offset -> parent.getChildAt(from + offset) }
        parent.removeViews(from, count)

        val destination = if (from > to) to else to - count
        moved.forEachIndexed { offset, child ->
            parent.addView(child, destination + offset)
        }
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        view.requireViewGroup().removeViews(index, count)
    }

    override fun clear() {
        view.requireViewGroup().removeAllViews()
    }

    private fun View.requireViewGroup(): ViewGroup =
        this as? ViewGroup
            ?: error("${this::class.simpleName} cannot contain children")

    private fun WidgetNode.requireAndroidViewNode(): AndroidViewNode =
        this as? AndroidViewNode
            ?: error("Cannot mix Android View nodes with another backend")
}
