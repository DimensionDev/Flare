package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toolingGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.slapps.cupertino.LocalContentColor
import com.slapps.cupertino.theme.CupertinoTheme

@Composable
internal actual fun PlatformIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * A composable that draws an [ImageVector] as an icon.
 *
 * @param imageVector The [ImageVector] to draw.
 * @param contentDescription Text used by accessibility services to describe what this icon
 * represents. This should always be provided unless this icon is used for decorative purposes,
 * and does not represent a meaningful action that a user can take. This text should be
 * localized, such as by using [stringResource] or similar.
 * @param modifier Modifier to apply to this icon.
 * @param tint The tint to apply to the icon. If [Color.Unspecified] is provided, then no tint is
 * applied.
 */
@Composable
private fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = CupertinoTheme.colorScheme.label,
) {
    val painter = rememberVectorPainter(imageVector)
    Icon(painter, tint, contentDescription, modifier)
}

/**
 * A composable that draws a [Painter] using the current theme's icon color and alpha.
 *
 * This function is useful for displaying icons that are provided as a [Painter] object.
 * It automatically applies the correct color and alpha to the icon based on the current
 * [LocalContentColor] and [LocalContentAlpha].
 *
 * @param painter The [Painter] to draw.
 * @param tint The color to apply to the [painter]. If [Color.Unspecified] is provided, then no tint is
 *  applied.
 * @param contentDescription Text used by accessibility services to describe what this icon
 *  represents. This should always be provided unless this icon is used for decorative purposes,
 *  and does not represent a meaningful action that a user can take.
 * @param modifier Optional [Modifier] to be applied to the icon.
 */
@Composable
private fun Icon(
    painter: Painter,
    tint: Color = CupertinoTheme.colorScheme.label,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // TODO: b/149735981 semantics for content description
    val colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)
    val semantics =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                this.role = Role.Image
            }
        } else {
            Modifier
        }
    Box(
        modifier
            .toolingGraphicsLayer()
            .defaultSizeFor(painter)
            .paint(
                painter,
                colorFilter = colorFilter,
                contentScale = ContentScale.Fit,
            ).then(semantics),
    )
}

private fun Modifier.defaultSizeFor(painter: Painter) =
    this.then(
        if (painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isInfinite()) {
            DefaultIconSizeModifier
        } else {
            Modifier
        },
    )

private fun Size.isInfinite() = width.isInfinite() && height.isInfinite()

// Default icon size, for icons with no intrinsic size information
private val DefaultIconSizeModifier = Modifier.size(16.dp)
