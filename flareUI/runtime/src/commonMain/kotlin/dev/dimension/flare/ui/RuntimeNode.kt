package dev.dimension.flare.ui

internal abstract class RuntimeNode {
    protected val nodes: MutableList<RuntimeNode> = mutableListOf()

    open fun prepareInsert(
        index: Int,
        instance: RuntimeNode,
    ): Unit = Unit

    abstract fun commitInsert(
        index: Int,
        instance: RuntimeNode,
    )

    abstract fun remove(
        index: Int,
        count: Int,
    )

    open fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        nodes.moveRange(from, to, count)
    }

    fun clear() {
        if (nodes.isNotEmpty()) {
            remove(0, nodes.size)
        }
    }

    abstract fun disposeSubtree()
}

internal abstract class ChildrenRuntimeNode : RuntimeNode() {
    protected abstract val target: FlareChildren

    override fun prepareInsert(
        index: Int,
        instance: RuntimeNode,
    ) {
        require(instance is WidgetRuntimeNode) {
            "A root or slot can contain only primitive widgets."
        }
        require(index in 0..nodes.size) { "Invalid child insertion index $index." }
    }

    override fun commitInsert(
        index: Int,
        instance: RuntimeNode,
    ) {
        val child = instance as WidgetRuntimeNode
        target.insert(index, child.widget)
        nodes.add(index, child)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        requireRange(index, count)
        val removed = nodes.subList(index, index + count).map { node -> node as WidgetRuntimeNode }
        target.remove(index, count)
        nodes.subList(index, index + count).clear()
        removed.forEach(WidgetRuntimeNode::disposeSubtree)
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        if (from == to || count == 0) return
        target.move(from, to, count)
        super.move(from, to, count)
    }

    override fun disposeSubtree() {
        clear()
    }

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
) : ChildrenRuntimeNode() {
    fun onBeginChanges() {
        target.onBeginChanges()
    }

    fun onEndChanges() {
        target.onEndChanges()
    }
}

internal class SlotRuntimeNode(
    private val slotId: FlareSlotId,
) : ChildrenRuntimeNode() {
    private var boundTarget: FlareChildren? = null

    override val target: FlareChildren
        get() = checkNotNull(boundTarget) { "Slot '$slotId' was used before it was bound to a widget." }

    fun bind(widget: FlareWidget) {
        check(boundTarget == null) { "Slot '$slotId' is already bound." }
        boundTarget = widget.children(slotId)
    }

    fun unbind() {
        clear()
        boundTarget = null
    }

    override fun disposeSubtree() {
        unbind()
    }

    override fun toString(): String = "BoundSlot($slotId)"
}

internal class WidgetRuntimeNode(
    val widget: FlareWidget,
) : RuntimeNode() {
    private var disposed: Boolean = false

    override fun prepareInsert(
        index: Int,
        instance: RuntimeNode,
    ) {
        val slot =
            instance as? SlotRuntimeNode
                ?: error("A primitive can contain only named Flare slots.")
        require(index in 0..nodes.size) { "Invalid slot insertion index $index." }
        slot.bind(widget)
    }

    override fun commitInsert(
        index: Int,
        instance: RuntimeNode,
    ) {
        nodes.add(index, instance)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        require(index >= 0 && count >= 0 && index + count <= nodes.size) {
            "Invalid slot range index=$index, count=$count, size=${nodes.size}."
        }
        val removed = nodes.subList(index, index + count).map { node -> node as SlotRuntimeNode }
        removed.forEach(SlotRuntimeNode::disposeSubtree)
        nodes.subList(index, index + count).clear()
    }

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
