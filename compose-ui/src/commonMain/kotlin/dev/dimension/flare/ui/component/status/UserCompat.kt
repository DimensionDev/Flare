package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal fun UserCompat(
    user: UiUserV2,
    modifier: Modifier = Modifier,
    onUserClick: (MicroBlogKey) -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    with(user) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarComponent(
                data = avatar,
                size = AvatarComponentDefaults.compatSize,
                modifier =
                    Modifier.clickable {
                        onUserClick.invoke(user.key)
                    },
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RichText(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable {
                            onUserClick.invoke(user.key)
                        },
                )
                PlatformText(
                    text = handle,
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable {
                            onUserClick.invoke(user.key)
                        },
                )
            }
            trailing.invoke(this)
        }
    }
}
