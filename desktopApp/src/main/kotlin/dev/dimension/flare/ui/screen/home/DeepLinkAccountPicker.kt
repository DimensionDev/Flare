package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Globe
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.deeplink_account_selection_browser
import dev.dimension.flare.deeplink_account_selection_message
import dev.dimension.flare.deeplink_account_selection_title
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DeepLinkAccountPicker(
    originalUrl: String,
    data: ImmutableMap<MicroBlogKey, Route>,
    onNavigate: (Route) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val accounts by producePresenter { presenter(data) }
    ContentDialog(
        visible = true,
        title = stringResource(Res.string.deeplink_account_selection_title),
        primaryButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            onDismissRequest()
        },
        content = {
            LazyColumn {
                item {
                    Header(stringResource(Res.string.deeplink_account_selection_message))
                }
                items(accounts) { (userState, route) ->
                    AccountItem(
                        userState.user,
                        onClick = {
                            onDismissRequest()
                            onNavigate(route)
                        },
                        toLogin = {},
                    )
                }
                item {
                    CardExpanderItem(
                        onClick = {
                            uriHandler.openUri(originalUrl)
                            onDismissRequest.invoke()
                        },
                        heading = {
                            Text(stringResource(Res.string.deeplink_account_selection_browser))
                        },
                        icon = {
                            FAIcon(
                                FontAwesomeIcons.Solid.Globe,
                                contentDescription = stringResource(Res.string.deeplink_account_selection_browser),
                            )
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun presenter(data: ImmutableMap<MicroBlogKey, Route>) =
    run {
        remember(data) {
            data.map {
                UserPresenter(AccountType.Specific(it.key), null) to it.value
            }
        }.map {
            it.first.invoke() to it.second
        }.toImmutableList()
    }
