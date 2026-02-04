package dev.dimension.flare.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import com.eygraber.compose.placeholder.PlaceholderDefaults
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.fade
import com.eygraber.compose.placeholder.placeholder
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
@ReadOnlyComposable
public fun Modifier.placeholder(
    visible: Boolean,
    color: Color = Color.Unspecified,
    shape: Shape? = null,
    highlight: PlaceholderHighlight? =
        PlaceholderHighlight.fade(PlatformTheme.colorScheme.card.copy(alpha = 0.3f)),
    placeholderFadeAnimationSpec: AnimationSpec<Float> = spring(),
    contentFadeAnimationSpec: AnimationSpec<Float> = spring(),
): Modifier =
    placeholder(
        visible = visible,
        color = if (color.isSpecified) color else PlaceholderDefaults.color(),
        shape = shape ?: PlatformTheme.shapes.small,
        highlight = highlight,
        placeholderFadeAnimationSpec = placeholderFadeAnimationSpec,
        contentFadeAnimationSpec = contentFadeAnimationSpec,
    )

@Composable
@ReadOnlyComposable
public fun PlaceholderDefaults.color(
    backgroundColor: Color = PlatformTheme.colorScheme.card,
    contentColor: Color = PlatformTheme.colorScheme.onCard,
    contentAlpha: Float = 0.1f,
): Color = contentColor.copy(contentAlpha).compositeOver(backgroundColor)
