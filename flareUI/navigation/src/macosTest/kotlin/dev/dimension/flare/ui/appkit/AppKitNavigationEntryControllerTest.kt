@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import androidx.navigation3.runtime.NavEntry
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.navigation.NavigationPresentation
import dev.dimension.flare.ui.navigation.ResolvedNavigationEntry
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

private class RecordingSubcompositionFactory : FlareSubcompositionFactory {
    var created: Int = 0
        private set
    var disposed: Int = 0
        private set
    var deactivated: Int = 0
        private set
    var installed: Int = 0
        private set

    override fun create(root: FlareChildren): FlareSubcomposition {
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
) : FlareSubcomposition {
    private var disposed: Boolean = false

    override fun setContent(content: FlareContent) {
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
