package dev.dimension.flare.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.OverlayScene
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.SceneStrategy
import soup.compose.material.motion.animation.materialSharedAxisZIn
import soup.compose.material.motion.animation.materialSharedAxisZOut

internal class FullScreenSceneStrategy<T : Any> : SceneStrategy<T> {
    @Composable
    override fun calculateScene(
        entries: List<NavEntry<T>>,
        onBack: (Int) -> Unit,
    ): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val properties = lastEntry?.metadata?.get(KEY) as? Properties
        return properties?.let { properties ->
            FullScreenScene(
                key = lastEntry.key,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                entry = lastEntry,
                onBack = onBack,
            )
        }
    }

    internal data object Properties

    companion object {
        private const val KEY = "full_screen"

        fun fullScreen() = mapOf(KEY to Properties)
    }
}

internal class FullScreenScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: (count: Int) -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        var visibleState = remember { SeekableTransitionState(false) }
        LaunchedEffect(Unit) {
            visibleState.animateTo(true)
        }

        val transition = rememberTransition(visibleState)
        transition.AnimatedVisibility(
            visible = {
                it
            },
            modifier =
                Modifier
                    .zIndex(Float.MAX_VALUE),
            enter = materialSharedAxisZIn(true),
            exit = materialSharedAxisZOut(false),
        ) {
            entry.content(key)
        }
        PredictiveBackHandler {
            try {
                it.collect {
                    visibleState.seekTo(it.progress, false)
                }
                visibleState.animateTo(false)
                onBack.invoke(1)
            } catch (e: Exception) {
                visibleState.animateTo(true)
            }
        }
    }
}
