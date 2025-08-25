package dev.dimension.flare.ui.component.platform

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import io.github.fornewid.placeholder.foundation.PlaceholderHighlight
import io.github.fornewid.placeholder.material3.placeholder

internal actual fun Modifier.placeholder(
    visible: Boolean,
    color: Color,
    shape: Shape?,
    highlight: PlaceholderHighlight?,
    placeholderFadeTransitionSpec: @Composable Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
    contentFadeTransitionSpec: @Composable Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
): Modifier =
    placeholder(
        visible = visible,
        color = color,
        shape = shape,
        highlight = highlight,
        placeholderFadeTransitionSpec = placeholderFadeTransitionSpec,
        contentFadeTransitionSpec = contentFadeTransitionSpec,
    )
