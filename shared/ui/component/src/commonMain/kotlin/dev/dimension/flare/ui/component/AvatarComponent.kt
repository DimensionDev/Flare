package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
public fun AvatarComponent(
    data: String?,
    modifier: Modifier = Modifier,
    size: Dp = AvatarComponentDefaults.size,
) {
    val appearanceSettings = LocalComponentAppearance.current
    NetworkImage(
        model = data,
        contentDescription = null,
        modifier =
            Modifier
                .size(size)
                .clip(
                    when (appearanceSettings.avatarShape) {
                        ComponentAppearance.AvatarShape.CIRCLE ->
                            CircleShape
                        ComponentAppearance.AvatarShape.SQUARE ->
                            RoundedCornerShape(4.dp)
                    },
                ).then(modifier),
    )
}

public object AvatarComponentDefaults {
    public val size: Dp = 44.dp
    public val compatSize: Dp = 20.dp
}
