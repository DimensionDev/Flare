package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isUnspecified
import io.github.composefluent.FluentTheme
import kotlinx.coroutines.launch

@Composable
internal actual fun rippleIndication(
    bounded: Boolean,
    radius: Dp,
    color: Color,
): Indication =
    RippleIndication(
        hover = if (color.isSpecified) color else FluentTheme.colors.subtleFill.secondary,
        pressed = if (color.isSpecified) color else FluentTheme.colors.subtleFill.tertiary,
        radius = radius,
    )

private class RippleIndication(
    private val hover: Color,
    private val pressed: Color,
    private val radius: Dp,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        DefaultDebugIndicationInstance(
            interactionSource = interactionSource,
            hover = hover,
            pressed = pressed,
            radius = radius,
        )

    override fun hashCode(): Int = hover.hashCode() + pressed.hashCode()

    override fun equals(other: Any?) = other === this

    private class DefaultDebugIndicationInstance(
        private val interactionSource: InteractionSource,
        private val hover: Color,
        private val pressed: Color,
        private val radius: Dp,
    ) : Modifier.Node(),
        DrawModifierNode {
        private var isPressed = false
        private var isHovered = false
        private var isFocused = false

        override fun onAttach() {
            coroutineScope.launch {
                var pressCount = 0
                var hoverCount = 0
                var focusCount = 0
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressCount++
                        is PressInteraction.Release -> pressCount--
                        is PressInteraction.Cancel -> pressCount--
                        is HoverInteraction.Enter -> hoverCount++
                        is HoverInteraction.Exit -> hoverCount--
                        is FocusInteraction.Focus -> focusCount++
                        is FocusInteraction.Unfocus -> focusCount--
                    }
                    val pressed = pressCount > 0
                    val hovered = hoverCount > 0
                    val focused = focusCount > 0
                    var invalidateNeeded = false
                    if (isPressed != pressed) {
                        isPressed = pressed
                        invalidateNeeded = true
                    }
                    if (isHovered != hovered) {
                        isHovered = hovered
                        invalidateNeeded = true
                    }
                    if (isFocused != focused) {
                        isFocused = focused
                        invalidateNeeded = true
                    }
                    if (invalidateNeeded) invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            val actualRadius = if (radius.isUnspecified) size.minDimension / 2 else radius.toPx()
            if (isPressed) {
                drawCircle(color = pressed, radius = actualRadius)
            } else if (isHovered || isFocused) {
                drawCircle(color = hover, radius = actualRadius)
            }
            drawContent()
        }
    }
}
