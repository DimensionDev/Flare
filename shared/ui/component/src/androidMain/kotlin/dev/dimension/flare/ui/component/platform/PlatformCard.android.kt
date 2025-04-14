package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@Composable
internal actual fun PlatformCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    shape: Shape?,
    elevated: Boolean,
    containerColor: Color?,
    content: @Composable () -> Unit,
) {
    val colorColors =
        if (containerColor != null) {
            CardDefaults
                .cardColors(
                    containerColor = containerColor,
                )
        } else {
            CardDefaults
                .cardColors()
        }
    val colorElevation =
        if (containerColor != null) {
            CardDefaults
                .elevatedCardColors(
                    containerColor = containerColor,
                )
        } else {
            CardDefaults
                .elevatedCardColors()
        }
    val colors =
        if (elevated) {
            colorElevation
        } else {
            colorColors
        }
    if (onClick == null) {
        Card(
            modifier = modifier,
            shape = shape ?: CardDefaults.shape,
            colors = colors,
            elevation =
                if (elevated) {
                    CardDefaults
                        .elevatedCardElevation()
                } else {
                    CardDefaults
                        .cardElevation()
                },
            content = { content() },
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape ?: CardDefaults.shape,
            colors = colors,
            elevation =
                if (elevated) {
                    CardDefaults
                        .elevatedCardElevation()
                } else {
                    CardDefaults
                        .cardElevation()
                },
            content = { content() },
            onClick = { onClick.invoke() },
        )
    }
}
