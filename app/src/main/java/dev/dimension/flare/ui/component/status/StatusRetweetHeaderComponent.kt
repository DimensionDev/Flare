package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.theme.MediumAlpha

@Composable
internal fun StatusRetweetHeaderComponent(
    icon: ImageVector,
    user: UiUserV2?,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .alpha(MediumAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FAIcon(
            icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(16.dp),
        )
        if (user != null) {
            Spacer(modifier = Modifier.width(8.dp))
            HtmlText(
                element = user.name.data,
                layoutDirection = LocalLayoutDirection.current,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .alignByBaseline()
                        .weight(1f, fill = false),
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier.alignByBaseline(),
        )
    }
}
