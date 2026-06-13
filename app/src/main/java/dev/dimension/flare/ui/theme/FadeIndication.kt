package dev.dimension.flare.ui.theme

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class FadeIndication(
    private val color: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FadeIndicationNode(
            interactionSource = interactionSource,
            color = color,
        )

    override fun hashCode(): Int = color.hashCode()

    override fun equals(other: Any?): Boolean = other is FadeIndication && other.color == color

    private class FadeIndicationNode(
        private val interactionSource: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(),
        DrawModifierNode {
        private var pressCount = 0
        private var alpha = 0f
        private var animationJob: Job? = null

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressCount++
                        is PressInteraction.Release -> pressCount = (pressCount - 1).coerceAtLeast(0)
                        is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                    }
                    animateAlpha(if (pressCount > 0) 1f else 0f)
                }
            }
        }

        private fun animateAlpha(targetAlpha: Float) {
            if (alpha == targetAlpha) return

            animationJob?.cancel()
            animationJob =
                coroutineScope.launch {
                    animate(
                        initialValue = alpha,
                        targetValue = targetAlpha,
                        animationSpec =
                            tween(
                                durationMillis = if (targetAlpha > alpha) 100 else 200,
                            ),
                    ) { value, _ ->
                        alpha = value
                        invalidateDraw()
                    }
                }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (alpha > 0f) {
                drawRect(
                    color = color.copy(alpha = color.alpha * alpha),
                    size = size,
                )
            }
        }
    }
}
