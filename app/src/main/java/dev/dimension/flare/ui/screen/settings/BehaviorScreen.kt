package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.TimelineAutoRefreshInterval
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.presenter.SettingsPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.LinkOpenDefaultsPresenter
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BehaviorScreen(
    toLinkOpenDefaults: () -> Unit,
    onBack: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter("behavior_settings") {
        remember { SettingsPresenter() }.invoke()
    }
    val appSettings = state.appSettings.takeSuccessOr(AppSettings(version = ""))
    val globalAppearance = LocalGlobalAppearance.current
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_behavior_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            SegmentedListItem(
                onClick = {
                    state.updateRefreshHomeTimelineOnLaunch(!appSettings.refreshHomeTimelineOnLaunch)
                },
                shapes = ListItemDefaults.first(),
                content = {
                    Text(text = stringResource(id = R.string.settings_refresh_home_timeline_on_launch))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_refresh_home_timeline_on_launch_description))
                },
                trailingContent = {
                    Switch(
                        checked = appSettings.refreshHomeTimelineOnLaunch,
                        onCheckedChange = state::updateRefreshHomeTimelineOnLaunch,
                    )
                },
            )
            SingleChoiceSettingsItem(
                headline = {
                    Text(text = stringResource(id = R.string.settings_home_timeline_auto_refresh_interval))
                },
                supporting = {
                    Text(text = stringResource(id = R.string.settings_home_timeline_auto_refresh_interval_description))
                },
                items =
                    persistentMapOf(
                        TimelineAutoRefreshInterval.DISABLED to
                            stringResource(id = R.string.settings_auto_refresh_disabled),
                        TimelineAutoRefreshInterval.ONE_MINUTE to
                            stringResource(id = R.string.settings_auto_refresh_one_minute),
                        TimelineAutoRefreshInterval.FIVE_MINUTES to
                            stringResource(id = R.string.settings_auto_refresh_five_minutes),
                        TimelineAutoRefreshInterval.FIFTEEN_MINUTES to
                            stringResource(id = R.string.settings_auto_refresh_fifteen_minutes),
                        TimelineAutoRefreshInterval.THIRTY_MINUTES to
                            stringResource(id = R.string.settings_auto_refresh_thirty_minutes),
                        TimelineAutoRefreshInterval.ONE_HOUR to
                            stringResource(id = R.string.settings_auto_refresh_one_hour),
                    ),
                selected = appSettings.homeTimelineAutoRefreshInterval,
                onSelected = state::updateHomeTimelineAutoRefreshInterval,
                shapes = ListItemDefaults.item(),
            )
            SegmentedListItem(
                onClick = {
                    state.update(AppearanceKeys.InAppBrowser, !globalAppearance.inAppBrowser)
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_in_app_browser))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_in_app_browser_description))
                },
                trailingContent = {
                    Switch(
                        checked = globalAppearance.inAppBrowser,
                        onCheckedChange = {
                            state.update(AppearanceKeys.InAppBrowser, it)
                        },
                    )
                },
            )
            SegmentedListItem(
                onClick = toLinkOpenDefaults,
                shapes = ListItemDefaults.last(),
                content = {
                    Text(text = stringResource(id = R.string.settings_link_open_defaults_title))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_link_open_defaults_description))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LinkOpenDefaultsScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val linkOpenDefaultsState by producePresenter("link_open_defaults_settings") {
        remember { LinkOpenDefaultsPresenter() }.invoke()
    }
    val linkOpenTargets = linkOpenDefaultsState.targets.takeSuccessOr(persistentListOf())
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_link_open_defaults_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            linkOpenTargets.forEachIndexed { index, target ->
                LinkOpenDefaultSettingsItem(
                    target = target,
                    state = linkOpenDefaultsState,
                    shapes =
                        when (index) {
                            0 -> if (linkOpenTargets.size == 1) ListItemDefaults.single() else ListItemDefaults.first()
                            linkOpenTargets.lastIndex -> ListItemDefaults.last()
                            else -> ListItemDefaults.item()
                        },
                )
            }
        }
    }
}

@Composable
private fun LinkOpenDefaultSettingsItem(
    target: LinkOpenDefaultsPresenter.Target,
    state: LinkOpenDefaultsPresenter.State,
    shapes: ListItemShapes,
) {
    val askLabel = stringResource(id = R.string.settings_link_open_default_ask_every_time)
    val browserLabel = stringResource(id = R.string.deeplink_account_selection_browser)
    var showMenu by remember { mutableStateOf(false) }
    SegmentedListItem(
        checked = showMenu,
        onCheckedChange = { showMenu = it },
        shapes = shapes,
        content = {
            Text(text = target.title)
        },
        supportingContent = {
            LinkOpenDefaultOptionContent(
                option = target.selectedOption,
                askLabel = askLabel,
                browserLabel = browserLabel,
            )
        },
        trailingContent = {
            FlareDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                target.options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            LinkOpenDefaultOptionContent(
                                option = option,
                                askLabel = askLabel,
                                browserLabel = browserLabel,
                            )
                        },
                        onClick = {
                            state.select(target, option)
                            showMenu = false
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun LinkOpenDefaultOptionContent(
    option: LinkOpenDefaultsPresenter.Option,
    askLabel: String,
    browserLabel: String,
) {
    when {
        option.isAsk -> Text(text = askLabel)
        option.isBrowser -> Text(text = browserLabel)
        option.account != null -> LinkOpenDefaultAccountOption(account = option.account!!)
    }
}

@Composable
private fun LinkOpenDefaultAccountOption(account: LinkOpenDefaultsPresenter.Account) {
    account.profile
        .onSuccess { user ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarComponent(data = user.avatar, size = 24.dp)
                Column {
                    RichText(text = user.name, maxLines = 1)
                    Text(text = user.handle.canonical, maxLines = 1)
                }
            }
        }.onLoading {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarComponent(data = null, size = 24.dp)
                Text(text = account.accountKey.toString())
            }
        }.onError {
            Text(text = account.accountKey.toString())
        }
}
