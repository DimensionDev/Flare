package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme

internal class MediaViewerMotion(
    scheme: MotionScheme,
) {
    // Keep the theme's full-screen spring speed, but media must never grow past its destination.
    private val stiffness = (scheme.slowSpatialSpec<Float>() as? SpringSpec)?.stiffness ?: Spring.StiffnessLow
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
    val background = scheme.slowEffectsSpec<Float>()
    val controls = scheme.defaultEffectsSpec<Float>()
}
