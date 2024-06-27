package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings

@Composable
fun AvatarComponent(
    data: String?,
    modifier: Modifier = Modifier,
    beforeModifier: Modifier = Modifier,
    size: Dp = AvatarComponentDefaults.size,
) {
    val appearanceSettings = LocalAppearanceSettings.current
    NetworkImage(
        model = data,
        contentDescription = null,
        modifier =
            Modifier
                .then(beforeModifier)
                .size(size)
                .clip(
                    when (appearanceSettings.avatarShape) {
                        AvatarShape.CIRCLE ->
                            CircleShape
                        AvatarShape.SQUARE ->
                            RoundedCornerShape(4.dp)
                    },
                ).then(modifier),
    )
}

object AvatarComponentDefaults {
    val size = 44.dp
    val compatSize = 20.dp
}
