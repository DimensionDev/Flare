package dev.dimension.flare.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.time.TimeSource

/** UI-only identities and coordinates; never stored in routes or persisted across windows. */
public class MediaTransitionSources {
    public var coordinates: LayoutCoordinates? = null
    public var hiddenSource: Any? by mutableStateOf(null)
    private val sources = mutableMapOf<Any, Source>()
    private var pressed: Pair<Any, TimeSource.Monotonic.ValueTimeMark>? = null

    internal fun register(source: Source) {
        sources[source.id] = source
    }

    internal fun remove(id: Any) {
        sources.remove(id)
        if (pressed?.first == id) pressed = null
    }

    internal fun press(id: Any) {
        pressed = id to TimeSource.Monotonic.markNow()
    }

    public fun takeOpeningSource(urls: Set<String>): Snapshot? {
        val candidate = pressed
        pressed = null
        if (candidate == null || candidate.second.elapsedNow().inWholeMilliseconds > 1500) return null
        return sources[candidate.first]?.takeIf { it.url in urls || it.previewUrl in urls }?.snapshot()
    }

    public fun returningSource(
        origin: Snapshot,
        urls: Set<String>,
    ): Snapshot? {
        val candidates = sources.values.filter { it.group == origin.group && (it.url in urls || it.previewUrl in urls) }
        val source = candidates.firstOrNull { it.id == origin.id } ?: candidates.singleOrNull()
        return source?.snapshot()
    }

    private fun Source.snapshot(): Snapshot? {
        val root = this@MediaTransitionSources.coordinates?.takeIf { it.isAttached } ?: return null
        val child = coordinates?.takeIf { it.isAttached } ?: return null
        if (root.findRootCoordinates() !== child.findRootCoordinates()) return null
        val bounds = root.localBoundingBoxOf(child, clipBounds = false)
        val visible =
            root
                .localBoundingBoxOf(child, clipBounds = true)
                .intersect(Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat()))
        if (bounds.isEmpty || visible.isEmpty) return null
        return Snapshot(id, group, url, previewUrl, bounds, visible, contentScale, alignment, shape, headers)
    }

    internal class Source(
        val id: Any,
        val group: Any,
        val url: String,
        val previewUrl: String,
        val contentScale: ContentScale,
        val alignment: Alignment,
        val shape: Shape,
        val headers: Map<String, String>?,
    ) {
        var coordinates: LayoutCoordinates? = null
    }

    public data class Snapshot(
        val id: Any,
        val group: Any,
        val url: String,
        val previewUrl: String,
        val bounds: Rect,
        val visibleBounds: Rect,
        val contentScale: ContentScale,
        val alignment: Alignment,
        val shape: Shape,
        val headers: Map<String, String>?,
    )
}

public val LocalMediaTransitionSources: ProvidableCompositionLocal<MediaTransitionSources?> = compositionLocalOf { null }
public val LocalMediaTransitionGroup: ProvidableCompositionLocal<Any?> = compositionLocalOf { null }
public val LocalMediaTransitionSourceEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }
public val LocalMediaTransitionShape: ProvidableCompositionLocal<Shape> = compositionLocalOf { RectangleShape }

@Composable
public fun Modifier.mediaTransitionSource(
    url: String?,
    previewUrl: String? = url,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    shape: Shape = LocalMediaTransitionShape.current,
    headers: Map<String, String>? = null,
): Modifier {
    val sources = LocalMediaTransitionSources.current
    if (sources == null || !LocalMediaTransitionSourceEnabled.current || url == null || previewUrl == null) return this
    val id = remember { Any() }
    val group = LocalMediaTransitionGroup.current ?: id
    val source =
        remember(id, group, url, previewUrl, contentScale, alignment, shape, headers) {
            MediaTransitionSources.Source(id, group, url, previewUrl, contentScale, alignment, shape, headers)
        }
    DisposableEffect(sources, source) {
        sources.register(source)
        onDispose { sources.remove(id) }
    }
    return this
        .onGloballyPositioned { source.coordinates = it }
        .graphicsLayer { alpha = if (sources.hiddenSource == id) 0f else 1f }
        .pointerInput(sources, id) {
            // Observe without consuming: parent click, long-press, and zoom handlers retain ownership.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                sources.press(id)
            }
        }
}
