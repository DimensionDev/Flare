package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.ClockRotateLeft
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Filter
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Palette
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.TableList
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun SettingsDetailPlaceholder(modifier: Modifier = Modifier) {
    FlareScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                    alignment = Alignment.CenterVertically,
                ),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.Gear,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(
    toAccounts: () -> Unit,
    toAppearance: () -> Unit,
    toStorage: () -> Unit,
    toAbout: () -> Unit,
    toTabCustomization: () -> Unit,
    toLocalFilter: () -> Unit,
    toGuestSettings: () -> Unit,
    toLocalHistory: () -> Unit,
    toAiConfig: () -> Unit,
    onBack: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { settingsPresenter() }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.user
                .onSuccess {
                    AccountItem(
                        userState = state.user,
                        onClick = {
                            toAccounts.invoke()
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                        toLogin = {
                            toAccounts.invoke()
                        },
                        modifier =
                            Modifier
                                .clip(shape = MaterialTheme.shapes.medium),
                    )
                }.onError {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    toAccounts.invoke()
                                }.clip(shape = MaterialTheme.shapes.medium),
                        leadingContent = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.CircleUser,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                    )
                }

            state.user
                .onError {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_guest_setting_title))
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    toGuestSettings.invoke()
                                },
                        leadingContent = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Globe,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_guest_setting_description))
                        },
                    )
                }

            Column(
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_title))
                    },
                    leadingContent = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Palette,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_subtitle))
                    },
                    modifier =
                        Modifier
                            .clickable {
                                toAppearance.invoke()
                            }.clip(shape = MaterialTheme.shapes.extraSmall),
                )
                state.user.onSuccess {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_side_panel))
                        },
                        leadingContent = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.TableList,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_side_panel_description))
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    toTabCustomization.invoke()
                                }.clip(shape = MaterialTheme.shapes.extraSmall),
                    )
                }
            }

            state.user.onSuccess {
                Column(
                    modifier =
                        Modifier
                            .clip(MaterialTheme.shapes.medium),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_local_filter_title))
                        },
                        leadingContent = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Filter,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_local_filter_description))
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    toLocalFilter.invoke()
                                }.clip(shape = MaterialTheme.shapes.extraSmall),
                    )
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_local_history_title))
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    toLocalHistory.invoke()
                                }.clip(shape = MaterialTheme.shapes.extraSmall),
                        leadingContent = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ClockRotateLeft,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_local_history_description))
                        },
                    )
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_storage_title))
                        },
                        leadingContent = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Database,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_storage_subtitle))
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    toStorage.invoke()
                                }.clip(shape = MaterialTheme.shapes.extraSmall),
                    )
                }
//            ListItem(
//                headlineContent = {
//                    Text(text = stringResource(id = R.string.settings_notifications_title))
//                },
//                leadingContent = {
//                    FAIcon(
//                        imageVector = Icons.Default.Notifications,
//                        contentDescription = null,
//                    )
//                },
//                supportingContent = {
//                    Text(text = stringResource(id = R.string.settings_notifications_subtitle))
//                },
//                modifier =
//                    Modifier.clickable {
//                        toNotifications.invoke()
//                    },
//            )
            }
            Column(
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_ai_config_title))
                    },
                    leadingContent = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Robot,
                            contentDescription = stringResource(id = R.string.settings_ai_config_title),
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_ai_config_description))
                    },
                    modifier =
                        Modifier
                            .clickable {
                                toAiConfig.invoke()
                            }.clip(shape = MaterialTheme.shapes.extraSmall),
                )
            }
            Column(
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_about_title))
                    },
                    leadingContent = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.CircleInfo,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_about_subtitle))
                    },
                    modifier =
                        Modifier
                            .clickable {
                                toAbout.invoke()
                            }.clip(shape = MaterialTheme.shapes.extraSmall),
                )
            }
        }
    }
}

@Composable
private fun settingsPresenter() =
    run {
        val state = remember { ActiveAccountPresenter() }.invoke()
        object : UserState by state {
        }
    }
