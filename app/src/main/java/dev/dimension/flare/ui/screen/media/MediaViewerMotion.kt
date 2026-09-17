package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme

internal class MediaViewerMotion(
    scheme: MotionScheme,
) {
    // Use the theme's default spatial stiffness without letting media grow past its destination.
    private val stiffness = (scheme.defaultSpatialSpec<Float>() as? SpringSpec)?.stiffness ?: Spring.StiffnessLow
    val spatial =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = stiffness,
            visibilityThreshold = 0.001f,
        )
    val drag =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = stiffness,
            visibilityThreshold = 0.5f,
        )
    val background = scheme.defaultEffectsSpec<Float>()
    val controls = scheme.defaultEffectsSpec<Float>()
}
