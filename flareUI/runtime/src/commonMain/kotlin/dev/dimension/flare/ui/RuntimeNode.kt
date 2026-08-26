package dev.dimension.flare.ui

internal abstract class RuntimeNode {
    protected val nodes: MutableList<RuntimeNode> = mutableListOf()
    protected abstract val target: FlareChildren

    open fun prepareInsert(
        index: Int,
        instance: RuntimeNode,
    ) {
        require(instance is WidgetRuntimeNode) {
            "A Flare container can contain only primitive widgets."
        }
        require(index in 0..nodes.size) { "Invalid child insertion index $index." }
    }

    fun commitInsert(
        index: Int,
        instance: RuntimeNode,
    ) {
        val child = instance as WidgetRuntimeNode
        target.insert(index, child.widget)
        nodes.add(index, child)
    }

    fun remove(
        index: Int,
        count: Int,
    ) {
        requireRange(index, count)
        val removed = nodes.subList(index, index + count).map { it as WidgetRuntimeNode }
        target.remove(index, count)
        nodes.subList(index, index + count).clear()
        removed.forEach(WidgetRuntimeNode::disposeSubtree)
    }

    fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        if (from == to || count == 0) return
        target.move(from, to, count)
        nodes.moveRange(from, to, count)
    }

    fun clear() {
        if (nodes.isNotEmpty()) {
            remove(0, nodes.size)
        }
    }

    abstract fun disposeSubtree()

    private fun requireRange(
        index: Int,
        count: Int,
    ) {
        require(index >= 0 && count >= 0 && index + count <= nodes.size) {
            "Invalid child range index=$index, count=$count, size=${nodes.size}."
        }
    }
}

internal class RootRuntimeNode(
    override val target: FlareChildren,
) : RuntimeNode() {
    fun onBeginChanges() {
        target.onBeginChanges()
    }

    fun onEndChanges() {
        target.onEndChanges()
    }

    override fun disposeSubtree() {
        clear()
    }
}

internal class WidgetRuntimeNode(
    val widget: FlareWidget,
) : RuntimeNode() {
    private var disposed: Boolean = false

    override val target: FlareChildren
        get() = checkNotNull(widget.children) { "$widget does not accept children." }

    fun setModifier(modifier: FlareModifier) {
        widget.updateModifier(modifier)
    }

    override fun disposeSubtree() {
        if (disposed) return
        disposed = true
        clear()
        widget.dispose()
    }
}

private fun MutableList<RuntimeNode>.moveRange(
    from: Int,
    to: Int,
    count: Int,
) {
    if (from == to || count == 0) return
    require(from >= 0 && count >= 0 && from + count <= size) {
        "Invalid move source from=$from, count=$count, size=$size."
    }
    require(to >= 0 && to <= size) { "Invalid move destination to=$to, size=$size." }

    val moved = subList(from, from + count).toList()
    subList(from, from + count).clear()
    val destination = if (from > to) to else to - count
    addAll(destination, moved)
}
