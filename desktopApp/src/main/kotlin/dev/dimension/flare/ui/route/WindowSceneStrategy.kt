package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.window.Window
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.dimension.flare.Res
import dev.dimension.flare.app_name
import dev.dimension.flare.flare_logo
import dev.dimension.flare.ui.route.WindowSceneStrategy.Companion.window
import dev.dimension.flare.ui.theme.FlareTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** An [OverlayScene] that renders an [entry] within a [Window]. */
internal class WindowScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        Window(
            onCloseRequest = onBack,
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.flare_logo),
            onKeyEvent = {
                if (it.key == Key.Escape) {
                    onBack.invoke()
                    true
                } else {
                    false
                }
            },
        ) {
            FlareTheme {
                entry.Content()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WindowScene<*>

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            entry == other.entry
    }

    override fun hashCode(): Int =
        key.hashCode() * 31 +
            previousEntries.hashCode() * 31 +
            overlaidEntries.hashCode() * 31 +
            entry.hashCode() * 31

    override fun toString(): String =
        "WindowScene(key=$key, entry=$entry, previousEntries=$previousEntries, overlaidEntries=$overlaidEntries)"
}

/**
 * A [SceneStrategy] that displays entries that have added [window] to their [NavEntry.metadata]
 * within a [Window] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
public class WindowSceneStrategy<T : Any> : SceneStrategy<T> {
    public override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val isWindow = lastEntry?.metadata?.get(WINDOW_KEY) as? Boolean
        return if (isWindow == true) {
            WindowScene(
                key = lastEntry.contentKey,
                entry = lastEntry,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                onBack = onBack,
            )
        } else {
            null
        }
    }

    public companion object {
        /**
         * Function to be called on the [NavEntry.metadata] to mark this entry as something that
         * should be displayed within a [Window].
         */
        public fun window(): Map<String, Any> = mapOf(WINDOW_KEY to true)

        const val WINDOW_KEY = "compose_window"
    }
}
