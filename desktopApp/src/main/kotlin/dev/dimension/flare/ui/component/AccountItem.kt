package dev.dimension.flare.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FaceSadTear
import dev.dimension.flare.Res
import dev.dimension.flare.account_item_error_message
import dev.dimension.flare.account_item_error_title
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.login_expired
import dev.dimension.flare.login_expired_relogin
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T : UiUserV2> AccountItem(
    userState: UiState<T>,
    onClick: (MicroBlogKey) -> Unit,
    toLogin: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (UiUserV2?) -> Unit = { },
    headlineContent: @Composable (UiUserV2) -> Unit = {
        RichText(text = it.name, maxLines = 1)
    },
    supportingContent: @Composable (UiUserV2) -> Unit = {
        Text(text = it.handle, maxLines = 1)
    },
    avatarSize: Dp = 24.dp,
) {
    userState
        .onSuccess { data ->
            CardExpanderItem(
                heading = {
                    headlineContent.invoke(data)
                },
                onClick = {
                    onClick.invoke(data.key)
                },
                modifier = modifier,
                icon = {
                    AvatarComponent(data = data.avatar, size = avatarSize)
                },
                trailing = {
                    trailingContent.invoke(data)
                },
                caption = {
                    supportingContent.invoke(data)
                },
            )
        }.onLoading {
            CardExpanderItem(
                heading = {
                    Text(text = "Loading...", modifier = Modifier.placeholder(true))
                },
                modifier = modifier,
                icon = {
                    AvatarComponent(
                        data = null,
                        modifier = Modifier.placeholder(true, shape = CircleShape),
                        size = avatarSize,
                    )
                },
                caption = {
                    Text(text = "Loading...", modifier = Modifier.placeholder(true))
                },
            )
        }.onError { throwable ->
            CardExpanderItem(
                heading = {
                    if (throwable is LoginExpiredException) {
                        Text(
                            text =
                                stringResource(
                                    Res.string.login_expired,
                                    throwable.accountKey.toString(),
                                ),
                        )
                    } else {
                        Text(text = stringResource(Res.string.account_item_error_title))
                    }
                },
                modifier = modifier,
                icon = {
                    FAIcon(
                        FontAwesomeIcons.Solid.FaceSadTear,
                        contentDescription = stringResource(Res.string.account_item_error_title),
                    )
                },
                caption = {
                    if (throwable is LoginExpiredException) {
                        Text(text = throwable.accountKey.toString())
                    } else {
                        Text(text = stringResource(Res.string.account_item_error_message))
                    }
                },
                trailing = {
                    if (throwable is LoginExpiredException) {
                        SubtleButton(onClick = toLogin) {
                            Text(text = stringResource(Res.string.login_expired_relogin))
                        }
                    } else {
                        trailingContent.invoke(null)
                    }
                },
            )
        }
}
