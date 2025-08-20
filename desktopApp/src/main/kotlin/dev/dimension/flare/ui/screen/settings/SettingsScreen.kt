package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Line
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.add_account
import dev.dimension.flare.app_name
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.home_login
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.remove_account
import dev.dimension.flare.settings_about_line
import dev.dimension.flare.settings_about_line_description
import dev.dimension.flare.settings_about_localization
import dev.dimension.flare.settings_about_localization_description
import dev.dimension.flare.settings_about_source_code
import dev.dimension.flare.settings_about_telegram
import dev.dimension.flare.settings_about_telegram_description
import dev.dimension.flare.settings_about_title
import dev.dimension.flare.settings_accounts_title
import dev.dimension.flare.settings_appearance_avatar_shape
import dev.dimension.flare.settings_appearance_avatar_shape_description
import dev.dimension.flare.settings_appearance_avatar_shape_round
import dev.dimension.flare.settings_appearance_avatar_shape_square
import dev.dimension.flare.settings_appearance_compat_link_previews
import dev.dimension.flare.settings_appearance_compat_link_previews_description
import dev.dimension.flare.settings_appearance_expand_media
import dev.dimension.flare.settings_appearance_expand_media_description
import dev.dimension.flare.settings_appearance_show_actions
import dev.dimension.flare.settings_appearance_show_actions_description
import dev.dimension.flare.settings_appearance_show_cw_img
import dev.dimension.flare.settings_appearance_show_cw_img_description
import dev.dimension.flare.settings_appearance_show_link_previews
import dev.dimension.flare.settings_appearance_show_link_previews_description
import dev.dimension.flare.settings_appearance_show_media
import dev.dimension.flare.settings_appearance_show_media_description
import dev.dimension.flare.settings_appearance_show_numbers
import dev.dimension.flare.settings_appearance_show_numbers_description
import dev.dimension.flare.settings_appearance_theme
import dev.dimension.flare.settings_appearance_theme_auto
import dev.dimension.flare.settings_appearance_theme_dark
import dev.dimension.flare.settings_appearance_theme_description
import dev.dimension.flare.settings_appearance_theme_light
import dev.dimension.flare.settings_appearance_title
import dev.dimension.flare.settings_appearance_video_autoplay
import dev.dimension.flare.settings_appearance_video_autoplay_description
import dev.dimension.flare.settings_privacy_policy
import dev.dimension.flare.settings_status_appearance_subtitle
import dev.dimension.flare.settings_status_appearance_title
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun SettingsScreen(toLogin: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter { presenter() }

    val scrollState = rememberScrollState()
    ScrollbarContainer(
        modifier = Modifier.fillMaxSize(),
        adapter = rememberScrollbarAdapter(scrollState),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(LocalWindowPadding.current)
                    .padding(horizontal = screenHorizontalPadding),
        ) {
            state.accountState.user
                .onSuccess { activeAccount ->
                    Header(stringResource(Res.string.settings_accounts_title))
                    Expander(
                        expanded = state.accountState.expanded,
                        onExpandedChanged = {
                            state.accountState.setExpanded(it)
                        },
                        heading = {
                            RichText(activeAccount.name)
                        },
                        icon = {
                            AvatarComponent(data = activeAccount.avatar, size = 24.dp)
                        },
                        caption = {
                            Text(text = activeAccount.handle)
                        },
                    ) {
                        state.accountState.accounts.onSuccess { accounts ->
                            repeat(accounts.size) { index ->
                                val account = accounts[index]
                                AccountItem(
                                    account.second,
                                    onClick = {
                                        state.accountState.setActiveAccount(it)
                                    },
                                    toLogin = toLogin,
                                    trailingContent = {
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (it == null) {
                                                SubtleButton(
                                                    onClick = {
                                                        state.accountState.logout(account.first.accountKey)
                                                    },
                                                ) {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.Trash,
                                                        contentDescription = stringResource(Res.string.remove_account),
                                                        tint = FluentTheme.colors.system.critical,
                                                    )
                                                }
                                            } else {
                                                RadioButton(
                                                    selected = activeAccount.key == it.key,
                                                    onClick = {
                                                        state.accountState.setActiveAccount(it.key)
                                                    },
                                                )
                                                MenuFlyoutContainer(
                                                    flyout = {
                                                        MenuFlyoutItem(
                                                            text = {
                                                                Text(
                                                                    stringResource(Res.string.remove_account),
                                                                    color = FluentTheme.colors.system.critical,
                                                                )
                                                            },
                                                            onClick = {
                                                                state.accountState.logout(it.key)
                                                            },
                                                            icon = {
                                                                FAIcon(
                                                                    FontAwesomeIcons.Solid.Trash,
                                                                    contentDescription =
                                                                        stringResource(
                                                                            Res.string.remove_account,
                                                                        ),
                                                                    tint = FluentTheme.colors.system.critical,
                                                                )
                                                            },
                                                        )
                                                    },
                                                ) {
                                                    SubtleButton(
                                                        onClick = {
                                                            isFlyoutVisible = !isFlyoutVisible
                                                        },
                                                        iconOnly = true,
                                                    ) {
                                                        FAIcon(
                                                            FontAwesomeIcons.Solid.EllipsisVertical,
                                                            contentDescription = stringResource(Res.string.remove_account),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        ExpanderItem(
                            heading = {
                                Text(stringResource(Res.string.add_account))
                            },
                            modifier =
                                Modifier.clickable {
                                    toLogin.invoke()
                                },
                            icon = {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Plus,
                                    contentDescription = stringResource(Res.string.add_account),
                                )
                            },
                        )
                    }
                }.onError {
                    CardExpanderItem(
                        heading = {
                            Text(stringResource(Res.string.home_login))
                        },
                        onClick = {
                            toLogin.invoke()
                        },
                    )
                }

            Header(stringResource(Res.string.settings_appearance_title))

            CardExpanderItem(
                icon = null,
                heading = {
                    Text(stringResource(Res.string.settings_appearance_theme))
                },
                caption = {
                    Text(stringResource(Res.string.settings_appearance_theme_description))
                },
                trailing = {
                    MenuFlyoutContainer(
                        flyout = {
                            MenuFlyoutItem(
                                text = { Text(stringResource(Res.string.settings_appearance_theme_auto)) },
                                onClick = {
                                    state.appearanceState.updateSettings {
                                        copy(theme = Theme.SYSTEM)
                                    }
                                    isFlyoutVisible = false
                                },
                            )
                            MenuFlyoutItem(
                                text = { Text(stringResource(Res.string.settings_appearance_theme_dark)) },
                                onClick = {
                                    isFlyoutVisible = false
                                    state.appearanceState.updateSettings {
                                        copy(theme = Theme.DARK)
                                    }
                                },
                            )
                            MenuFlyoutItem(
                                text = { Text(stringResource(Res.string.settings_appearance_theme_light)) },
                                onClick = {
                                    isFlyoutVisible = false
                                    state.appearanceState.updateSettings {
                                        copy(theme = Theme.LIGHT)
                                    }
                                },
                            )
                        },
                        content = {
                            DropDownButton(
                                onClick = { isFlyoutVisible = !isFlyoutVisible },
                                content = {
                                    Text(
                                        when (LocalAppearanceSettings.current.theme) {
                                            Theme.SYSTEM -> stringResource(Res.string.settings_appearance_theme_auto)
                                            Theme.DARK -> stringResource(Res.string.settings_appearance_theme_dark)
                                            Theme.LIGHT -> stringResource(Res.string.settings_appearance_theme_light)
                                        },
                                    )
                                },
                            )
                        },
                        adaptivePlacement = true,
                        placement = FlyoutPlacement.BottomAlignedEnd,
                    )
                },
            )

            Expander(
                icon = null,
                expanded = state.appearanceState.expanded,
                onExpandedChanged = state.appearanceState::setExpanded,
                heading = {
                    Text(stringResource(Res.string.settings_status_appearance_title))
                },
                caption = {
                    Text(stringResource(Res.string.settings_status_appearance_subtitle))
                },
            ) {
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_avatar_shape))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_avatar_shape_description))
                    },
                    trailing = {
                        MenuFlyoutContainer(
                            flyout = {
                                MenuFlyoutItem(
                                    text = { Text(stringResource(Res.string.settings_appearance_avatar_shape_round)) },
                                    onClick = {
                                        state.appearanceState.updateSettings {
                                            copy(avatarShape = AvatarShape.CIRCLE)
                                        }
                                        isFlyoutVisible = false
                                    },
                                )
                                MenuFlyoutItem(
                                    text = { Text(stringResource(Res.string.settings_appearance_avatar_shape_square)) },
                                    onClick = {
                                        state.appearanceState.updateSettings {
                                            copy(avatarShape = AvatarShape.SQUARE)
                                        }
                                        isFlyoutVisible = false
                                    },
                                )
                            },
                            content = {
                                DropDownButton(
                                    onClick = { isFlyoutVisible = !isFlyoutVisible },
                                    content = {
                                        Text(
                                            when (LocalAppearanceSettings.current.avatarShape) {
                                                AvatarShape.CIRCLE ->
                                                    stringResource(
                                                        Res.string.settings_appearance_avatar_shape_round,
                                                    )
                                                AvatarShape.SQUARE ->
                                                    stringResource(
                                                        Res.string.settings_appearance_avatar_shape_square,
                                                    )
                                            },
                                        )
                                    },
                                )
                            },
                            adaptivePlacement = true,
                            placement = FlyoutPlacement.BottomAlignedEnd,
                        )
                    },
                )

                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_show_actions))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_show_actions_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.showActions,
                            {
                                state.appearanceState.updateSettings {
                                    copy(showActions = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                AnimatedVisibility(LocalAppearanceSettings.current.showActions) {
                    ExpanderItem(
                        heading = {
                            Text(stringResource(Res.string.settings_appearance_show_numbers))
                        },
                        caption = {
                            Text(stringResource(Res.string.settings_appearance_show_numbers_description))
                        },
                        trailing = {
                            Switcher(
                                checked = LocalAppearanceSettings.current.showNumbers,
                                {
                                    state.appearanceState.updateSettings {
                                        copy(showNumbers = it)
                                    }
                                },
                                textBefore = true,
                            )
                        },
                    )
                }
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_show_link_previews))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_show_link_previews_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.showLinkPreview,
                            {
                                state.appearanceState.updateSettings {
                                    copy(showLinkPreview = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                AnimatedVisibility(LocalAppearanceSettings.current.showLinkPreview) {
                    ExpanderItem(
                        heading = {
                            Text(stringResource(Res.string.settings_appearance_compat_link_previews))
                        },
                        caption = {
                            Text(stringResource(Res.string.settings_appearance_compat_link_previews_description))
                        },
                        trailing = {
                            Switcher(
                                checked = LocalAppearanceSettings.current.compatLinkPreview,
                                {
                                    state.appearanceState.updateSettings {
                                        copy(compatLinkPreview = it)
                                    }
                                },
                                textBefore = true,
                            )
                        },
                    )
                }
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_show_media))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_show_media_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.showMedia,
                            {
                                state.appearanceState.updateSettings {
                                    copy(showMedia = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                AnimatedVisibility(LocalAppearanceSettings.current.showMedia) {
                    ExpanderItem(
                        heading = {
                            Text(stringResource(Res.string.settings_appearance_show_cw_img))
                        },
                        caption = {
                            Text(stringResource(Res.string.settings_appearance_show_cw_img_description))
                        },
                        trailing = {
                            Switcher(
                                checked = LocalAppearanceSettings.current.showSensitiveContent,
                                {
                                    state.appearanceState.updateSettings {
                                        copy(showSensitiveContent = it)
                                    }
                                },
                                textBefore = true,
                            )
                        },
                    )
                }
                AnimatedVisibility(LocalAppearanceSettings.current.showMedia) {
                    ExpanderItem(
                        heading = {
                            Text(stringResource(Res.string.settings_appearance_expand_media))
                        },
                        caption = {
                            Text(stringResource(Res.string.settings_appearance_expand_media_description))
                        },
                        trailing = {
                            Switcher(
                                checked = LocalAppearanceSettings.current.expandMediaSize,
                                {
                                    state.appearanceState.updateSettings {
                                        copy(expandMediaSize = it)
                                    }
                                },
                                textBefore = true,
                            )
                        },
                    )
                }
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_video_autoplay))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_video_autoplay_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.videoAutoplay in listOf(VideoAutoplay.ALWAYS, VideoAutoplay.WIFI),
                            {
                                state.appearanceState.updateSettings {
                                    copy(
                                        videoAutoplay =
                                            if (it) VideoAutoplay.ALWAYS else VideoAutoplay.NEVER,
                                    )
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
            }

            Header(stringResource(Res.string.settings_about_title))
            Expander(
                icon = null,
                heading = {
                    Text(stringResource(Res.string.app_name))
                },
                caption = {
                    Text(System.getProperty("jpackage.app-version", "1.0.0"))
                },
                expanded = state.aboutExpanded,
                onExpandedChanged = { state.setAboutExpanded(it) },
            ) {
                CardExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_source_code))
                    },
                    caption = {
                        Text(
                            text = "https://github.com/DimensionDev/Flare",
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://github.com/DimensionDev/Flare")
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Brands.Github,
                            contentDescription = "GitHub",
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                CardExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_telegram))
                    },
                    caption = {
                        Text(
                            text = stringResource(resource = Res.string.settings_about_telegram_description),
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://t.me/+0UtcP6_qcDoyOWE1")
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Brands.Telegram,
                            contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                CardExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_line))
                    },
                    caption = {
                        Text(
                            text = stringResource(resource = Res.string.settings_about_line_description),
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://line.me/ti/g/hf95HyGJ9k")
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Brands.Line,
                            contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                CardExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_localization))
                    },
                    caption = {
                        Text(
                            text = stringResource(resource = Res.string.settings_about_localization_description),
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://crowdin.com/project/flareapp")
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Language,
                            contentDescription = stringResource(resource = Res.string.settings_about_localization),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                CardExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_privacy_policy))
                    },
                    caption = {
                        Text(
                            text = "https://legal.mask.io/maskbook",
                        )
                    },
                    onClick = {
                        uriHandler.openUri("https://legal.mask.io/maskbook/")
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Lock,
                            contentDescription = stringResource(resource = Res.string.settings_privacy_policy),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun presenter() =
    run {
        val accountState = accountsPresenter()
        val appearanceState = appearancePresenter()
        var aboutExpanded by remember { mutableStateOf(false) }

        object {
            val accountState = accountState
            val appearanceState = appearanceState

            val aboutExpanded = aboutExpanded

            fun setAboutExpanded(value: Boolean) {
                aboutExpanded = value
            }
        }
    }

@Composable
private fun appearancePresenter() =
    run {
        var expanded by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val settingsRepository = koinInject<SettingsRepository>()
        object {
            val expanded = expanded

            fun setExpanded(value: Boolean) {
                expanded = value
            }

            fun updateSettings(block: AppearanceSettings.() -> AppearanceSettings) {
                scope.launch {
                    settingsRepository.updateAppearanceSettings(block)
                }
            }
        }
    }

@Composable
private fun accountsPresenter(settingsRepository: SettingsRepository = koinInject()) =
    run {
        val scope = rememberCoroutineScope()
        var expanded by remember { mutableStateOf(false) }
        val activeAccountState = remember { ActiveAccountPresenter() }.invoke()
        val state =
            remember {
                AccountsPresenter()
            }.invoke()

        object : AccountsState by state, UserState by activeAccountState {
            val expanded = expanded

            fun setExpanded(value: Boolean) {
                expanded = value
            }

            fun logout(accountKey: MicroBlogKey) {
                accounts.onSuccess { accountList ->
                    if (accountList.size == 1) {
                        // is Last account
                        scope.launch {
                            settingsRepository.updateTabSettings {
                                TabSettings()
                            }
                        }
                    } else {
                        scope.launch {
                            settingsRepository.updateTabSettings {
                                copy(
                                    secondaryItems =
                                        secondaryItems?.filter {
                                            (it.account as? AccountType.Specific)?.accountKey != accountKey
                                        },
                                    mainTabs =
                                        mainTabs.filter {
                                            (it.account as? AccountType.Specific)?.accountKey != accountKey
                                        },
                                )
                            }
                        }
                    }
                }
                removeAccount(accountKey)
            }
        }
    }
