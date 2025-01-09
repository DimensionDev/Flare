package dev.dimension.flare.ui.component.platform

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import com.konyaco.fluent.FluentTheme
import io.github.fornewid.placeholder.foundation.PlaceholderHighlight

@Composable
private fun placeHolderColor(): Color =
    FluentTheme.colors.text.text.primary
        .copy(alpha = 0.1f)
        .compositeOver(FluentTheme.colors.background.card.secondary)

internal actual fun Modifier.placeholder(
    visible: Boolean,
    color: Color,
    shape: Shape?,
    highlight: PlaceholderHighlight?,
    placeholderFadeTransitionSpec: @Composable Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
    contentFadeTransitionSpec: @Composable Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
): Modifier =
    composed {
        Modifier.placeholder(
            visible = visible,
            color = if (color.isSpecified) color else placeHolderColor(),
            shape = shape ?: FluentTheme.shapes.control,
            highlight = highlight,
            placeholderFadeTransitionSpec = placeholderFadeTransitionSpec,
            contentFadeTransitionSpec = contentFadeTransitionSpec,
        )
    }

public fun Modifier.placeholder(visible: Boolean): Modifier = placeholder(visible, Color.Unspecified)
