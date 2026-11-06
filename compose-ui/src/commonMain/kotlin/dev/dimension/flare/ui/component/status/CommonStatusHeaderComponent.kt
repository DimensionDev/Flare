package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.profile_open_user
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

@Composable
public fun CommonStatusHeaderComponent(
    data: UiProfile,
    onUserClick: ((MicroBlogKey) -> Unit)? = profileClick(data),
    modifier: Modifier = Modifier,
    avatarModifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    subtitle: String = data.handle.display,
    leadingContent: @Composable (RowScope.() -> Unit)? = {
        Box(avatarModifier, propagateMinConstraints = true) {
            AvatarComponent(
                data = data.avatar,
                contentDescription =
                    if (onUserClick != null) {
                        stringResource(Res.string.profile_open_user, data.handle.display)
                    } else {
                        null
                    },
                modifier =
                    if (onUserClick == null) {
                        Modifier
                    } else {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onUserClick(data.key) }
                    },
            )
        }
    },
    trailing: @Composable RowScope.() -> Unit = {},
) {
    ListComponent(
        leadingContent = leadingContent,
        headlineContent = {
            RichText(
                text = data.name,
                modifier =
                    nameModifier.profileTextClick(data.key, onUserClick),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            PlatformText(
                text = subtitle,
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
                modifier =
                    handleModifier.profileTextClick(data.key, onUserClick),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = trailing,
        modifier = modifier,
    )
}

@Composable
private fun profileClick(data: UiProfile): ((MicroBlogKey) -> Unit)? {
    if (data.clickEvent == ClickEvent.Noop) return null
    val uriHandler = LocalUriHandler.current
    return { data.onClicked(ClickContext(launcher = uriHandler::openUri)) }
}

@Composable
private fun Modifier.profileTextClick(
    key: MicroBlogKey,
    onClick: ((MicroBlogKey) -> Unit)?,
): Modifier =
    if (onClick == null) {
        this
    } else {
        pointerHoverIcon(PointerIcon.Hand).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            onClick(key)
        }
    }

@Composable
public fun ListComponent(
    headlineContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent.invoke(this)
            Spacer(modifier = Modifier.width(8.dp))
        }
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
