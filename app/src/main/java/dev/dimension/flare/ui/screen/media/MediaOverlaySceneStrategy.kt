package dev.dimension.flare.ui.screen.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.component.LocalMediaTransitionSourceEnabled
import dev.dimension.flare.ui.component.MediaTransitionSources
import dev.dimension.flare.ui.route.Route

/** Keeps navigation ownership in its original Router, but presents media above the app scaffold. */
internal class MediaOverlaySceneStrategy(
    private val host: MediaViewerOverlayHostState,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        val route = entry.metadata[MEDIA_OVERLAY_ROUTE] as? Route.Media ?: return null
        val previous = entries.dropLast(1)
        val underlying = previous.lastOrNull() ?: return null
        // An activity layer cannot cover another dialog window, so these entries use a
        // dialog of their own with a fade and deliberately have no Hero source.
        val inDialog = dialogMetadataKey in underlying.metadata || BottomSheetSceneStrategy.isBottomSheetEntry(underlying)
        return MediaOverlayScene(entry, route, previous, host, onBack, inDialog)
    }
}

internal fun Any?.isMediaViewerRoute(): Boolean =
    this is Route.Media.Image || this is Route.Media.RawMedia || this is Route.Media.StatusMedia

private val dialogMetadataKey = DialogSceneStrategy.dialog().keys.single()
private const val MEDIA_OVERLAY_ROUTE = "media_overlay_route"

internal fun mediaOverlayMetadata(route: Route.Media): Map<String, Any> =
    DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) +
        (MEDIA_OVERLAY_ROUTE to route)

internal class MediaOverlayPresentation(
    val state: MediaViewerOverlayState,
    val entry: NavEntry<NavKey>,
    context: CompositionLocalContext,
) {
    var context by mutableStateOf(context)
}

private class MediaOverlayScene(
    private val entry: NavEntry<NavKey>,
    private val route: Route.Media,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val host: MediaViewerOverlayHostState,
    private val onBack: () -> Unit,
    private val inDialog: Boolean,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries
    private var state: MediaViewerOverlayState? = null

    override val content: @Composable () -> Unit = {
        if (inDialog) {
            val dialogState = remember(host, key) { MediaViewerOverlayState(MediaTransitionSources(), route, onBack) }
            SideEffect { state = dialogState }
            DisposableEffect(dialogState) { onDispose { dialogState.dispose() } }
            Dialog(
                onDismissRequest = dialogState::requestDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                CompositionLocalProvider(
                    LocalMediaViewerOverlay provides dialogState,
                    LocalMediaTransitionSourceEnabled provides false,
                ) {
                    MediaOverlayLayer(dialogState) { entry.Content() }
                }
            }
        } else {
            val context = currentCompositionLocalContext
            val presentation =
                remember(host, key) {
                    MediaOverlayPresentation(
                        state = MediaViewerOverlayState(host.sources, route, onBack),
                        entry = entry,
                        context = context,
                    )
                }
            SideEffect {
                state = presentation.state
                presentation.context = context
            }
            DisposableEffect(presentation) {
                host.presentations.add(presentation)
                onDispose {
                    host.presentations.remove(presentation)
                    presentation.state.dispose()
                }
            }
        }
    }

    override suspend fun onRemove() {
        state?.close()
    }

    override fun equals(other: Any?): Boolean =
        other is MediaOverlayScene && host === other.host && key == other.key && inDialog == other.inDialog &&
            previousEntries.map { it.contentKey } == other.previousEntries.map { it.contentKey }

    override fun hashCode(): Int = 31 * key.hashCode() + previousEntries.map { it.contentKey }.hashCode()
}
