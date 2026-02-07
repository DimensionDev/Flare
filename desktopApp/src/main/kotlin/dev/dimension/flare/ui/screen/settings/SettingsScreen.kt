package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Line
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.solid.AngleRight
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleXmark
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.SupportedLocales
import dev.dimension.flare.action_export
import dev.dimension.flare.action_import
import dev.dimension.flare.add_account
import dev.dimension.flare.app_name
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.AppSettings
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.delete
import dev.dimension.flare.edit
import dev.dimension.flare.home_login
import dev.dimension.flare.import_completed
import dev.dimension.flare.import_confirmation_message
import dev.dimension.flare.import_confirmation_title
import dev.dimension.flare.import_error
import dev.dimension.flare.ok
import dev.dimension.flare.remove_account
import dev.dimension.flare.save_completed
import dev.dimension.flare.save_error
import dev.dimension.flare.settings_about_line
import dev.dimension.flare.settings_about_line_description
import dev.dimension.flare.settings_about_localization
import dev.dimension.flare.settings_about_localization_description
import dev.dimension.flare.settings_about_source_code
import dev.dimension.flare.settings_about_telegram
import dev.dimension.flare.settings_about_telegram_description
import dev.dimension.flare.settings_about_title
import dev.dimension.flare.settings_accounts_title
import dev.dimension.flare.settings_ai_config_description
import dev.dimension.flare.settings_ai_config_enable_tldr
import dev.dimension.flare.settings_ai_config_entable_translation
import dev.dimension.flare.settings_ai_config_server
import dev.dimension.flare.settings_ai_config_server_hint
import dev.dimension.flare.settings_ai_config_server_self_host_description
import dev.dimension.flare.settings_ai_config_title
import dev.dimension.flare.settings_ai_config_tldr_description
import dev.dimension.flare.settings_ai_config_translation_description
import dev.dimension.flare.settings_appearance_absolute_timestamp
import dev.dimension.flare.settings_appearance_absolute_timestamp_description
import dev.dimension.flare.settings_appearance_avatar_shape
import dev.dimension.flare.settings_appearance_avatar_shape_description
import dev.dimension.flare.settings_appearance_avatar_shape_round
import dev.dimension.flare.settings_appearance_avatar_shape_square
import dev.dimension.flare.settings_appearance_compat_link_previews
import dev.dimension.flare.settings_appearance_compat_link_previews_description
import dev.dimension.flare.settings_appearance_expand_media
import dev.dimension.flare.settings_appearance_expand_media_description
import dev.dimension.flare.settings_appearance_full_width_post
import dev.dimension.flare.settings_appearance_full_width_post_description
import dev.dimension.flare.settings_appearance_hide_reposts
import dev.dimension.flare.settings_appearance_hide_reposts_description
import dev.dimension.flare.settings_appearance_hide_replies
import dev.dimension.flare.settings_appearance_hide_replies_description
import dev.dimension.flare.settings_appearance_post_action_style
import dev.dimension.flare.settings_appearance_post_action_style_description
import dev.dimension.flare.settings_appearance_post_action_style_hidden
import dev.dimension.flare.settings_appearance_post_action_style_left_aligned
import dev.dimension.flare.settings_appearance_post_action_style_right_aligned
import dev.dimension.flare.settings_appearance_post_action_style_stretch
import dev.dimension.flare.settings_appearance_show_compose_in_home_timeline
import dev.dimension.flare.settings_appearance_show_compose_in_home_timeline_description
import dev.dimension.flare.settings_appearance_show_cw_img
import dev.dimension.flare.settings_appearance_show_cw_img_description
import dev.dimension.flare.settings_appearance_show_link_previews
import dev.dimension.flare.settings_appearance_show_link_previews_description
import dev.dimension.flare.settings_appearance_show_media
import dev.dimension.flare.settings_appearance_show_media_description
import dev.dimension.flare.settings_appearance_show_numbers
import dev.dimension.flare.settings_appearance_show_numbers_description
import dev.dimension.flare.settings_appearance_show_platform_logo
import dev.dimension.flare.settings_appearance_show_platform_logo_description
import dev.dimension.flare.settings_appearance_theme
import dev.dimension.flare.settings_appearance_theme_auto
import dev.dimension.flare.settings_appearance_theme_dark
import dev.dimension.flare.settings_appearance_theme_description
import dev.dimension.flare.settings_appearance_theme_light
import dev.dimension.flare.settings_appearance_title
import dev.dimension.flare.settings_language_description
import dev.dimension.flare.settings_language_title
import dev.dimension.flare.settings_local_history_description
import dev.dimension.flare.settings_local_history_title
import dev.dimension.flare.settings_privacy_policy
import dev.dimension.flare.settings_rss_management_description
import dev.dimension.flare.settings_rss_management_title
import dev.dimension.flare.settings_status_appearance_subtitle
import dev.dimension.flare.settings_status_appearance_title
import dev.dimension.flare.settings_storage_app_log
import dev.dimension.flare.settings_storage_app_log_description
import dev.dimension.flare.settings_storage_clear_database
import dev.dimension.flare.settings_storage_clear_database_description
import dev.dimension.flare.settings_storage_clear_image_cache
import dev.dimension.flare.settings_storage_clear_image_cache_description
import dev.dimension.flare.settings_storage_export_data
import dev.dimension.flare.settings_storage_export_data_description
import dev.dimension.flare.settings_storage_import_data
import dev.dimension.flare.settings_storage_import_data_description
import dev.dimension.flare.settings_storage_subtitle
import dev.dimension.flare.settings_storage_title
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.ComposeInAppNotification
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.ExportDataPresenter
import dev.dimension.flare.ui.presenter.ImportDataPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.FlareServerProviderPresenter
import dev.dimension.flare.ui.presenter.settings.StoragePresenter
import dev.dimension.flare.ui.presenter.settings.StorageState
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.HyperlinkButton
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

@Composable
internal fun SettingsScreen(
    toLogin: () -> Unit,
    toLocalCache: () -> Unit,
    toAppLog: () -> Unit,
    toRSSManagement: () -> Unit,
) {
    val window = LocalComposeWindow.current
    val state by producePresenter {
        presenter(
            onExportFilePicker = {
                java.awt
                    .FileDialog(window, "Export Data", java.awt.FileDialog.SAVE)
                    .apply {
                        file = "flare_data_export.json"
                        isVisible = true
                    }.let {
                        if (it.directory != null && it.file != null) {
                            java.io.File(it.directory, it.file)
                        } else {
                            null
                        }
                    }
            },
            onImportFilePicker = {
                java.awt
                    .FileDialog(window, "Import Data", java.awt.FileDialog.LOAD)
                    .apply {
                        isVisible = true
                    }.let {
                        if (it.directory != null && it.file != null) {
                            java.io.File(it.directory, it.file)
                        } else {
                            null
                        }
                    }
            },
        )
    }

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

            CardExpanderItem(
                icon = null,
                heading = {
                    Text(stringResource(Res.string.settings_language_title))
                },
                caption = {
                    Text(stringResource(Res.string.settings_language_description))
                },
                trailing = {
                    var isFlyoutVisible by remember { mutableStateOf(false) }
                    DropDownButton(
                        onClick = { isFlyoutVisible = !isFlyoutVisible },
                        content = {
                            Text(Locale.getDefault().displayName)
                        },
                    )
                    MenuFlyout(
                        visible = isFlyoutVisible,
                        onDismissRequest = { isFlyoutVisible = false },
                        modifier = Modifier.heightIn(max = 200.dp),
                        placement = FlyoutPlacement.BottomAlignedEnd,
                    ) {
                        SupportedLocales.tags.forEach {
                            MenuFlyoutItem(
                                text = {
                                    Text(
                                        Locale.forLanguageTag(it).let {
                                            it.getDisplayName(it)
                                        },
                                    )
                                },
                                onClick = {
                                    state.setLanguage(it)
                                    isFlyoutVisible = false
                                },
                            )
                        }
                    }
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
                        Text(stringResource(Res.string.settings_appearance_show_compose_in_home_timeline))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_show_compose_in_home_timeline_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.showComposeInHomeTimeline,
                            {
                                state.appearanceState.updateSettings {
                                    copy(showComposeInHomeTimeline = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_full_width_post))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_full_width_post_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.fullWidthPost,
                            {
                                state.appearanceState.updateSettings {
                                    copy(fullWidthPost = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
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
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_post_action_style))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_post_action_style_description))
                    },
                    trailing = {
                        val items =
                            remember {
                                persistentMapOf(
                                    PostActionStyle.Hidden to Res.string.settings_appearance_post_action_style_hidden,
                                    PostActionStyle.LeftAligned to Res.string.settings_appearance_post_action_style_left_aligned,
                                    PostActionStyle.RightAligned to Res.string.settings_appearance_post_action_style_right_aligned,
                                    PostActionStyle.Stretch to Res.string.settings_appearance_post_action_style_stretch,
                                )
                            }
                        MenuFlyoutContainer(
                            flyout = {
                                items.forEach { (key, value) ->
                                    MenuFlyoutItem(
                                        onClick = {
                                            state.appearanceState.updateSettings {
                                                copy(postActionStyle = key)
                                            }
                                            isFlyoutVisible = false
                                        },
                                        text = { Text(stringResource(value)) },
                                    )
                                }
                            },
                            content = {
                                DropDownButton(
                                    onClick = { isFlyoutVisible = !isFlyoutVisible },
                                    content = {
                                        items[LocalAppearanceSettings.current.postActionStyle]?.let {
                                            Text(stringResource(it))
                                        }
                                    },
                                )
                            },
                            adaptivePlacement = true,
                            placement = FlyoutPlacement.BottomAlignedEnd,
                        )
                    },
                )
                ExpanderItemSeparator()
                AnimatedVisibility(LocalAppearanceSettings.current.postActionStyle != PostActionStyle.Hidden) {
                    Column {
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
                        ExpanderItemSeparator()
                    }
                }
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_absolute_timestamp))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_absolute_timestamp_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.absoluteTimestamp,
                            {
                                state.appearanceState.updateSettings {
                                    copy(absoluteTimestamp = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_show_platform_logo))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_show_platform_logo_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.showPlatformLogo,
                            {
                                state.appearanceState.updateSettings {
                                    copy(showPlatformLogo = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
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
                ExpanderItemSeparator()
                AnimatedVisibility(LocalAppearanceSettings.current.showLinkPreview) {
                    Column {
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
                        ExpanderItemSeparator()
                    }
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
                ExpanderItemSeparator()
                AnimatedVisibility(LocalAppearanceSettings.current.showMedia) {
                    Column {
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
                        ExpanderItemSeparator()
                    }
                }
                AnimatedVisibility(LocalAppearanceSettings.current.showMedia) {
                    Column {
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
                        ExpanderItemSeparator()
                    }
                }
                // Hide reposts toggle: remove reposts from timelines (keeps quoted posts and search results)
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_hide_reposts))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_hide_reposts_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.hideReposts,
                            {
                                state.appearanceState.updateSettings {
                                    copy(hideReposts = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
                // Hide replies toggle: remove replies by others from timelines
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_hide_replies))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_hide_replies_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.hideReplies,
                            {
                                state.appearanceState.updateSettings {
                                    copy(hideReplies = it)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
            }

            Header(stringResource(Res.string.settings_storage_title))
            AnimatedVisibility(
                state.accountState.activeAccount.isSuccess,
            ) {
                CardExpanderItem(
                    onClick = toLocalCache,
                    heading = {
                        Text(stringResource(Res.string.settings_local_history_title))
                    },
                    trailing = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.AngleRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_local_history_description))
                    },
                    icon = null,
                )
            }
            CardExpanderItem(
                onClick = toRSSManagement,
                heading = {
                    Text(stringResource(Res.string.settings_rss_management_title))
                },
                trailing = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.AngleRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                },
                caption = {
                    Text(stringResource(Res.string.settings_rss_management_description))
                },
                icon = null,
            )
            Expander(
                expanded = state.storageState.expanded,
                onExpandedChanged = state.storageState::setExpanded,
                heading = {
                    Text(stringResource(Res.string.settings_storage_title))
                },
                caption = {
                    Text(stringResource(Res.string.settings_storage_subtitle))
                },
                icon = null,
            ) {
                CardExpanderItem(
                    onClick = toAppLog,
                    heading = {
                        Text(stringResource(Res.string.settings_storage_app_log))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_storage_app_log_description))
                    },
                    trailing = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.AngleRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    },
                )

                ExpanderItem(
                    heading = {
                        Text(text = stringResource(Res.string.settings_storage_clear_image_cache))
                    },
                    caption = {
                        Text(
                            text =
                                stringResource(
                                    Res.string.settings_storage_clear_image_cache_description,
                                    state.storageState.imageCacheSize,
                                ),
                        )
                    },
                    trailing = {
                        Button(
                            onClick = {
                                state.storageState.clearImageCache()
                            },
                        ) {
                            Text(stringResource(Res.string.delete))
                        }
                    },
                )

                ExpanderItem(
                    heading = {
                        Text(text = stringResource(Res.string.settings_storage_clear_database))
                    },
                    caption = {
                        Text(
                            text =
                                stringResource(
                                    Res.string.settings_storage_clear_database_description,
                                    state.storageState.userCount,
                                    state.storageState.statusCount,
                                ),
                        )
                    },
                    trailing = {
                        Button(
                            onClick = {
                                state.storageState.clearCacheDatabase()
                            },
                        ) {
                            Text(stringResource(Res.string.delete))
                        }
                    },
                )

                ExpanderItemSeparator()

                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_storage_export_data))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_storage_export_data_description))
                    },
                    trailing = {
                        Button(
                            onClick = {
                                state.storageState.export()
                            },
                        ) {
                            Text(stringResource(Res.string.action_export))
                        }
                    },
                )

                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_storage_import_data))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_storage_import_data_description))
                    },
                    trailing = {
                        Button(
                            onClick = {
                                state.storageState.import()
                            },
                        ) {
                            Text(stringResource(Res.string.action_import))
                        }
                    },
                )
            }

            Header(stringResource(Res.string.settings_ai_config_title))
            ContentDialog(
                title = stringResource(Res.string.settings_ai_config_server),
                visible = state.aiConfigState.showServerDialog,
                content = {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_ai_config_server_self_host_description),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        TextField(
                            state.aiConfigState.serverText,
                            placeholder = { Text(stringResource(Res.string.settings_ai_config_server_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            trailing = {
                                state.aiConfigState.serverValidation
                                    .onSuccess {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.CircleCheck,
                                            contentDescription = null,
                                            tint = FluentTheme.colors.system.neutral,
                                        )
                                    }.onError {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.CircleXmark,
                                            contentDescription = null,
                                            tint = FluentTheme.colors.system.critical,
                                        )
                                    }.onLoading {
                                        ProgressRing(
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                            },
                        )
                    }
                },
                primaryButtonText = stringResource(Res.string.ok),
                closeButtonText = stringResource(Res.string.cancel),
                onButtonClick = {
                    when (it) {
                        ContentDialogButton.Primary -> {
                            if (state.aiConfigState.serverValidation.isSuccess) {
                                state.aiConfigState.setShowServerDialog(false)
                                state.aiConfigState.confirm()
                            }
                        }
                        else -> {
                            state.aiConfigState.setShowServerDialog(false)
                        }
                    }
                },
            )

            ContentDialog(
                title = stringResource(Res.string.import_confirmation_title),
                visible = state.storageState.showImportConfirmation,
                content = {
                    Text(
                        text = stringResource(Res.string.import_confirmation_message),
                        modifier = Modifier.padding(16.dp),
                    )
                },
                primaryButtonText = stringResource(Res.string.ok),
                closeButtonText = stringResource(Res.string.cancel),
                onButtonClick = {
                    when (it) {
                        ContentDialogButton.Primary -> {
                            state.storageState.confirmImport()
                        }
                        else -> {
                            state.storageState.cancelImport()
                        }
                    }
                },
            )

            Expander(
                state.aiConfigState.expanded,
                onExpandedChanged = state.aiConfigState::setExpanded,
                heading = {
                    Text(stringResource(Res.string.settings_ai_config_title))
                },
                caption = {
                    Text(stringResource(Res.string.settings_ai_config_description))
                },
                icon = null,
            ) {
                ExpanderItem(
//                    onClick = {
//                        state.aiConfigState.setShowServerDialog(true)
//                    },
                    heading = {
                        Text(stringResource(Res.string.settings_ai_config_server))
                    },
                    caption = {
                        state.aiConfigState.currentServer.onSuccess {
                            Text(it)
                        }
                    },
                    trailing = {
                        Button(
                            onClick = {
                                state.aiConfigState.setShowServerDialog(true)
                            },
                        ) {
                            Text(stringResource(Res.string.edit))
                        }
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_ai_config_entable_translation))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_ai_config_translation_description))
                    },
                    trailing = {
                        Switcher(
                            checked = state.aiConfigState.aiConfig.translation,
                            {
                                state.aiConfigState.update { copy(translation = it) }
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_ai_config_enable_tldr))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_ai_config_tldr_description))
                    },
                    trailing = {
                        Switcher(
                            checked = state.aiConfigState.aiConfig.tldr,
                            {
                                state.aiConfigState.update { copy(tldr = it) }
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
                ExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_source_code))
                    },
                    trailing = {
                        HyperlinkButton(
                            "https://github.com/DimensionDev/Flare",
                        ) {
                            Text(
                                text = "https://github.com/DimensionDev/Flare",
                                maxLines = 1,
                            )
                        }
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Brands.Github,
                            contentDescription = "GitHub",
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_telegram))
                    },
                    caption = {
                        Text(text = stringResource(resource = Res.string.settings_about_telegram_description))
                    },
                    trailing = {
                        HyperlinkButton(
                            "https://t.me/+0UtcP6_qcDoyOWE1",
                        ) {
                            Text(
                                text = "https://t.me/+0UtcP6_qcDoyOWE1",
                                maxLines = 1,
                            )
                        }
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Brands.Telegram,
                            contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_line))
                    },
                    caption = {
                        Text(text = stringResource(resource = Res.string.settings_about_line_description))
                    },
                    trailing = {
                        HyperlinkButton(
                            "https://line.me/ti/g/hf95HyGJ9k",
                        ) {
                            Text(
                                text = "https://line.me/ti/g/hf95HyGJ9k",
                                maxLines = 1,
                            )
                        }
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Brands.Line,
                            contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_about_localization))
                    },
                    caption = {
                        Text(text = stringResource(resource = Res.string.settings_about_localization_description))
                    },
                    trailing = {
                        HyperlinkButton(
                            "https://crowdin.com/project/flareapp",
                        ) {
                            Text(
                                text = "https://crowdin.com/project/flareapp",
                                maxLines = 1,
                            )
                        }
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Language,
                            contentDescription = stringResource(resource = Res.string.settings_about_localization),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(text = stringResource(resource = Res.string.settings_privacy_policy))
                    },
                    trailing = {
                        HyperlinkButton(
                            "https://legal.mask.io/maskbook",
                        ) {
                            Text(
                                text = "https://legal.mask.io/maskbook",
                                maxLines = 1,
                            )
                        }
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
private fun presenter(
    onExportFilePicker: () -> File?,
    onImportFilePicker: () -> File?,
) = run {
    val scope = rememberCoroutineScope()
    val settingsRepository = koinInject<SettingsRepository>()
    val accountState = accountsPresenter()
    val appearanceState = appearancePresenter()
    val storageState = storagePresenter(onExportFilePicker, onImportFilePicker)
    val aiConfigState = aiConfigPresenter()
    var aboutExpanded by remember { mutableStateOf(false) }

    object {
        val accountState = accountState
        val appearanceState = appearanceState
        val aiConfigState = aiConfigState
        val storageState = storageState
        val aboutExpanded = aboutExpanded

        fun setAboutExpanded(value: Boolean) {
            aboutExpanded = value
        }

        fun setLanguage(tag: String) {
            scope.launch {
                settingsRepository.updateAppSettings {
                    copy(language = tag)
                }
            }
        }
    }
}

@Composable
private fun storagePresenter(
    onExportFilePicker: () -> File?,
    onImportFilePicker: () -> File?,
) = run {
    var refreshKey by remember { mutableStateOf(0) }
    val state = remember { StoragePresenter() }.invoke()

    val notification = koinInject<ComposeInAppNotification>()
    val exportPresenter = remember { ExportDataPresenter() }
    val exportState = exportPresenter.body()
    val scope = rememberCoroutineScope()

    var importJson by remember { mutableStateOf<String?>(null) }
    val importPresenter = remember(importJson) { importJson?.let { ImportDataPresenter(it) } }
    val importState = importPresenter?.body()

    var showImportConfirmation by remember { mutableStateOf(false) }
    var pendingImportFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(importState) {
        importState?.let {
            try {
                it.import()
                notification.message(Res.string.import_completed)
            } catch (e: Exception) {
                notification.message(Res.string.import_error, success = false)
            } finally {
                importJson = null
                refreshKey++
            }
        }
    }

    var imageCacheSize by remember(refreshKey) {
        mutableLongStateOf(
            SingletonImageLoader
                .get(PlatformContext.INSTANCE)
                .diskCache
                ?.size
                ?.div(1024L * 1024L) ?: 0L,
        )
    }
    var expanded by remember { mutableStateOf(false) }
    object : StorageState by state {
        val expanded = expanded
        val imageCacheSize = imageCacheSize
        val showImportConfirmation = showImportConfirmation

        fun clearImageCache() {
            SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache?.clear()
            refreshKey++
        }

        fun clearCacheDatabase() {
            state.clearCache()
        }

        fun export() {
            scope.launch {
                try {
                    val json = exportState.export()
                    onExportFilePicker()?.let { file ->
                        file.writeText(json)
                        notification.message(Res.string.save_completed)
                    }
                } catch (e: Exception) {
                    notification.message(Res.string.save_error, success = false)
                }
            }
        }

        fun import() {
            onImportFilePicker()?.let { file ->
                pendingImportFile = file
                showImportConfirmation = true
            }
        }

        fun confirmImport() {
            pendingImportFile?.let { file ->
                importJson = file.readText()
            }
            showImportConfirmation = false
            pendingImportFile = null
        }

        fun cancelImport() {
            showImportConfirmation = false
            pendingImportFile = null
        }

        fun setExpanded(value: Boolean) {
            expanded = value
            if (value) {
                refreshKey++
            }
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
        var expanded by remember { mutableStateOf(false) }
        val activeAccountState = remember { ActiveAccountPresenter() }.invoke()
        val state =
            remember {
                AccountManagementPresenter()
            }.invoke()

        object : AccountManagementPresenter.State by state, UserState by activeAccountState {
            val expanded = expanded

            fun setExpanded(value: Boolean) {
                expanded = value
            }
        }
    }

@OptIn(FlowPreview::class)
@Composable
private fun aiConfigPresenter(settingsRepository: SettingsRepository = koinInject<SettingsRepository>()) =
    run {
        var expanded by remember { mutableStateOf(false) }
        var showServerDialog by remember { mutableStateOf(false) }
        val serverText = rememberTextFieldState()
        val scope = rememberCoroutineScope()
        val state = remember { FlareServerProviderPresenter() }.invoke()
        val aiConfig by remember { settingsRepository.appSettings.map { it.aiConfig } }
            .collectAsState(AppSettings.AiConfig())
        state.currentServer.onSuccess { currentServer ->
            LaunchedEffect(currentServer) {
                serverText.edit {
                    delete(0, length)
                    append(currentServer)
                }
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { serverText.text }
                .distinctUntilChanged()
                .debounce(666L)
                .collectLatest {
                    state.checkServer(it.toString())
                }
        }

        object : FlareServerProviderPresenter.State by state {
            val showServerDialog = showServerDialog
            val serverText = serverText
            val aiConfig = aiConfig
            val expanded = expanded

            fun setExpanded(value: Boolean) {
                expanded = value
            }

            fun update(block: AppSettings.AiConfig.() -> AppSettings.AiConfig) {
                scope.launch {
                    settingsRepository.updateAppSettings { copy(aiConfig = block.invoke(this.aiConfig)) }
                }
            }

            fun setShowServerDialog(value: Boolean) {
                showServerDialog = value
            }
        }
    }
