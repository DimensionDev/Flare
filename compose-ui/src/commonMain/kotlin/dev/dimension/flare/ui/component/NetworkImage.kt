package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
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
import coil3.compose.SubcomposeAsyncImage
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.theme.PlatformTheme

public val LocalNetworkImageAllowHardware: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { true }

@Composable
public fun NetworkImage(
    model: UiMedia?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    placeholder: Painter? = null,
) {
    val placeholderColor = PlatformTheme.colorScheme.outline
    AsyncImage(
        model = rememberMediaImageRequest(model),
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
    model: UiMedia?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
) {
    SubcomposeAsyncImage(
        model = rememberMediaImageRequest(model),
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
    media: UiMedia,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = rememberMediaImageRequest(media, crossfade = false),
        modifier = modifier,
        contentDescription = null,
    )
}
