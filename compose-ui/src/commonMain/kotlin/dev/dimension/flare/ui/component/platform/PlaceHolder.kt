package dev.dimension.flare.ui.component.platform

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.revenuecat.placeholder.PlaceholderDefaults
import com.revenuecat.placeholder.PlaceholderHighlight
import com.revenuecat.placeholder.placeholder
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal fun Modifier.placeholder(
    visible: Boolean,
    color: Color = Color.Gray.copy(alpha = 0.35f),
    shape: Shape = PlatformTheme.shapes.medium,
    highlight: PlaceholderHighlight? = PlaceholderDefaults.fade,
    placeholderFadeTransitionSpec: () -> FiniteAnimationSpec<Float> = { spring() },
    contentFadeTransitionSpec: () -> FiniteAnimationSpec<Float> = { spring() },
): Modifier =
    this.placeholder(
        enabled = visible,
        color = color,
        shape = shape,
        highlight = highlight,
        placeholderFadeTransitionSpec = placeholderFadeTransitionSpec,
        contentFadeTransitionSpec = contentFadeTransitionSpec,
    )
