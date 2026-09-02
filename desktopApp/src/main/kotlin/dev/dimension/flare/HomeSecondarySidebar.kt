package dev.dimension.flare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.SecondaryTabsPresenter
import dev.dimension.flare.ui.route.Route
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.Expander
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeSecondarySidebar(
    secondaryTabs: UiState<ImmutableList<SecondaryTabsPresenter.Item>>,
    isLoggedIn: Boolean?,
    aiAgentEnabled: Boolean,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accounts = secondaryTabs.takeSuccess().orEmpty()
    val searchAccount = accounts.firstOrNull()?.accountType ?: AccountType.Guest
    val searchState = rememberTextFieldState()
    val submitSearch = {
        navigate(
            Route.Search(
                accountType = searchAccount,
                keyword = searchState.text.toString().trim(),
            ),
        )
    }
    val scrollState = rememberScrollState()
    val sidebarRoutes =
        buildList<Triple<StringResource, ImageVector, Route>> {
            add(Triple(Res.string.settings_draft_box_title, FontAwesomeIcons.Solid.PenToSquare, Route.DraftBox))
            add(Triple(Res.string.settings_rss_management_title, FontAwesomeIcons.Solid.SquareRss, Route.RssList))
            add(Triple(Res.string.settings_local_history_title, FontAwesomeIcons.Solid.ClockRotateLeft, Route.LocalCache))
            if (aiAgentEnabled) {
                add(Triple(Res.string.settings_agent_history_title, FontAwesomeIcons.Solid.Robot, Route.AgentHistory))
            }
            add(Triple(Res.string.home_settings, FontAwesomeIcons.Solid.Gear, Route.Settings))
        }

    FlareScrollBar(scrollState) {
        Column(
            modifier =
                modifier
                    .fillMaxHeight()
                    .padding(LocalWindowPadding.current)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                state = searchState,
                modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.SingleLine,
                onKeyboardAction = { submitSearch() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                placeholder = { Text(stringResource(Res.string.emoji_picker_search)) },
                trailing = {
                    SubtleButton(
                        onClick = submitSearch,
                        iconOnly = true,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.MagnifyingGlass,
                            contentDescription = stringResource(Res.string.emoji_picker_search),
                        )
                    }
                },
            )

            if (isLoggedIn == false) {
                SidebarItem(
                    label = stringResource(Res.string.home_login),
                    icon = FontAwesomeIcons.Solid.UserPlus,
                    onClick = { navigate(Route.ServiceSelect) },
                )
            }

            AccountShortcutList(
                accounts = accounts,
                initiallyExpanded = true,
                navigate = navigate,
            )

            sidebarRoutes.forEach { (label, icon, route) ->
                SidebarItem(
                    label = stringResource(label),
                    icon = icon,
                    onClick = { navigate(route) },
                )
            }
        }
    }
}

@Composable
internal fun AccountShortcutList(
    accounts: List<SecondaryTabsPresenter.Item>,
    initiallyExpanded: Boolean = false,
    navigate: (Route) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { account ->
            var expanded by remember(account.accountType) { mutableStateOf(initiallyExpanded) }
            account.user.takeSuccess()?.let { user ->
                Expander(
                    expanded = expanded,
                    onExpandedChanged = { expanded = it },
                    heading = {
                        RichText(text = user.name, maxLines = 1)
                    },
                    caption = {
                        Text(text = user.handle.canonical, maxLines = 1)
                    },
                    icon = {
                        AvatarComponent(data = user.avatar, size = 24.dp)
                    },
                ) {
                    account.tabs.forEach { shortcut ->
                        val route = getDirection(shortcut) ?: return@forEach
                        CardExpanderItem(
                            onClick = { navigate(route) },
                            heading = {
                                dev.dimension.flare.ui.component
                                    .Text(shortcut.title.asText())
                            },
                            icon = {
                                FAIcon(
                                    imageVector = shortcut.icon.toImageVector(),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    CardExpanderItem(
        onClick = onClick,
        heading = { Text(label) },
        icon = {
            FAIcon(imageVector = icon, contentDescription = label)
        },
    )
}
