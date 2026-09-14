@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    dev.dimension.compose.nativekit.navigation.ExperimentalNativeKitNavigation::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit.appkit

import androidx.navigation3.runtime.NavEntry
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitSubcomposition
import dev.dimension.compose.nativekit.NativeKitSubcompositionFactory
import dev.dimension.compose.nativekit.navigation.NavigationPresentation
import dev.dimension.compose.nativekit.navigation.ResolvedNavigationEntry
import kotlin.test.Test
import kotlin.test.assertEquals

public class AppKitNavigationEntryControllerTest {
    @Test
    public fun retainsControllerIdentityWithoutRetainingHiddenContent() {
        val factory = RecordingSubcompositionFactory()
        val controller =
            AppKitNavigationEntryController(
                initialEntry = entry("home"),
                subcompositions = factory,
            )

        controller.view
        assertEquals(0, factory.created)

        controller.realizeContent()
        controller.realizeContent()
        assertEquals(1, factory.created)
        assertEquals(0, factory.disposed)

        controller.releaseContent()
        controller.releaseContent()
        assertEquals(1, factory.disposed)

        controller.realizeContent()
        assertEquals(2, factory.created)

        controller.dispose()
        controller.dispose()
        assertEquals(2, factory.disposed)
    }

    @Test
    public fun deactivatedContentKeepsItsHostAndReactivatesWithoutRecreation() {
        val factory = RecordingSubcompositionFactory()
        val controller =
            AppKitNavigationEntryController(
                initialEntry = entry("home"),
                subcompositions = factory,
            )

        controller.realizeContent()
        controller.deactivateContent()
        controller.deactivateContent()

        assertEquals(1, factory.created)
        assertEquals(1, factory.deactivated)
        assertEquals(1, factory.installed)
        assertEquals(0, factory.disposed)

        controller.realizeContent()

        assertEquals(1, factory.created)
        assertEquals(2, factory.installed)
        assertEquals(0, factory.disposed)

        controller.dispose()
        assertEquals(1, factory.disposed)
    }
}

private fun entry(contentKey: String): ResolvedNavigationEntry =
    ResolvedNavigationEntry(
        contentKey = contentKey,
        presentation = NavigationPresentation.Page,
        entry =
            NavEntry(
                key = contentKey,
                contentKey = contentKey,
            ) {},
    )

private class RecordingSubcompositionFactory : NativeKitSubcompositionFactory {
    var created: Int = 0
        private set
    var disposed: Int = 0
        private set
    var deactivated: Int = 0
        private set
    var installed: Int = 0
        private set

    override fun create(root: NativeKitChildren): NativeKitSubcomposition {
        created += 1
        return RecordingSubcomposition(
            onInstalled = { installed += 1 },
            onDeactivated = { deactivated += 1 },
            onDisposed = { disposed += 1 },
        )
    }
}

private class RecordingSubcomposition(
    private val onInstalled: () -> Unit,
    private val onDeactivated: () -> Unit,
    private val onDisposed: () -> Unit,
) : NativeKitSubcomposition {
    private var disposed: Boolean = false

    override fun setContent(content: NativeKitContent) {
        onInstalled()
    }

    override fun deactivate() {
        onDeactivated()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        onDisposed()
    }
}
