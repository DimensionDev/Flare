package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.theme.MediumAlpha

@Composable
internal fun CommonStatusHeaderComponent(
    data: UiUserV2,
    onUserClick: (MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarComponent(
            data = data.avatar,
            modifier =
                Modifier
                    .clickable {
                        onUserClick(data.key)
                    },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f),
        ) {
            HtmlText(
                element = data.name.data,
                modifier =
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onUserClick(data.key)
                        },
                maxLines = 1,
            )
            Text(
                text = data.handle,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .alpha(MediumAlpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onUserClick(data.key)
                        },
            )
        }
        trailing.invoke(this)
    }
}
