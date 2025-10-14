package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableMap

@Composable
public fun NetworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    placeholder: Painter? = null,
    customHeaders: ImmutableMap<String, String>? = null,
) {
    val platformContext = LocalPlatformContext.current
    val placeholderColor = PlatformTheme.colorScheme.outline
    AsyncImage(
        model =
            remember(model, platformContext, customHeaders) {
                ImageRequest
                    .Builder(platformContext)
                    .data(model)
                    .crossfade(true)
                    .let {
                        if (customHeaders != null) {
                            it.httpHeaders(
                                NetworkHeaders
                                    .Builder()
                                    .apply {
                                        customHeaders.forEach { (key, value) ->
                                            set(key, value)
                                        }
                                    }.build(),
                            )
                        } else {
                            it
                        }
                    }.let {
                        if (model is String) {
                            it.memoryCacheKey(model)
                        } else {
                            it
                        }
                    }.build()
            },
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        modifier = modifier,
        filterQuality = filterQuality,
        placeholder =
            placeholder
                ?: remember(placeholderColor) {
                    ColorPainter(placeholderColor)
                },
    )
}

@Composable
public fun SubcomposeNetworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    customHeaders: ImmutableMap<String, String>? = null,
) {
    val platformContext = LocalPlatformContext.current
    SubcomposeAsyncImage(
        model =
            remember(model, platformContext, customHeaders) {
                ImageRequest
                    .Builder(platformContext)
                    .data(model)
                    .crossfade(true)
                    .let {
                        if (customHeaders != null) {
                            it.httpHeaders(
                                NetworkHeaders
                                    .Builder()
                                    .apply {
                                        customHeaders.forEach { (key, value) ->
                                            set(key, value)
                                        }
                                    }.build(),
                            )
                        } else {
                            it
                        }
                    }.let {
                        if (model is String) {
                            it.memoryCacheKey(model)
                        } else {
                            it
                        }
                    }.build()
            },
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        modifier = modifier,
        filterQuality = filterQuality,
    )
}

@Composable
internal fun EmojiImage(
    uri: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = uri,
        modifier = modifier,
        contentDescription = null,
    )
}
