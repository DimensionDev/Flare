package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.theme.MediumAlpha

@Composable
internal fun CommonStatusHeaderComponent(
    data: UiUser,
    onUserClick: (MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarComponent(
            data = data.avatarUrl,
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    onUserClick(data.userKey)
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            HtmlText(
                element = data.nameElement,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onUserClick(data.userKey)
                    }
            )
            Text(
                text = data.handle,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .alpha(MediumAlpha)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onUserClick(data.userKey)
                    }
            )
        }
        trailing.invoke(this)
    }
}
