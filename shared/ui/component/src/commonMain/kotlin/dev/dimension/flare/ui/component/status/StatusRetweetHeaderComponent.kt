package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal fun StatusRetweetHeaderComponent(
    icon: ImageVector,
    user: UiUserV2?,
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PlatformTheme.typography.caption,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FAIcon(
            icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(textStyle.fontSize.value.dp),
        )
        if (user != null) {
            Spacer(modifier = Modifier.width(8.dp))
            RichText(
                text = user.name,
                layoutDirection = LocalLayoutDirection.current,
                textStyle = textStyle,
                modifier =
                    Modifier
                        .alignByBaseline()
                        .weight(1f, fill = false),
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        PlatformText(
            text = text,
            style = textStyle,
            modifier =
                Modifier.alignByBaseline(),
        )
    }
}
