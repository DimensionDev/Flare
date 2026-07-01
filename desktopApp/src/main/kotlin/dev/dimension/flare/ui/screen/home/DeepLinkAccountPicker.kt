package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Globe
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.deeplink_account_selection_browser
import dev.dimension.flare.deeplink_account_selection_message
import dev.dimension.flare.deeplink_account_selection_save_default
import dev.dimension.flare.deeplink_account_selection_title
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LinkOpenDefaultsActionsPresenter
import dev.dimension.flare.ui.route.Route
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.CheckBox
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
    val defaultsActions by producePresenter("link_open_defaults_picker") {
        remember(originalUrl) { LinkOpenDefaultsActionsPresenter(originalUrl) }.invoke()
    }
    var saveAsDefault by remember(defaultsActions.canSaveDefault) {
        mutableStateOf(defaultsActions.canSaveDefault)
    }
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
                items(accounts) { item ->
                    AccountItem(
                        item.userState.user,
                        onClick = {
                            if (saveAsDefault && defaultsActions.canSaveDefault) {
                                defaultsActions.setAccountDefault(item.accountKey)
                            }
                            onDismissRequest()
                            onNavigate(item.route)
                        },
                        toLogin = {},
                        toRelogin = {
                            onDismissRequest()
                            onNavigate(Route.Relogin(it))
                        },
                    )
                }
                item {
                    CardExpanderItem(
                        onClick = {
                            if (saveAsDefault && defaultsActions.canSaveDefault) {
                                defaultsActions.setBrowserDefault()
                            }
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
                if (defaultsActions.canSaveDefault) {
                    item {
                        Row(
                            modifier =
                                Modifier.clickable {
                                    saveAsDefault = !saveAsDefault
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CheckBox(
                                checked = saveAsDefault,
                                onCheckStateChange = {
                                    saveAsDefault = it
                                },
                            )
                            Text(stringResource(Res.string.deeplink_account_selection_save_default))
                        }
                    }
                }
            }
        },
    )
}

private data class DeepLinkAccountItem(
    val accountKey: MicroBlogKey,
    val userState: UserState,
    val route: Route,
)

@Composable
private fun presenter(data: ImmutableMap<MicroBlogKey, Route>) =
    run {
        remember(data) {
            data.map { (accountKey, route) ->
                accountKey to (UserPresenter(AccountType.Specific(accountKey), null) to route)
            }
        }.map { (accountKey, presenterAndRoute) ->
            DeepLinkAccountItem(
                accountKey = accountKey,
                userState = presenterAndRoute.first.invoke(),
                route = presenterAndRoute.second,
            )
        }.toImmutableList()
    }
