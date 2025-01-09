package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.ListItemSeparator

@Composable
internal actual fun PlatformIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
) {
    ListItemSeparator()
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
