package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ClockRotateLeft
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.PenToSquare
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.UserPlus
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.SecondaryTabsPresenter
import dev.dimension.flare.ui.route.Route
import kotlinx.collections.immutable.ImmutableList

private val SidebarHorizontalPadding = 16.dp

@Composable
internal fun HomeSecondarySidebar(
    secondaryTabs: UiState<ImmutableList<SecondaryTabsPresenter.Item>>,
    isLoggedIn: Boolean?,
    aiAgentEnabled: Boolean,
    currentRoute: Route,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accounts = secondaryTabs.takeSuccess().orEmpty()
    val searchAccount = accounts.firstOrNull()?.accountType ?: AccountType.Guest
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitSearch = {
        navigate(Route.Search(accountType = searchAccount, query = query.trim()))
        keyboardController?.hide()
        Unit
    }

    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SidebarHorizontalPadding),
                placeholder = { Text(stringResource(R.string.discover_search_placeholder)) },
                singleLine = true,
                leadingIcon = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = submitSearch) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                            contentDescription = stringResource(R.string.discover_search_placeholder),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
            )
        }

        if (isLoggedIn == false) {
            item {
                SidebarItem(
                    label = stringResource(R.string.login_button),
                    selected = currentRoute is Route.ServiceSelect,
                    icon = FontAwesomeIcons.Solid.UserPlus,
                    onClick = { navigate(Route.ServiceSelect.Selection) },
                )
            }
        }

        accounts.forEach { account ->
            val profileRoute = account.tabs.firstOrNull()?.let(::getDirection)
            item(key = "account-${account.accountType}") {
                NavigationDrawerItem(
                    label = {
                        Column {
                            account.user.takeSuccess()?.let { user ->
                                RichText(user.name, maxLines = 1)
                                Text(
                                    user.handle.canonical,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    selected = profileRoute != null && currentRoute == profileRoute,
                    onClick = { profileRoute?.let(navigate) },
                    icon = {
                        account.user.takeSuccess()?.let { user ->
                            AvatarComponent(user.avatar)
                        }
                    },
                    modifier = Modifier.padding(horizontal = SidebarHorizontalPadding),
                )
            }
            account.tabs.drop(1).forEach { tab ->
                val route = getDirection(tab) ?: return@forEach
                item(key = "${account.accountType}-$route") {
                    NavigationDrawerItem(
                        label = {
                            dev.dimension.flare.ui.component
                                .Text(tab.title.asText())
                        },
                        selected = currentRoute == route,
                        onClick = { navigate(route) },
                        icon = {
                            TabIcon(
                                icon = tab.icon.asType(),
                                title = tab.title.asText(),
                                iconOnly = true,
                            )
                        },
                        modifier =
                            Modifier.padding(
                                start = SidebarHorizontalPadding + 24.dp,
                                end = SidebarHorizontalPadding,
                            ),
                    )
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        item {
            SidebarItem(
                label = stringResource(R.string.draft_box_title),
                selected = currentRoute is Route.DraftBox,
                icon = FontAwesomeIcons.Solid.PenToSquare,
                onClick = { navigate(Route.DraftBox) },
            )
        }
        item {
            SidebarItem(
                label = stringResource(R.string.settings_rss_management_title),
                selected = currentRoute is Route.Rss.Sources,
                icon = FontAwesomeIcons.Solid.SquareRss,
                onClick = { navigate(Route.Rss.Sources) },
            )
        }
        item {
            SidebarItem(
                label = stringResource(R.string.settings_local_history_title),
                selected = currentRoute is Route.Settings.LocalHistory,
                icon = FontAwesomeIcons.Solid.ClockRotateLeft,
                onClick = { navigate(Route.Settings.LocalHistory) },
            )
        }
        if (aiAgentEnabled) {
            item {
                SidebarItem(
                    label = stringResource(R.string.agent_history_title),
                    selected = currentRoute is Route.Settings.AgentHistory,
                    icon = FontAwesomeIcons.Solid.Robot,
                    onClick = { navigate(Route.Settings.AgentHistory) },
                )
            }
        }
        item {
            SidebarItem(
                label = stringResource(R.string.settings_title),
                selected = currentRoute is Route.Settings.Main,
                icon = FontAwesomeIcons.Solid.Gear,
                onClick = { navigate(Route.Settings.Main) },
            )
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = {
            FAIcon(imageVector = icon, contentDescription = label)
        },
        modifier = Modifier.padding(horizontal = SidebarHorizontalPadding),
    )
}
