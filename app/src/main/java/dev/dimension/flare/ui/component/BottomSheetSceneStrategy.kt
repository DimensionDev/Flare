package dev.dimension.flare.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

@OptIn(ExperimentalMaterial3Api::class)
private class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val properties: BottomSheetProperties,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    lateinit var sheetState: SheetState

    override val content: @Composable (() -> Unit) = {
        sheetState =
            rememberBottomSheetState(
                initialValue =
                    if (properties.expandFully) SheetValue.Expanded else SheetValue.PartiallyExpanded,
                enabledValues =
                    if (properties.expandFully) {
                        setOf(SheetValue.Hidden, SheetValue.Expanded)
                    } else {
                        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
                    },
            )
        ModalBottomSheet(
            onDismissRequest = { onBack() },
            properties = properties.properties,
            sheetState = sheetState,
        ) {
            entry.Content()
        }
    }

    override suspend fun onRemove() {
        sheetState.hide()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BottomSheetScene<*>

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
}

@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val properties = lastEntry?.metadata?.get(BOTTOMSHEET_KEY) as? BottomSheetProperties
        return properties?.let { properties ->
            BottomSheetScene(
                key = lastEntry.contentKey,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                entry = lastEntry,
                properties = properties,
                onBack = onBack,
            )
        }
    }

    companion object {
        private const val BOTTOMSHEET_KEY = "bottom_sheet"

        fun bottomSheet(
            properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
            expandFully: Boolean = false,
        ): Map<String, Any> =
            mapOf(
                BOTTOMSHEET_KEY to
                    BottomSheetProperties(
                        properties = properties,
                        expandFully = expandFully,
                    ),
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private data class BottomSheetProperties(
    val properties: ModalBottomSheetProperties,
    val expandFully: Boolean,
)
