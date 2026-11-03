package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal fun MediaVideoBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .mediaBadgePadding(horizontal = 16.dp, vertical = 16.dp)
                .clip(PlatformTheme.shapes.medium)
                .background(Color.Black.copy(alpha = 0.5f))
                .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
                .mediaBadgePadding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

internal fun Modifier.mediaBadgePadding(
    horizontal: Dp,
    vertical: Dp,
): Modifier =
    layout { measurable, constraints ->
        // Reserve space for content before padding can consume a narrow preview.
        val content = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val horizontalPadding = minOf(horizontal.roundToPx(), (constraints.maxWidth - content.width) / 2)
        val verticalPadding = minOf(vertical.roundToPx(), (constraints.maxHeight - content.height) / 2)
        layout(
            constraints.constrainWidth(content.width + horizontalPadding * 2),
            constraints.constrainHeight(content.height + verticalPadding * 2),
        ) {
            content.placeRelative(horizontalPadding, verticalPadding)
        }
    }
