package dev.dimension.flare.ui.screen.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.ClockRotateLeft
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Filter
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Palette
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.TableList
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeIconData
import dev.dimension.flare.ui.component.ThemedIcon
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
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
    toColorSpace: () -> Unit,
    toTabCustomization: () -> Unit,
    toLocalFilter: () -> Unit,
    toGuestSettings: () -> Unit,
    toLocalHistory: () -> Unit,
    toAiConfig: () -> Unit,
    toRSSManagement: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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
                        avatarSize = 40.dp,
                        onClick = {
                            toAccounts.invoke()
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                        toLogin = {
                            toAccounts.invoke()
                        },
                        shapes = ListItemDefaults.single(),
                    )
                }.onError {
                    SegmentedListItem(
                        onClick = {
                            toAccounts.invoke()
                        },
                        shapes = ListItemDefaults.single(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                        leadingContent = {
                            ThemedIcon(
                                imageVector = FontAwesomeIcons.Solid.CircleUser,
                                contentDescription = null,
                                color = ThemeIconData.Color.ImperialMagenta,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_accounts_title))
                        },
                    )
                }

            state.user
                .onError {
                    SegmentedListItem(
                        onClick = {
                            toGuestSettings.invoke()
                        },
                        shapes = ListItemDefaults.single(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_guest_setting_title))
                        },
                        leadingContent = {
                            ThemedIcon(
                                imageVector = FontAwesomeIcons.Solid.Globe,
                                contentDescription = stringResource(id = R.string.settings_guest_setting_title),
                                color = ThemeIconData.Color.SapphireBlue,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_guest_setting_description))
                        },
                    )
                }
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SegmentedListItem(
                    onClick = {
                        toAppearance.invoke()
                    },
                    shapes =
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU && !state.user.isSuccess) {
                            ListItemDefaults.single()
                        } else {
                            ListItemDefaults.first()
                        },
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_title))
                    },
                    leadingContent = {
                        ThemedIcon(
                            imageVector = FontAwesomeIcons.Solid.Palette,
                            contentDescription = stringResource(id = R.string.settings_appearance_title),
                            color = ThemeIconData.Color.RoyalPurple,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_subtitle))
                    },
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    SegmentedListItem(
                        onClick = {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                        data = "package:${BuildConfig.APPLICATION_ID}".toUri()
                                    }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        shapes =
                            if (state.user.isSuccess) {
                                ListItemDefaults.item()
                            } else {
                                ListItemDefaults.last()
                            },
                        content = {
                            Text(text = stringResource(id = R.string.settings_language_title))
                        },
                        leadingContent = {
                            ThemedIcon(
                                imageVector = FontAwesomeIcons.Solid.Language,
                                contentDescription = stringResource(id = R.string.settings_language_title),
                                color = ThemeIconData.Color.SapphireBlue,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_language_description))
                        },
                    )
                }
                state.user.onSuccess {
                    SegmentedListItem(
                        onClick = {
                            toTabCustomization.invoke()
                        },
                        shapes = ListItemDefaults.last(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_side_panel))
                        },
                        leadingContent = {
                            ThemedIcon(
                                imageVector = FontAwesomeIcons.Solid.TableList,
                                contentDescription = stringResource(id = R.string.settings_side_panel),
                                color = ThemeIconData.Color.DeepTeal,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_side_panel_description))
                        },
                    )
                }
            }

            state.user
                .onSuccess {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        SegmentedListItem(
                            onClick = {
                                toRSSManagement.invoke()
                            },
                            shapes = ListItemDefaults.first(),
                            content = {
                                Text(text = stringResource(id = R.string.settings_rss_management_title))
                            },
                            leadingContent = {
                                ThemedIcon(
                                    imageVector = FontAwesomeIcons.Solid.SquareRss,
                                    contentDescription = stringResource(id = R.string.settings_rss_management_title),
                                    color = ThemeIconData.Color.ImperialMagenta,
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_rss_management_description))
                            },
                        )
                        SegmentedListItem(
                            onClick = {
                                toLocalFilter.invoke()
                            },
                            shapes = ListItemDefaults.item(),
                            content = {
                                Text(text = stringResource(id = R.string.settings_local_filter_title))
                            },
                            leadingContent = {
                                ThemedIcon(
                                    imageVector = FontAwesomeIcons.Solid.Filter,
                                    contentDescription = stringResource(id = R.string.settings_local_filter_title),
                                    color = ThemeIconData.Color.BurntUmber,
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_local_filter_description))
                            },
                        )
                        SegmentedListItem(
                            onClick = {
                                toLocalHistory.invoke()
                            },
                            shapes = ListItemDefaults.item(),
                            content = {
                                Text(text = stringResource(id = R.string.settings_local_history_title))
                            },
                            leadingContent = {
                                ThemedIcon(
                                    imageVector = FontAwesomeIcons.Solid.ClockRotateLeft,
                                    contentDescription = stringResource(id = R.string.settings_local_history_title),
                                    color = ThemeIconData.Color.BurntUmber,
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_local_history_description))
                            },
                        )
                        SegmentedListItem(
                            onClick = {
                                toStorage.invoke()
                            },
                            shapes = ListItemDefaults.last(),
                            content = {
                                Text(text = stringResource(id = R.string.settings_storage_title))
                            },
                            leadingContent = {
                                ThemedIcon(
                                    imageVector = FontAwesomeIcons.Solid.Database,
                                    contentDescription = stringResource(id = R.string.settings_storage_title),
                                    color = ThemeIconData.Color.DarkAmber,
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_storage_subtitle))
                            },
                        )
                    }
//            ListItem(
//                headlineContent = {
//                    Text(text = stringResource(id = R.string.settings_notifications_title))
//                },
//                leadingContent = {
//                    ThemedIcon(
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
                }.onError {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        SegmentedListItem(
                            onClick = {
                                toRSSManagement.invoke()
                            },
                            shapes = ListItemDefaults.first(),
                            content = {
                                Text(text = stringResource(id = R.string.settings_rss_management_title))
                            },
                            leadingContent = {
                                ThemedIcon(
                                    imageVector = FontAwesomeIcons.Solid.SquareRss,
                                    contentDescription = stringResource(id = R.string.settings_rss_management_title),
                                    color = ThemeIconData.Color.ImperialMagenta,
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_rss_management_description))
                            },
                        )
                        SegmentedListItem(
                            onClick = {
                                toStorage.invoke()
                            },
                            shapes = ListItemDefaults.last(),
                            content = {
                                Text(text = stringResource(id = R.string.settings_storage_title))
                            },
                            leadingContent = {
                                ThemedIcon(
                                    imageVector = FontAwesomeIcons.Solid.Database,
                                    contentDescription = stringResource(id = R.string.settings_storage_title),
                                    color = ThemeIconData.Color.DarkAmber,
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_storage_subtitle))
                            },
                        )
                    }
                }
            SegmentedListItem(
                onClick = {
                    toAiConfig.invoke()
                },
                shapes = ListItemDefaults.single(),
                content = {
                    Text(text = stringResource(id = R.string.settings_ai_config_title))
                },
                leadingContent = {
                    ThemedIcon(
                        imageVector = FontAwesomeIcons.Solid.Robot,
                        contentDescription = stringResource(id = R.string.settings_ai_config_title),
                        color = ThemeIconData.Color.ForestGreen,
                    )
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_ai_config_description))
                },
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (BuildConfig.DEBUG) {
                    SegmentedListItem(
                        onClick = {
                            toColorSpace.invoke()
                        },
                        shapes = ListItemDefaults.first(),
                        content = {
                            Text(text = "Color Space")
                        },
                        leadingContent = {
                            ThemedIcon(
                                imageVector = FontAwesomeIcons.Solid.CircleInfo,
                                contentDescription = null,
                                color = ThemeIconData.Color.CharcoalGrey,
                            )
                        },
                    )
                }
                SegmentedListItem(
                    onClick = {
                        toAbout.invoke()
                    },
                    shapes =
                        if (BuildConfig.DEBUG) {
                            ListItemDefaults.last()
                        } else {
                            ListItemDefaults.single()
                        },
                    content = {
                        Text(text = stringResource(id = R.string.settings_about_title))
                    },
                    leadingContent = {
                        ThemedIcon(
                            imageVector = FontAwesomeIcons.Solid.CircleInfo,
                            contentDescription = stringResource(id = R.string.settings_about_title),
                            color = ThemeIconData.Color.CharcoalGrey,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_about_subtitle))
                    },
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
