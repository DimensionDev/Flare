package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun CommonStatusHeaderComponent(
    data: UiUserV2,
    onUserClick: (MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    ListComponent(
        leadingContent = {
            AvatarComponent(
                data = data.avatar,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            onUserClick(data.key)
                        },
            )
        },
        headlineContent = {
            RichText(
                text = data.name,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onUserClick(data.key)
                        },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            PlatformText(
                text = data.handle,
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onUserClick(data.key)
                        },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = trailing,
        modifier = modifier,
    )
}

@Composable
public fun ListComponent(
    headlineContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable RowScope.() -> Unit = {},
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent.invoke(this)
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f),
        ) {
            headlineContent.invoke(this)
            supportingContent.invoke(this)
        }
        trailingContent.invoke(this)
    }
}
