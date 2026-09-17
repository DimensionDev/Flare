package dev.dimension.flare.ui.screen.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.route.Route

internal const val MEDIA_SHARED_ROUTE = "media_shared_route"

internal fun Any?.isMediaViewerRoute(): Boolean =
    this is Route.Media.Image || this is Route.Media.RawMedia || this is Route.Media.StatusMedia

internal class MediaSharedSceneStrategy(
    private val host: MediaSharedTransitionHostState,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        val route = entry.metadata[MEDIA_SHARED_ROUTE] as? Route.Media ?: return null
        val previous = entries.dropLast(1)
        val underlying = previous.lastOrNull() ?: return null
        // Shared elements cannot cross window boundaries. Keep the normal dialog fallback.
        if (DialogSceneStrategy.dialog().keys.single() in underlying.metadata || BottomSheetSceneStrategy.isBottomSheetEntry(underlying)) {
            return null
        }
        return MediaSharedScene(entry, route, previous, host, onBack)
    }
}

private class MediaSharedScene(
    private val entry: NavEntry<NavKey>,
    private val route: Route.Media,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val host: MediaSharedTransitionHostState,
    private val onBack: () -> Unit,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries = listOf(entry)
    override val overlaidEntries = previousEntries
    private var presentation: MediaSharedPresentation? = null

    override val content: @Composable () -> Unit = {
        val context = currentCompositionLocalContext
        val current = remember(host, key) { MediaSharedPresentation(entry, route, host.shared, onBack, context) }
        SideEffect {
            presentation = current
            current.context = context
        }
        DisposableEffect(current) {
            host.presentations.add(current)
            onDispose { host.presentations.remove(current) }
        }
    }

    override suspend fun onRemove() {
        presentation?.close()
    }

    override fun equals(other: Any?): Boolean =
        other is MediaSharedScene && host === other.host && key == other.key &&
            previousEntries.map { it.contentKey } == other.previousEntries.map { it.contentKey }

    override fun hashCode(): Int = 31 * key.hashCode() + previousEntries.map { it.contentKey }.hashCode()
}
