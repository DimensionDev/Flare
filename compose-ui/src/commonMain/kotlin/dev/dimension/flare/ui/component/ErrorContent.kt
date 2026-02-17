package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.FileCircleExclamation
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.login_expired
import dev.dimension.flare.compose.ui.login_expired_message
import dev.dimension.flare.compose.ui.permission_denied_message
import dev.dimension.flare.compose.ui.permission_denied_title
import dev.dimension.flare.compose.ui.status_loadmore_error
import dev.dimension.flare.data.network.misskey.api.model.MisskeyException
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ErrorContent(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (error) {
        is LoginExpiredException -> {
            LoginExpiredError(error, modifier)
        }

        is MisskeyException -> {
            MisskeyError(
                error = error,
                modifier = modifier,
                onRetry = onRetry,
            )
        }

        else -> {
            CommonError(
                error = error,
                onRetry = onRetry,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun CommonError(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable {
                    onRetry.invoke()
                }.fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.FileCircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(text = stringResource(Res.string.status_loadmore_error))
        error.message?.let { PlatformText(text = it) }
    }
}

@Composable
private fun MisskeyError(
    error: MisskeyException,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (error.error?.code == "PERMISSION_DENIED") {
        val uriHandler = LocalUriHandler.current
        Column(
            modifier =
                modifier
                    .clickable {
                        uriHandler.openUri(DeeplinkRoute.Login.toUri())
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            PlatformText(
                text = stringResource(resource = Res.string.permission_denied_title),
            )
            PlatformText(
                text = stringResource(resource = Res.string.permission_denied_message),
            )
        }
    } else {
        CommonError(
            error = error,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoginExpiredError(
    error: LoginExpiredException,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(DeeplinkRoute.Login.toUri())
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired_message),
        )
        PlatformText(
            text = error.accountKey.toString(),
        )
    }
}
