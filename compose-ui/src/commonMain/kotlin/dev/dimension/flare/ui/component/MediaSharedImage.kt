package dev.dimension.flare.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.compose.rememberAsyncImagePainter
import coil3.memory.MemoryCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.collections.immutable.ImmutableMap
import kotlin.math.roundToInt

/** Reuse the source pixels and intrinsic size from the first frame of the transition. */
@Composable
public fun rememberMediaSharedImagePainter(
    preview: String,
    customHeaders: ImmutableMap<String, String>?,
): Painter {
    val context = LocalPlatformContext.current
    val cached =
        remember(preview, context) {
            SingletonImageLoader
                .get(context)
                .memoryCache
                ?.get(MemoryCache.Key(preview))
                ?.image
        }
    if (cached != null) return remember(cached, context) { cached.asPainter(context) }
    return rememberAsyncImagePainter(
        model =
            remember(preview, customHeaders, context) {
                ImageRequest
                    .Builder(context)
                    .data(preview)
                    .memoryCacheKey(preview)
                    .size(Size.ORIGINAL)
                    .crossfade(false)
                    .apply {
                        if (customHeaders != null) {
                            httpHeaders(
                                NetworkHeaders
                                    .Builder()
                                    .apply {
                                        customHeaders.forEach { (key, value) -> set(key, value) }
                                    }.build(),
                            )
                        }
                    }.build()
            },
    )
}

public val Painter.mediaAspectRatio: Float?
    get() = intrinsicSize.takeIf { it.isSpecified && it.width > 0f && it.height > 0f }?.let { it.width / it.height }

/** Share the image bounds, leaving the zoom/pan viewport at its final size. */
@Composable
public fun MediaSharedImage(
    preview: String,
    painter: Painter,
    visible: Boolean,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = painter.mediaAspectRatio,
    fillWidth: Boolean = false,
) {
    Image(
        painter = painter,
        contentDescription = null,
        // Both ends use Crop. Fit/FillWidth is expressed by the destination bounds,
        // since sharedElement doesn't interpolate ContentScale between endpoints.
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val viewportWidth = constraints.maxWidth
                    val viewportHeight = constraints.maxHeight
                    val ratio =
                        aspectRatio?.takeIf { it.isFinite() && it > 0f }
                            ?: (viewportWidth.toFloat() / viewportHeight)
                    val width = if (fillWidth) viewportWidth else minOf(viewportWidth, (viewportHeight * ratio).roundToInt())
                    val height = (width / ratio).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(width.coerceAtLeast(1), height))
                    layout(viewportWidth, viewportHeight) {
                        placeable.placeRelative((viewportWidth - width) / 2, if (fillWidth) 0 else (viewportHeight - height) / 2)
                    }
                }.mediaSharedElementDestination(preview)
                .drawWithContent { if (visible) drawContent() },
    )
}
