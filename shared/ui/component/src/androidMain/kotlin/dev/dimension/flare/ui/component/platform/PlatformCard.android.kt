package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
internal actual fun PlatformCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    shape: Shape?,
    elevated: Boolean,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Card(
            modifier = modifier,
            shape = shape ?: androidx.compose.material3.CardDefaults.shape,
            colors =
                if (elevated) {
                    androidx.compose.material3.CardDefaults
                        .elevatedCardColors()
                } else {
                    androidx.compose.material3.CardDefaults
                        .cardColors()
                },
            elevation =
                if (elevated) {
                    androidx.compose.material3.CardDefaults
                        .elevatedCardElevation()
                } else {
                    androidx.compose.material3.CardDefaults
                        .cardElevation()
                },
            content = { content() },
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape ?: androidx.compose.material3.CardDefaults.shape,
            colors =
                if (elevated) {
                    androidx.compose.material3.CardDefaults
                        .elevatedCardColors()
                } else {
                    androidx.compose.material3.CardDefaults
                        .cardColors()
                },
            elevation =
                if (elevated) {
                    androidx.compose.material3.CardDefaults
                        .elevatedCardElevation()
                } else {
                    androidx.compose.material3.CardDefaults
                        .cardElevation()
                },
            content = { content() },
            onClick = { onClick.invoke() },
        )
    }
}
