@file:OptIn(
    ExperimentalNativeKitNavigation::class,
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
)

package dev.dimension.compose.nativekit.navigation

import androidx.navigation3.runtime.NavEntry
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitSubcomposition
import dev.dimension.compose.nativekit.NativeKitSubcompositionFactory
import dev.dimension.compose.nativekit.NativeKitWidget
import kotlin.test.Test
import kotlin.test.assertEquals

public class NavigationEntryContentHostTest {
    @Test
    public fun skipsResettingContentForTheSameNavEntryInstance() {
        val factory = RecordingSubcompositionFactory()
        val original = resolvedEntry(NavEntry(key = "home", contentKey = "home") {})
        val host =
            NavigationEntryContentHost(
                root = EmptyChildren,
                nativeControllerOwner = null,
                subcompositions = factory,
                initialEntry = original,
            )

        host.update(original)

        assertEquals(1, factory.composition.setContentCalls)

        val replacement = resolvedEntry(NavEntry(key = "home", contentKey = "home") {})
        host.update(replacement)

        assertEquals(2, factory.composition.setContentCalls)
        host.dispose()
        assertEquals(1, factory.composition.disposeCalls)
    }

    @Test
    public fun deactivatedHostDefersUpdatesUntilItIsActivatedAgain() {
        val factory = RecordingSubcompositionFactory()
        val original = resolvedEntry(NavEntry(key = "home", contentKey = "home") {})
        val host =
            NavigationEntryContentHost(
                root = EmptyChildren,
                nativeControllerOwner = null,
                subcompositions = factory,
                initialEntry = original,
            )

        host.deactivate()
        val replacement = resolvedEntry(NavEntry(key = "home", contentKey = "home") {})
        host.update(replacement)

        assertEquals(1, factory.composition.deactivateCalls)
        assertEquals(1, factory.composition.setContentCalls)

        host.activate()

        assertEquals(2, factory.composition.setContentCalls)
        host.dispose()
    }
}

private fun resolvedEntry(entry: NavEntry<String>): ResolvedNavigationEntry = resolveNavigationEntries(listOf(entry)).single()

private class RecordingSubcompositionFactory : NativeKitSubcompositionFactory {
    val composition = RecordingSubcomposition()

    override fun create(root: NativeKitChildren): NativeKitSubcomposition = composition
}

private class RecordingSubcomposition : NativeKitSubcomposition {
    var setContentCalls: Int = 0
        private set
    var disposeCalls: Int = 0
        private set
    var deactivateCalls: Int = 0
        private set

    override fun setContent(content: NativeKitContent) {
        setContentCalls += 1
    }

    override fun deactivate() {
        deactivateCalls += 1
    }

    override fun dispose() {
        disposeCalls += 1
    }
}

private object EmptyChildren : NativeKitChildren {
    override fun insert(
        index: Int,
        widget: NativeKitWidget,
    ) = Unit

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) = Unit

    override fun remove(
        index: Int,
        count: Int,
    ) = Unit
}
