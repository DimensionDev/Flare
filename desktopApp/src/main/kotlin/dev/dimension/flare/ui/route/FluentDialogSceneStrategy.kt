package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.dimension.flare.ui.route.FluentDialogSceneStrategy.Companion.dialog
import io.github.composefluent.component.FluentDialog

/** An [OverlayScene] that renders an [entry] within a [Dialog]. */
internal class FluentDialogScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val popupProperties: PopupProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        FluentDialog(
            visible = true,
            properties = popupProperties,
        ) {
            entry.Content()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FluentDialogScene<*>

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            entry == other.entry &&
            popupProperties == other.popupProperties
    }

    override fun hashCode(): Int =
        key.hashCode() * 31 +
            previousEntries.hashCode() * 31 +
            overlaidEntries.hashCode() * 31 +
            entry.hashCode() * 31 +
            popupProperties.hashCode() * 31

    override fun toString(): String =
        "DialogScene(key=$key, entry=$entry, previousEntries=$previousEntries, overlaidEntries=$overlaidEntries, popupProperties=$popupProperties)"
}

/**
 * A [SceneStrategy] that displays entries that have added [dialog] to their [NavEntry.metadata]
 * within a [Dialog] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
public class FluentDialogSceneStrategy<T : Any> : SceneStrategy<T> {
    public override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val popupProperties = lastEntry?.metadata?.get(DIALOG_KEY) as? PopupProperties
        return popupProperties?.let { properties ->
            FluentDialogScene(
                key = lastEntry.contentKey,
                entry = lastEntry,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                popupProperties = popupProperties,
                onBack = onBack,
            )
        }
    }

    public companion object {
        /**
         * Function to be called on the [NavEntry.metadata] to mark this entry as something that
         * should be displayed within a [Dialog].
         *
         * @param dialogProperties properties that should be passed to the containing [Dialog].
         */
        public fun dialog(popupProperties: PopupProperties = PopupProperties()): Map<String, Any> = mapOf(DIALOG_KEY to popupProperties)

        internal const val DIALOG_KEY = "fluent_dialog"
    }
}
