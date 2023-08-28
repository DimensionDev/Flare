package dev.dimension.flare.ui.component.placeholder

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified

/**
 * Returns the value used as the the `color` parameter value on [Modifier.placeholder].
 *
 * @param backgroundColor The current background color of the layout. Defaults to
 * `MaterialTheme.colorScheme.surface`.
 * @param contentColor The content color to be used on top of [backgroundColor].
 * @param contentAlpha The alpha component to set on [contentColor] when compositing the color
 * on top of [backgroundColor]. Defaults to `0.1f`.
 */
@Composable
public fun PlaceholderDefaults.color(
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    contentAlpha: Float = 0.1f,
): Color = contentColor.copy(contentAlpha).compositeOver(backgroundColor)

/**
 * Returns the value used as the the `highlightColor` parameter value of
 * [PlaceholderHighlight.Companion.fade].
 *
 * @param backgroundColor The current background color of the layout. Defaults to
 * `MaterialTheme.colorScheme.surface`.
 * @param alpha The alpha component to set on [backgroundColor]. Defaults to `0.3f`.
 */
@Composable
public fun PlaceholderDefaults.fadeHighlightColor(
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.3f,
): Color = backgroundColor.copy(alpha = alpha)

/**
 * Returns the value used as the the `highlightColor` parameter value of
 * [PlaceholderHighlight.Companion.shimmer].
 *
 * @param backgroundColor The current background color of the layout. Defaults to
 * `MaterialTheme.colorScheme.inverseSurface`.
 * @param alpha The alpha component to set on [backgroundColor]. Defaults to `0.75f`.
 */
@Composable
public fun PlaceholderDefaults.shimmerHighlightColor(
    backgroundColor: Color = MaterialTheme.colorScheme.inverseSurface,
    alpha: Float = 0.75f,
): Color {
    return backgroundColor.copy(alpha = alpha)
}

/**
 * Draws some skeleton UI which is typically used whilst content is 'loading'.
 *
 * To customize the color and shape of the placeholder, you can use the foundation version of
 * [Modifier.placeholder], along with the values provided by [PlaceholderDefaults].
 *
 * A cross-fade transition will be applied to the content and placeholder UI when the [visible]
 * value changes. The transition can be customized via the [contentFadeTransitionSpec] and
 * [placeholderFadeTransitionSpec] parameters.
 *
 * You can provide a [PlaceholderHighlight] which runs an highlight animation on the placeholder.
 * The [shimmer] and [fade] implementations are provided for easy usage.
 *
 * You can find more information on the pattern at the Material Theming
 * [Placeholder UI](https://material.io/design/communication/launch-screen.html#placeholder-ui)
 * guidelines.
 *
 * @sample com.google.accompanist.sample.placeholder.DocSample_Material_Placeholder
 *
 * @param visible whether the placeholder should be visible or not.
 * @param color the color used to draw the placeholder UI. If [Color.Unspecified] is provided,
 * the placeholder will use [PlaceholderDefaults.color].
 * @param shape desired shape of the placeholder. If null is provided the placeholder
 * will use the small shape set in [MaterialTheme.shapes].
 * @param highlight optional highlight animation.
 * @param placeholderFadeTransitionSpec The transition spec to use when fading the placeholder
 * on/off screen. The boolean parameter defined for the transition is [visible].
 * @param contentFadeTransitionSpec The transition spec to use when fading the content
 * on/off screen. The boolean parameter defined for the transition is [visible].
 */
public fun Modifier.placeholder(
    visible: Boolean,
    color: Color = Color.Unspecified,
    shape: Shape? = null,
    highlight: PlaceholderHighlight? = null,
    placeholderFadeTransitionSpec: @Composable Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float> = { spring() },
    contentFadeTransitionSpec: @Composable Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float> = { spring() },
): Modifier = composed {
    Modifier.placeholder(
        visible = visible,
        color = if (color.isSpecified) color else PlaceholderDefaults.color(),
        shape = shape ?: MaterialTheme.shapes.small,
        highlight = highlight
            ?: PlaceholderHighlight.fade(PlaceholderDefaults.fadeHighlightColor()),
        placeholderFadeTransitionSpec = placeholderFadeTransitionSpec,
        contentFadeTransitionSpec = contentFadeTransitionSpec,
    )
}
