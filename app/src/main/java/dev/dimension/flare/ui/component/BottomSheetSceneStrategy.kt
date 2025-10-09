package dev.dimension.flare.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

internal class BottomSheetScene<T : Any>
    @OptIn(ExperimentalMaterial3Api::class)
    constructor(
        override val key: Any,
        override val previousEntries: List<NavEntry<T>>,
        override val overlaidEntries: List<NavEntry<T>>,
        private val properties: ModalBottomSheetProperties,
        private val entry: NavEntry<T>,
        private val onBack: () -> Unit,
    ) : OverlayScene<T> {
        override val entries: List<NavEntry<T>> = listOf(entry)

        @OptIn(ExperimentalMaterial3Api::class)
        override val content: @Composable (() -> Unit) = {
            ModalBottomSheet(
                onDismissRequest = { onBack() },
                properties = properties,
            ) {
                entry.Content()
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val properties = lastEntry?.metadata?.get(BOTTOMSHEET_KEY) as? ModalBottomSheetProperties
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

        fun bottomSheet(properties: ModalBottomSheetProperties = ModalBottomSheetProperties()): Map<String, Any> =
            mapOf(BOTTOMSHEET_KEY to properties)
    }
}
