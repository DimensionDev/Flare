package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.dimension.flare.ui.component.DialogSceneStrategy2.Companion.dialog

/** An [OverlayScene] that renders an [entry] within a [Dialog]. */
internal class DialogScene2<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val dialogProperties: DialogProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = rememberLifecycleOwner()
        Dialog(onDismissRequest = onBack, properties = dialogProperties) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DialogScene2<*>

        // Compare by contentKey values rather than NavEntry object identity (content ===).
        // NavEntry wrappers are recreated with new closure instances on every recomposition
        // (the lambda is passed to a constructor, not a @Composable function, so it is never
        // a stable ComposableLambda). Using object identity would cause false inequality,
        // causing LaunchedEffect(overlayScenes) to add a new scene on every recomposition
        // while the old one is still animating → duplicate ModalBottomSheet dialogs sharing
        // the same SaveableStateProvider key → crash.
        return key == other.key &&
            previousEntries.map { it.contentKey } == other.previousEntries.map { it.contentKey } &&
            overlaidEntries.map { it.contentKey } == other.overlaidEntries.map { it.contentKey } &&
            entry.contentKey == other.entry.contentKey
    }

    override fun hashCode(): Int =
        key.hashCode() * 31 +
            previousEntries.map { it.contentKey }.hashCode() * 31 +
            overlaidEntries.map { it.contentKey }.hashCode() * 31 +
            entry.contentKey.hashCode()

    override fun toString(): String =
        "DialogScene(key=$key, entry=$entry, previousEntries=$previousEntries, overlaidEntries=$overlaidEntries, dialogProperties=$dialogProperties)"
}

/**
 * A [SceneStrategy] that displays entries that have added [dialog] to their [NavEntry.metadata]
 * within a [Dialog] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
public class DialogSceneStrategy2<T : Any> : SceneStrategy<T> {
    public override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val dialogProperties = lastEntry?.metadata?.get(DialogKey)
        return dialogProperties?.let { properties ->
            DialogScene2(
                key = lastEntry.contentKey,
                entry = lastEntry,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                dialogProperties = properties,
                onBack = onBack,
            )
        }
    }

    public companion object {
        /**
         * The key for [NavEntry.metadata] or [Scene.metadata] to indicate that an entry should be
         * displayed within a [Dialog].
         *
         * @sample androidx.navigation3.ui.samples.DialogSample
         */
        public object DialogKey : NavMetadataKey<DialogProperties>

        /**
         * Function to be called on the [NavEntry.metadata] to mark this entry as something that
         * should be displayed within a [Dialog].
         *
         * @param dialogProperties properties that should be passed to the containing [Dialog].
         */
        public fun dialog(dialogProperties: DialogProperties = DialogProperties()): Map<String, Any> =
            metadata { put(DialogKey, dialogProperties) }
    }
}
