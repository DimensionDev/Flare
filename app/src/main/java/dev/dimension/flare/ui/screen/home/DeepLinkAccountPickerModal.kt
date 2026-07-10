package dev.dimension.flare.ui.screen.home

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Globe
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LinkOpenDefaultsActionsPresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun DeepLinkAccountPickerModal(
    originalUrl: String,
    data: ImmutableMap<MicroBlogKey, Route>,
    onNavigate: (Route) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val accounts by producePresenter { presenter(data) }
    val defaultsActions by producePresenter("link_open_defaults_picker") {
        remember(originalUrl) { LinkOpenDefaultsActionsPresenter(originalUrl) }.invoke()
    }
    var saveAsDefault by remember(originalUrl) {
        mutableStateOf(false)
    }
    LazyColumn(
        contentPadding =
            PaddingValues(
                horizontal = screenHorizontalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stickyHeader {
            Text(stringResource(R.string.deeplink_account_selection_message))
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        itemsIndexed(accounts) { index, item ->
            AccountItem(
                item.userState.user,
                onClick = {
                    if (saveAsDefault) {
                        defaultsActions.setAccountDefault(item.accountKey)
                    }
                    onDismissRequest()
                    onNavigate(item.route)
                },
                toLogin = {},
                toRelogin = {
                    onDismissRequest()
                    onNavigate(Route.ServiceSelect.Relogin(it))
                },
                modifier =
                    Modifier
                        .listCard(
                            index = index,
                            totalCount = accounts.size + 1,
                        ),
            )
        }
        item {
            val text = stringResource(R.string.media_menu_copy_link)
            ListItem(
                modifier =
                    Modifier
                        .listCard(
                            index = accounts.size,
                            totalCount = accounts.size + 1,
                        ).clickable {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        clipData =
                                            ClipData.newPlainText(
                                                text,
                                                originalUrl,
                                            ),
                                    ),
                                )
                                onDismissRequest.invoke()
                            }
                        },
                leadingContent = {
                    FAIcon(
                        FontAwesomeIcons.Solid.Globe,
                        contentDescription = text,
                    )
                },
                elevation = ListItemDefaults.elevation(),
                content = {
                    Text(text)
                },
            )
        }
        item {
            ListItem(
                modifier =
                    Modifier
                        .clickable {
                            saveAsDefault = !saveAsDefault
                        },
                trailingContent = {
                    Checkbox(
                        checked = saveAsDefault,
                        onCheckedChange = {
                            saveAsDefault = it
                        },
                    )
                },
                colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                elevation = ListItemDefaults.elevation(),
                content = {
                    Text(stringResource(R.string.deeplink_account_selection_save_default))
                },
            )
        }
        item {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
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
