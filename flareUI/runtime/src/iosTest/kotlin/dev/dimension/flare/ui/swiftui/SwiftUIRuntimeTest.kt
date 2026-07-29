@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    dev.dimension.flare.ui.LowLevelFlareApi::class,
)

package dev.dimension.flare.ui.swiftui

import dev.dimension.flare.ui.FlareComponentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class SwiftUIRuntimeTest {
    @Test
    fun swiftOwnedPluginPopulatesTypedWidgetSystem() {
        val tree = FlareSwiftUITree()
        val component = FlareComponentType<TestNode>("SwiftOwnedTest")
        val plugin =
            object : FlareSwiftUINodePlugin {
                override fun install(registrar: FlareSwiftUINodeRegistrar) {
                    registrar.register(component) { registeredTree ->
                        TestNode(registeredTree)
                    }
                }
            }

        val widgetSystem = createSwiftUIWidgetSystem(listOf(plugin))
        val node = widgetSystem.create(SwiftUIBackend(tree), component)

        assertIs<TestNode>(node)
        assertSame(tree, node.sourceTree)
        tree.dispose()
    }

    @Test
    fun childSlotPreservesLiveNodeIdentityAcrossMoves() {
        val tree = FlareSwiftUITree()
        val first = TestNode(tree)
        val second = TestNode(tree)
        val third = TestNode(tree)

        tree.root.insert(0, first)
        tree.root.insert(1, second)
        tree.root.insert(2, third)
        tree.root.move(fromIndex = 0, toIndex = 3, count = 1)

        assertEquals(3, tree.root.nodes.size)
        assertSame(second, tree.root.nodes[0])
        assertSame(third, tree.root.nodes[1])
        assertSame(first, tree.root.nodes[2])
    }

    @Test
    fun structuralChangesAreDeduplicatedPerApplyTransaction() {
        val tree = FlareSwiftUITree()
        val observer = TestObserver()
        tree.setObserver(observer)
        observer.reset()

        tree.beginChanges()
        tree.root.insert(0, TestNode(tree))
        tree.root.insert(1, TestNode(tree))
        tree.root.insert(2, TestNode(tree))
        tree.endChanges()

        assertEquals(1, observer.notifications)
        assertEquals(listOf(tree.root), observer.changedChildren)
        assertEquals(emptyList(), observer.changedNodes)
        tree.dispose()
    }

    @Test
    fun propertyChangesNotifyOnlyDirtyNodes() {
        val tree = FlareSwiftUITree()
        val observer = TestObserver()
        val first = TestNode(tree)
        val second = TestNode(tree)
        tree.setObserver(observer)
        observer.reset()

        tree.beginChanges()
        first.invalidateForTest()
        first.invalidateForTest()
        second.invalidateForTest()
        tree.endChanges()

        val expectedNodes: List<FlareSwiftUINode> = listOf(first, second)
        assertEquals(expectedNodes, observer.changedNodes)
        assertEquals(emptyList(), observer.changedChildren)
        tree.dispose()
    }

    private class TestNode(
        val sourceTree: FlareSwiftUITree,
    ) : FlareSwiftUINode(sourceTree) {
        fun invalidateForTest() {
            invalidate()
        }
    }

    private class TestObserver : FlareSwiftUITreeObserver {
        var notifications: Int = 0
        val changedNodes = mutableListOf<FlareSwiftUINode>()
        val changedChildren = mutableListOf<FlareSwiftUIChildren>()

        override fun nodeDidChange(node: FlareSwiftUINode) {
            notifications += 1
            changedNodes += node
        }

        override fun childrenDidChange(children: FlareSwiftUIChildren) {
            notifications += 1
            changedChildren += children
        }

        fun reset() {
            notifications = 0
            changedNodes.clear()
            changedChildren.clear()
        }
    }
}
