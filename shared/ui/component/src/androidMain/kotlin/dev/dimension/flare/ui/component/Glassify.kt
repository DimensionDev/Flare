package dev.dimension.flare.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

public val LocalHazeState: ProvidableCompositionLocal<HazeState> = compositionLocalOf { HazeState() }

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
public fun Glassify(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 4.dp,
    shadowElevation: Dp = 4.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    hazeState: HazeState = LocalHazeState.current,
    hazeStyle: HazeStyle = HazeMaterials.regular(color),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource ?: MutableInteractionSource(),
    ) {
        Box(
            modifier =
                Modifier
                    .hazeEffect(
                        state = hazeState,
                        style = hazeStyle,
                    ),
            contentAlignment = contentAlignment,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
public fun Glassify(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 4.dp,
    shadowElevation: Dp = 4.dp,
    border: BorderStroke? = null,
    hazeState: HazeState = LocalHazeState.current,
    hazeStyle: HazeStyle = HazeMaterials.regular(color),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        Box(
            modifier =
                Modifier
                    .hazeEffect(
                        state = hazeState,
                        style = hazeStyle,
                    ),
            contentAlignment = contentAlignment,
        ) {
            content()
        }
    }
}
