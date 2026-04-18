package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import compose.icons.fontawesomeicons.brands.Discord
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.solid.AngleRight
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.action_export
import dev.dimension.flare.action_import
import dev.dimension.flare.add_account
import dev.dimension.flare.app_name
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.delete
import dev.dimension.flare.edit
import dev.dimension.flare.home_login
import dev.dimension.flare.import_completed
import dev.dimension.flare.import_confirmation_message
import dev.dimension.flare.import_confirmation_title
import dev.dimension.flare.import_error
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ok
import dev.dimension.flare.remove_account
import dev.dimension.flare.save_completed
import dev.dimension.flare.save_error
import dev.dimension.flare.settings_about_discord
import dev.dimension.flare.settings_about_discord_description
import dev.dimension.flare.settings_about_localization
import dev.dimension.flare.settings_about_localization_description
import dev.dimension.flare.settings_about_source_code
import dev.dimension.flare.settings_about_telegram
import dev.dimension.flare.settings_about_telegram_description
import dev.dimension.flare.settings_about_title
import dev.dimension.flare.settings_accounts_remove_confirm
import dev.dimension.flare.settings_accounts_title
import dev.dimension.flare.settings_ai_config_api_key
import dev.dimension.flare.settings_ai_config_api_key_hint
import dev.dimension.flare.settings_ai_config_description
import dev.dimension.flare.settings_ai_config_enable_pre_translation
import dev.dimension.flare.settings_ai_config_enable_tldr
import dev.dimension.flare.settings_ai_config_model
import dev.dimension.flare.settings_ai_config_model_description
import dev.dimension.flare.settings_ai_config_model_loading
import dev.dimension.flare.settings_ai_config_model_manual_input
import dev.dimension.flare.settings_ai_config_model_select
import dev.dimension.flare.settings_ai_config_pre_translation_description
import dev.dimension.flare.settings_ai_config_reasoning_effort
import dev.dimension.flare.settings_ai_config_reasoning_effort_default
import dev.dimension.flare.settings_ai_config_reasoning_effort_description
import dev.dimension.flare.settings_ai_config_reasoning_effort_high
import dev.dimension.flare.settings_ai_config_reasoning_effort_low
import dev.dimension.flare.settings_ai_config_reasoning_effort_medium
import dev.dimension.flare.settings_ai_config_server
import dev.dimension.flare.settings_ai_config_server_hint
import dev.dimension.flare.settings_ai_config_server_url_requirement
import dev.dimension.flare.settings_ai_config_title
import dev.dimension.flare.settings_ai_config_tldr_description
import dev.dimension.flare.settings_ai_config_tldr_prompt
import dev.dimension.flare.settings_ai_config_translate_prompt
import dev.dimension.flare.settings_ai_config_translate_provider
import dev.dimension.flare.settings_ai_config_translate_provider_ai
import dev.dimension.flare.settings_ai_config_translate_provider_deepl
import dev.dimension.flare.settings_ai_config_translate_provider_deepl_api_key
import dev.dimension.flare.settings_ai_config_translate_provider_deepl_use_pro
import dev.dimension.flare.settings_ai_config_translate_provider_deepl_use_pro_description
import dev.dimension.flare.settings_ai_config_translate_provider_description
import dev.dimension.flare.settings_ai_config_translate_provider_google_cloud
import dev.dimension.flare.settings_ai_config_translate_provider_google_cloud_api_key
import dev.dimension.flare.settings_ai_config_translate_provider_google_web
import dev.dimension.flare.settings_ai_config_translate_provider_libretranslate
import dev.dimension.flare.settings_ai_config_translate_provider_libretranslate_api_key
import dev.dimension.flare.settings_ai_config_translate_provider_libretranslate_base_url
import dev.dimension.flare.settings_ai_config_type
import dev.dimension.flare.settings_ai_config_type_description
import dev.dimension.flare.settings_ai_config_type_on_device
import dev.dimension.flare.settings_ai_config_type_openai
import dev.dimension.flare.settings_ai_config_value_empty_placeholder
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
import dev.dimension.flare.settings_appearance_timeline_display_mode
import dev.dimension.flare.settings_appearance_timeline_display_mode_card
import dev.dimension.flare.settings_appearance_timeline_display_mode_description
import dev.dimension.flare.settings_appearance_timeline_display_mode_gallery
import dev.dimension.flare.settings_appearance_timeline_display_mode_plain
import dev.dimension.flare.settings_appearance_title
import dev.dimension.flare.settings_appearance_video_autoplay
import dev.dimension.flare.settings_appearance_video_autoplay_description
import dev.dimension.flare.settings_draft_box_description
import dev.dimension.flare.settings_draft_box_title
import dev.dimension.flare.settings_language_description
import dev.dimension.flare.settings_language_title
import dev.dimension.flare.settings_local_history_description
import dev.dimension.flare.settings_local_history_title
import dev.dimension.flare.settings_nostr_relays_manage
import dev.dimension.flare.settings_privacy_policy
import dev.dimension.flare.settings_rss_management_description
import dev.dimension.flare.settings_rss_management_title
import dev.dimension.flare.settings_services_title
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
import dev.dimension.flare.settings_translation_ai_test_action
import dev.dimension.flare.settings_translation_ai_test_description
import dev.dimension.flare.settings_translation_ai_test_original
import dev.dimension.flare.settings_translation_ai_test_result
import dev.dimension.flare.settings_translation_ai_test_title
import dev.dimension.flare.settings_translation_auto_excluded_languages
import dev.dimension.flare.settings_translation_description
import dev.dimension.flare.settings_translation_title
import dev.dimension.flare.tab_settings_drag
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.ComposeInAppNotification
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.ExportDataPresenter
import dev.dimension.flare.ui.presenter.ImportDataPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AiConfigPresenter
import dev.dimension.flare.ui.presenter.settings.AiReasoningEffortOption
import dev.dimension.flare.ui.presenter.settings.AiTranslationTestPresenter
import dev.dimension.flare.ui.presenter.settings.AiTypeOption
import dev.dimension.flare.ui.presenter.settings.StoragePresenter
import dev.dimension.flare.ui.presenter.settings.StorageState
import dev.dimension.flare.ui.presenter.settings.TranslateProviderOption
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AutoSuggestBoxDefaults
import io.github.composefluent.component.AutoSuggestionBox
import io.github.composefluent.component.Button
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.HyperlinkButton
import io.github.composefluent.component.ListItem
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableColumn
import java.io.File
import java.util.Locale

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsScreen(
    toLogin: () -> Unit,
    toDraftBox: () -> Unit,
    toLocalCache: () -> Unit,
    toAppLog: () -> Unit,
    toRSSManagement: () -> Unit,
    toNostrRelays: (MicroBlogKey) -> Unit,
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
    var pendingDeleteAccountKey by remember { mutableStateOf<MicroBlogKey?>(null) }
    var pendingDeleteAccountLabel by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    FlareScrollBar(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(LocalWindowPadding.current)
                    .padding(horizontal = screenHorizontalPadding),
        ) {
            state.accountState.accounts
                .onSuccess { accounts ->
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
                                    Text(text = activeAccount.handle.canonical)
                                },
                            ) {
                                ReorderableColumn(
                                    list = accounts,
                                    onSettle = { fromIndex, toIndex ->
                                        state.accountState.moveItem(fromIndex, toIndex)
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) { _, account, _ ->
                                    key(account.account.accountKey.toString()) {
                                        ReorderableItem {
                                            AccountItem(
                                                account.profile,
                                                onClick = {
                                                    state.accountState.setActiveAccount(it)
                                                },
                                                toLogin = toLogin,
                                                trailingContent = { user ->
                                                    Row(
                                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        if (user == null) {
                                                            SubtleButton(
                                                                onClick = {
                                                                    pendingDeleteAccountKey = account.account.accountKey
                                                                    pendingDeleteAccountLabel = account.account.accountKey.toString()
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
                                                                selected = activeAccount.key == user.key,
                                                                onClick = {
                                                                    state.accountState.setActiveAccount(user.key)
                                                                },
                                                            )
                                                            SubtleButton(
                                                                modifier = Modifier.draggableHandle(),
                                                                onClick = {},
                                                                iconOnly = true,
                                                            ) {
                                                                FAIcon(
                                                                    FontAwesomeIcons.Solid.Bars,
                                                                    contentDescription = stringResource(Res.string.tab_settings_drag),
                                                                )
                                                            }
                                                            MenuFlyoutContainer(
                                                                flyout = {
                                                                    if (account.account.platformType == PlatformType.Nostr) {
                                                                        MenuFlyoutItem(
                                                                            text = {
                                                                                Text(
                                                                                    stringResource(Res.string.settings_nostr_relays_manage),
                                                                                )
                                                                            },
                                                                            onClick = {
                                                                                toNostrRelays(user.key)
                                                                            },
                                                                            icon = {
                                                                                FAIcon(
                                                                                    FontAwesomeIcons.Solid.List,
                                                                                    contentDescription =
                                                                                        stringResource(
                                                                                            Res.string.settings_nostr_relays_manage,
                                                                                        ),
                                                                                )
                                                                            },
                                                                        )
                                                                    }
                                                                    MenuFlyoutItem(
                                                                        text = {
                                                                            Text(
                                                                                stringResource(Res.string.remove_account),
                                                                                color = FluentTheme.colors.system.critical,
                                                                            )
                                                                        },
                                                                        onClick = {
                                                                            pendingDeleteAccountKey = user.key
                                                                            pendingDeleteAccountLabel = user.handle.canonical
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

            ContentDialog(
                title = stringResource(Res.string.remove_account),
                visible = pendingDeleteAccountKey != null,
                content = {
                    Text(
                        text =
                            stringResource(
                                Res.string.settings_accounts_remove_confirm,
                                pendingDeleteAccountLabel ?: pendingDeleteAccountKey.toString(),
                            ),
                        modifier = Modifier.padding(16.dp),
                    )
                },
                primaryButtonText = stringResource(Res.string.delete),
                closeButtonText = stringResource(Res.string.cancel),
                onButtonClick = {
                    when (it) {
                        ContentDialogButton.Primary -> {
                            pendingDeleteAccountKey?.let(state.accountState::deleteItem)
                            pendingDeleteAccountKey = null
                            pendingDeleteAccountLabel = null
                        }

                        else -> {
                            pendingDeleteAccountKey = null
                            pendingDeleteAccountLabel = null
                        }
                    }
                },
            )

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
                        BuildConfig.supportedLocaleTags.forEach {
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
                                                AvatarShape.CIRCLE -> {
                                                    stringResource(
                                                        Res.string.settings_appearance_avatar_shape_round,
                                                    )
                                                }

                                                AvatarShape.SQUARE -> {
                                                    stringResource(
                                                        Res.string.settings_appearance_avatar_shape_square,
                                                    )
                                                }
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
                        Text(stringResource(Res.string.settings_appearance_timeline_display_mode))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_timeline_display_mode_description))
                    },
                    trailing = {
                        val items =
                            remember {
                                persistentMapOf(
                                    TimelineDisplayMode.Card to Res.string.settings_appearance_timeline_display_mode_card,
                                    TimelineDisplayMode.Plain to Res.string.settings_appearance_timeline_display_mode_plain,
                                    TimelineDisplayMode.Gallery to Res.string.settings_appearance_timeline_display_mode_gallery,
                                )
                            }
                        MenuFlyoutContainer(
                            flyout = {
                                items.forEach { (key, value) ->
                                    MenuFlyoutItem(
                                        onClick = {
                                            state.appearanceState.updateSettings {
                                                copy(timelineDisplayMode = key)
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
                                        items[LocalAppearanceSettings.current.timelineDisplayMode]?.let {
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
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_video_autoplay))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_video_autoplay_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalAppearanceSettings.current.videoAutoplay == VideoAutoplay.ALWAYS,
                            {
                                state.appearanceState.updateSettings {
                                    copy(videoAutoplay = if (it) VideoAutoplay.ALWAYS else VideoAutoplay.NEVER)
                                }
                            },
                            textBefore = true,
                        )
                    },
                )
            }

            Header(stringResource(Res.string.settings_storage_title))
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
                onClick = toDraftBox,
                heading = {
                    Text(stringResource(Res.string.settings_draft_box_title))
                },
                trailing = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.AngleRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                },
                caption = {
                    Text(stringResource(Res.string.settings_draft_box_description))
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
                AnimatedVisibility(state.storageState.isClearingImageCache) {
                    ProgressBar(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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
                AnimatedVisibility(state.storageState.isClearingDatabaseCache) {
                    ProgressBar(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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

            Header(stringResource(Res.string.settings_services_title))
            state.aiConfigState.textEditDialog?.let { dialog ->
                val textState = rememberTextFieldState()
                var isSuggestionExpanded by remember(dialog) { mutableStateOf(false) }
                LaunchedEffect(dialog) {
                    textState.edit {
                        replace(0, length, dialog.value)
                    }
                }
                val filteredSuggestions by remember(dialog) {
                    snapshotFlow { textState.text.toString() }
                        .map { query ->
                            dialog.suggestions.filter { item ->
                                query.isBlank() || item.contains(query, ignoreCase = true)
                            }
                        }
                }.collectAsState(dialog.suggestions)
                ContentDialog(
                    title = dialog.title,
                    visible = true,
                    content = {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            if (dialog.placeholder.isNotBlank()) {
                                Text(
                                    text = dialog.placeholder,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            if (dialog.suggestions.isEmpty()) {
                                TextField(
                                    state = textState,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                                )
                            } else {
                                AutoSuggestionBox(
                                    expanded = isSuggestionExpanded,
                                    onExpandedChange = { isSuggestionExpanded = it },
                                ) {
                                    TextField(
                                        state = textState,
                                        shape = AutoSuggestBoxDefaults.textFieldShape(isSuggestionExpanded),
                                        modifier = Modifier.fillMaxWidth().flyoutAnchor().heightIn(max = 480.dp),
                                        lineLimits = TextFieldLineLimits.SingleLine,
                                    )
                                    AutoSuggestBoxDefaults.suggestFlyout(
                                        expanded = isSuggestionExpanded && filteredSuggestions.isNotEmpty(),
                                        onDismissRequest = { isSuggestionExpanded = false },
                                        itemsContent = {
                                            items(filteredSuggestions) { item ->
                                                ListItem(
                                                    onClick = {
                                                        textState.edit {
                                                            replace(0, length, item)
                                                        }
                                                        isSuggestionExpanded = false
                                                    },
                                                    text = { Text(item, maxLines = 1) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        },
                                        modifier = Modifier.flyoutSize(matchAnchorWidth = true),
                                    )
                                }
                            }
                            if (dialog.hint.isNotBlank()) {
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    dialog.hint,
                                    style = FluentTheme.typography.caption,
                                    color = FluentTheme.colors.text.text.secondary,
                                )
                            }
                        }
                    },
                    primaryButtonText = stringResource(Res.string.ok),
                    closeButtonText = stringResource(Res.string.cancel),
                    onButtonClick = {
                        when (it) {
                            ContentDialogButton.Primary -> {
                                dialog.onConfirm(textState.text.toString())
                                state.aiConfigState.setTextEditDialog(null)
                            }

                            else -> {
                                state.aiConfigState.setTextEditDialog(null)
                            }
                        }
                    },
                )
            }
            if (state.aiConfigState.showExcludedLanguagesDialog) {
                ExcludedLanguagesDialog(
                    title = stringResource(Res.string.settings_translation_auto_excluded_languages),
                    selectedLanguages = state.aiConfigState.autoTranslateExcludedLanguages,
                    onDismiss = {
                        state.aiConfigState.setExcludedLanguagesDialog(false)
                    },
                    onConfirm = { selectedLanguages ->
                        state.aiConfigState.setAutoTranslateExcludedLanguages(selectedLanguages)
                        state.aiConfigState.setExcludedLanguagesDialog(false)
                    },
                )
            }

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
                val serverTitle = stringResource(Res.string.settings_ai_config_server)
                val serverHint = stringResource(Res.string.settings_ai_config_server_hint)
                val serverRequirementHint = stringResource(Res.string.settings_ai_config_server_url_requirement)
                val apiKeyTitle = stringResource(Res.string.settings_ai_config_api_key)
                val apiKeyHint = stringResource(Res.string.settings_ai_config_api_key_hint)
                val modelTitle = stringResource(Res.string.settings_ai_config_model)
                val modelPlaceholder = stringResource(Res.string.settings_ai_config_model_select)
                val tldrPromptTitle = stringResource(Res.string.settings_ai_config_tldr_prompt)
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_ai_config_type))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_ai_config_type_description))
                    },
                    trailing = {
                        DropDownButton(
                            onClick = {
                                state.aiConfigState.setShowTypeDropdown(!state.aiConfigState.showTypeDropdown)
                            },
                        ) {
                            Text(
                                stringResource(
                                    when (state.aiConfigState.aiType) {
                                        AiTypeOption.OnDevice -> Res.string.settings_ai_config_type_on_device
                                        AiTypeOption.OpenAI -> Res.string.settings_ai_config_type_openai
                                    },
                                ),
                            )
                        }
                        MenuFlyout(
                            visible = state.aiConfigState.showTypeDropdown,
                            onDismissRequest = { state.aiConfigState.setShowTypeDropdown(false) },
                            placement = FlyoutPlacement.BottomAlignedEnd,
                            modifier = Modifier.heightIn(max = 200.dp),
                        ) {
                            state.aiConfigState.supportedTypes.forEach { type ->
                                MenuFlyoutItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                when (type) {
                                                    AiTypeOption.OnDevice -> Res.string.settings_ai_config_type_on_device
                                                    AiTypeOption.OpenAI -> Res.string.settings_ai_config_type_openai
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        state.aiConfigState.selectType(type)
                                        state.aiConfigState.setShowTypeDropdown(false)
                                    },
                                )
                            }
                        }
                    },
                )
                ExpanderItemSeparator()
                AnimatedVisibility(state.aiConfigState.aiType == AiTypeOption.OpenAI) {
                    Column {
                        ExpanderItem(
                            heading = { Text(stringResource(Res.string.settings_ai_config_server)) },
                            caption = {
                                Text(
                                    state.aiConfigState.openAIServerUrl.ifBlank {
                                        stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                    },
                                )
                            },
                            trailing = {
                                Button(
                                    onClick = {
                                        state.aiConfigState.setTextEditDialog(
                                            TextEditDialogState(
                                                title = serverTitle,
                                                placeholder = serverHint,
                                                value = state.aiConfigState.openAIServerUrl,
                                                suggestions = state.aiConfigState.serverSuggestions,
                                                hint = serverRequirementHint,
                                                onConfirm = { newValue ->
                                                    state.aiConfigState.setOpenAIServerUrl(newValue)
                                                },
                                            ),
                                        )
                                    },
                                ) {
                                    Text(stringResource(Res.string.edit))
                                }
                            },
                        )
                        ExpanderItemSeparator()
                        ExpanderItem(
                            heading = { Text(stringResource(Res.string.settings_ai_config_api_key)) },
                            caption = {
                                Text(
                                    state.aiConfigState.openAIApiKey.ifBlank {
                                        stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                    },
                                )
                            },
                            trailing = {
                                Button(
                                    onClick = {
                                        state.aiConfigState.setTextEditDialog(
                                            TextEditDialogState(
                                                title = apiKeyTitle,
                                                placeholder = apiKeyHint,
                                                value = state.aiConfigState.openAIApiKey,
                                                onConfirm = { newValue ->
                                                    state.aiConfigState.setOpenAIApiKey(newValue)
                                                },
                                            ),
                                        )
                                    },
                                ) {
                                    Text(stringResource(Res.string.edit))
                                }
                            },
                        )
                        ExpanderItemSeparator()
                        val shouldShowManualModelInput =
                            when (val openAIModels = state.aiConfigState.openAIModels) {
                                is UiState.Error -> true
                                is UiState.Success -> openAIModels.data.isEmpty()
                                is UiState.Loading -> false
                            }
                        if (shouldShowManualModelInput) {
                            ExpanderItem(
                                heading = { Text(stringResource(Res.string.settings_ai_config_model_manual_input)) },
                                caption = {
                                    Text(
                                        state.aiConfigState.openAIModel.ifBlank {
                                            stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                        },
                                    )
                                },
                                trailing = {
                                    Button(
                                        onClick = {
                                            state.aiConfigState.setTextEditDialog(
                                                TextEditDialogState(
                                                    title = modelTitle,
                                                    placeholder = modelPlaceholder,
                                                    value = state.aiConfigState.openAIModel,
                                                    onConfirm = state.aiConfigState::setOpenAIModel,
                                                ),
                                            )
                                        },
                                    ) {
                                        Text(stringResource(Res.string.edit))
                                    }
                                },
                            )
                            ExpanderItemSeparator()
                        } else {
                            ExpanderItem(
                                heading = { Text(stringResource(Res.string.settings_ai_config_model)) },
                                caption = { Text(stringResource(Res.string.settings_ai_config_model_description)) },
                                trailing = {
                                    DropDownButton(
                                        onClick = {
                                            state.aiConfigState.setShowModelDropdown(!state.aiConfigState.showModelDropdown)
                                        },
                                    ) {
                                        Text(
                                            state.aiConfigState.openAIModel.ifBlank {
                                                stringResource(Res.string.settings_ai_config_model_select)
                                            },
                                        )
                                    }
                                    MenuFlyout(
                                        visible = state.aiConfigState.showModelDropdown,
                                        onDismissRequest = { state.aiConfigState.setShowModelDropdown(false) },
                                        placement = FlyoutPlacement.BottomAlignedEnd,
                                        modifier = Modifier.heightIn(max = 200.dp),
                                    ) {
                                        state.aiConfigState.openAIModels
                                            .onLoading {
                                                MenuFlyoutItem(
                                                    text = { Text(stringResource(Res.string.settings_ai_config_model_loading)) },
                                                    onClick = {},
                                                )
                                            }.onSuccess { models ->
                                                models.forEach { model ->
                                                    MenuFlyoutItem(
                                                        text = { Text(model) },
                                                        onClick = {
                                                            state.aiConfigState.setOpenAIModel(model)
                                                            state.aiConfigState.setShowModelDropdown(false)
                                                        },
                                                    )
                                                }
                                            }
                                    }
                                },
                            )
                            ExpanderItemSeparator()
                        }
                        ExpanderItem(
                            heading = { Text(stringResource(Res.string.settings_ai_config_reasoning_effort)) },
                            caption = {
                                Text(stringResource(Res.string.settings_ai_config_reasoning_effort_description))
                            },
                            trailing = {
                                DropDownButton(
                                    onClick = {
                                        state.aiConfigState.setShowReasoningEffortDropdown(
                                            !state.aiConfigState.showReasoningEffortDropdown,
                                        )
                                    },
                                ) {
                                    Text(openAIReasoningEffortLabel(state.aiConfigState.openAIReasoningEffort))
                                }
                                MenuFlyout(
                                    visible = state.aiConfigState.showReasoningEffortDropdown,
                                    onDismissRequest = { state.aiConfigState.setShowReasoningEffortDropdown(false) },
                                    placement = FlyoutPlacement.BottomAlignedEnd,
                                ) {
                                    state.aiConfigState.supportedOpenAIReasoningEfforts.forEach { effort ->
                                        MenuFlyoutItem(
                                            text = { Text(openAIReasoningEffortLabel(effort)) },
                                            onClick = {
                                                state.aiConfigState.setOpenAIReasoningEffort(effort)
                                                state.aiConfigState.setShowReasoningEffortDropdown(false)
                                            },
                                        )
                                    }
                                }
                            },
                        )
                        ExpanderItemSeparator()
                    }
                }
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_ai_config_enable_tldr))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_ai_config_tldr_description))
                    },
                    trailing = {
                        Switcher(
                            checked = state.aiConfigState.aiTldr,
                            {
                                state.aiConfigState.setAITldr(it)
                            },
                            textBefore = true,
                        )
                    },
                )
                AnimatedVisibility(state.aiConfigState.aiTldr) {
                    Column {
                        ExpanderItemSeparator()
                        ExpanderItem(
                            heading = { Text(stringResource(Res.string.settings_ai_config_tldr_prompt)) },
                            caption = {
                                Text(
                                    state.aiConfigState.tldrPrompt.ifBlank {
                                        stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                    },
                                )
                            },
                            trailing = {
                                Button(
                                    onClick = {
                                        state.aiConfigState.setTextEditDialog(
                                            TextEditDialogState(
                                                title = tldrPromptTitle,
                                                placeholder = "",
                                                value = state.aiConfigState.tldrPrompt,
                                                onConfirm = { newValue ->
                                                    state.aiConfigState.setTldrPrompt(newValue)
                                                },
                                            ),
                                        )
                                    },
                                ) {
                                    Text(stringResource(Res.string.edit))
                                }
                            },
                        )
                    }
                }
            }
            Expander(
                state.aiConfigState.translationExpanded,
                onExpandedChanged = state.aiConfigState::setTranslationExpanded,
                heading = {
                    Text(stringResource(Res.string.settings_translation_title))
                },
                caption = {
                    Text(stringResource(Res.string.settings_translation_description))
                },
                icon = null,
            ) {
                val translatePromptTitle = stringResource(Res.string.settings_ai_config_translate_prompt)
                val deepLApiKeyTitle = stringResource(Res.string.settings_ai_config_translate_provider_deepl_api_key)
                val googleCloudApiKeyTitle = stringResource(Res.string.settings_ai_config_translate_provider_google_cloud_api_key)
                val libreTranslateBaseUrlTitle = stringResource(Res.string.settings_ai_config_translate_provider_libretranslate_base_url)
                val libreTranslateApiKeyTitle = stringResource(Res.string.settings_ai_config_translate_provider_libretranslate_api_key)
                val excludedLanguagesTitle = stringResource(Res.string.settings_translation_auto_excluded_languages)
                val hasProviderSettings = state.aiConfigState.translateProvider != TranslateProviderOption.GoogleWeb
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_ai_config_translate_provider))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_ai_config_translate_provider_description))
                    },
                    trailing = {
                        DropDownButton(
                            onClick = {
                                state.aiConfigState.setShowProviderDropdown(!state.aiConfigState.showProviderDropdown)
                            },
                        ) {
                            Text(
                                translateProviderOptionLabel(state.aiConfigState.translateProvider),
                            )
                        }
                        MenuFlyout(
                            visible = state.aiConfigState.showProviderDropdown,
                            onDismissRequest = { state.aiConfigState.setShowProviderDropdown(false) },
                            placement = FlyoutPlacement.BottomAlignedEnd,
                            modifier = Modifier.heightIn(max = 200.dp),
                        ) {
                            state.aiConfigState.supportedTranslateProviders.forEach { provider ->
                                MenuFlyoutItem(
                                    text = {
                                        Text(translateProviderOptionLabel(provider))
                                    },
                                    onClick = {
                                        state.aiConfigState.selectTranslateProvider(provider)
                                        state.aiConfigState.setShowProviderDropdown(false)
                                    },
                                )
                            }
                        }
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_ai_config_enable_pre_translation))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_ai_config_pre_translation_description))
                    },
                    trailing = {
                        Switcher(
                            checked = state.aiConfigState.preTranslate,
                            {
                                state.aiConfigState.setPreTranslate(it)
                            },
                            textBefore = true,
                        )
                    },
                )
                AnimatedVisibility(state.aiConfigState.preTranslate) {
                    Column {
                        ExpanderItemSeparator()
                        ExpanderItem(
                            heading = {
                                Text(stringResource(Res.string.settings_translation_auto_excluded_languages))
                            },
                            caption = {
                                Text(
                                    excludedLanguageSummary(
                                        selectedLanguages = state.aiConfigState.autoTranslateExcludedLanguages,
                                        emptyPlaceholder = stringResource(Res.string.settings_ai_config_value_empty_placeholder),
                                    ),
                                )
                            },
                            trailing = {
                                Button(
                                    onClick = {
                                        state.aiConfigState.setExcludedLanguagesDialog(true)
                                    },
                                ) {
                                    Text(stringResource(Res.string.edit))
                                }
                            },
                        )
                    }
                }
                AnimatedVisibility(hasProviderSettings) {
                    Column {
                        ExpanderItemSeparator()
                        when (state.aiConfigState.translateProvider) {
                            TranslateProviderOption.AI -> {
                                ExpanderItem(
                                    heading = { Text(stringResource(Res.string.settings_ai_config_translate_prompt)) },
                                    caption = {
                                        Text(
                                            state.aiConfigState.translatePrompt.ifBlank {
                                                stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                            },
                                        )
                                    },
                                    trailing = {
                                        Button(
                                            onClick = {
                                                state.aiConfigState.setTextEditDialog(
                                                    TextEditDialogState(
                                                        title = translatePromptTitle,
                                                        placeholder = "",
                                                        value = state.aiConfigState.translatePrompt,
                                                        onConfirm = { newValue ->
                                                            state.aiConfigState.setTranslatePrompt(newValue)
                                                        },
                                                    ),
                                                )
                                            },
                                        ) {
                                            Text(stringResource(Res.string.edit))
                                        }
                                    },
                                )
                                ExpanderItemSeparator()
                                ExpanderItem(
                                    heading = { Text(stringResource(Res.string.settings_translation_ai_test_title)) },
                                    caption = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(stringResource(Res.string.settings_translation_ai_test_description))
                                            Text(stringResource(Res.string.settings_translation_ai_test_original))
                                            RichText(state.aiConfigState.aiTranslationTestState.sampleText)
                                            Button(
                                                onClick = {
                                                    state.aiConfigState.aiTranslationTestState.runTest()
                                                },
                                            ) {
                                                Text(stringResource(Res.string.settings_translation_ai_test_action))
                                            }
                                            if (state.aiConfigState.aiTranslationTestState.isLoading) {
                                                Text(stringResource(Res.string.settings_ai_config_model_loading))
                                            }
                                            state.aiConfigState.aiTranslationTestState.errorMessage?.let { message ->
                                                Text(
                                                    message,
                                                    color = FluentTheme.colors.system.critical,
                                                )
                                            }
                                            state.aiConfigState.aiTranslationTestState.translatedText?.let { translated ->
                                                Text(stringResource(Res.string.settings_translation_ai_test_result))
                                                RichText(translated)
                                            }
                                        }
                                    },
                                )
                            }

                            TranslateProviderOption.DeepL -> {
                                ExpanderItem(
                                    heading = { Text(deepLApiKeyTitle) },
                                    caption = {
                                        Text(
                                            state.aiConfigState.deepLApiKey.ifBlank {
                                                stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                            },
                                        )
                                    },
                                    trailing = {
                                        Button(
                                            onClick = {
                                                state.aiConfigState.setTextEditDialog(
                                                    TextEditDialogState(
                                                        title = deepLApiKeyTitle,
                                                        placeholder = "",
                                                        value = state.aiConfigState.deepLApiKey,
                                                        onConfirm = { newValue ->
                                                            state.aiConfigState.setDeepLApiKey(newValue)
                                                        },
                                                    ),
                                                )
                                            },
                                        ) {
                                            Text(stringResource(Res.string.edit))
                                        }
                                    },
                                )
                                ExpanderItemSeparator()
                                ExpanderItem(
                                    heading = { Text(stringResource(Res.string.settings_ai_config_translate_provider_deepl_use_pro)) },
                                    caption = {
                                        Text(stringResource(Res.string.settings_ai_config_translate_provider_deepl_use_pro_description))
                                    },
                                    trailing = {
                                        Switcher(
                                            checked = state.aiConfigState.deepLUsePro,
                                            {
                                                state.aiConfigState.setDeepLUsePro(it)
                                            },
                                            textBefore = true,
                                        )
                                    },
                                )
                            }

                            TranslateProviderOption.GoogleCloud -> {
                                ExpanderItem(
                                    heading = { Text(googleCloudApiKeyTitle) },
                                    caption = {
                                        Text(
                                            state.aiConfigState.googleCloudApiKey.ifBlank {
                                                stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                            },
                                        )
                                    },
                                    trailing = {
                                        Button(
                                            onClick = {
                                                state.aiConfigState.setTextEditDialog(
                                                    TextEditDialogState(
                                                        title = googleCloudApiKeyTitle,
                                                        placeholder = "",
                                                        value = state.aiConfigState.googleCloudApiKey,
                                                        onConfirm = { newValue ->
                                                            state.aiConfigState.setGoogleCloudApiKey(newValue)
                                                        },
                                                    ),
                                                )
                                            },
                                        ) {
                                            Text(stringResource(Res.string.edit))
                                        }
                                    },
                                )
                            }

                            TranslateProviderOption.LibreTranslate -> {
                                ExpanderItem(
                                    heading = { Text(libreTranslateBaseUrlTitle) },
                                    caption = {
                                        Text(
                                            state.aiConfigState.libreTranslateBaseUrl.ifBlank {
                                                stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                            },
                                        )
                                    },
                                    trailing = {
                                        Button(
                                            onClick = {
                                                state.aiConfigState.setTextEditDialog(
                                                    TextEditDialogState(
                                                        title = libreTranslateBaseUrlTitle,
                                                        placeholder = "https://libretranslate.example.com",
                                                        value = state.aiConfigState.libreTranslateBaseUrl,
                                                        onConfirm = { newValue ->
                                                            state.aiConfigState.setLibreTranslateBaseUrl(newValue)
                                                        },
                                                    ),
                                                )
                                            },
                                        ) {
                                            Text(stringResource(Res.string.edit))
                                        }
                                    },
                                )
                                ExpanderItemSeparator()
                                ExpanderItem(
                                    heading = { Text(libreTranslateApiKeyTitle) },
                                    caption = {
                                        Text(
                                            state.aiConfigState.libreTranslateApiKey.ifBlank {
                                                stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                            },
                                        )
                                    },
                                    trailing = {
                                        Button(
                                            onClick = {
                                                state.aiConfigState.setTextEditDialog(
                                                    TextEditDialogState(
                                                        title = libreTranslateApiKeyTitle,
                                                        placeholder = "",
                                                        value = state.aiConfigState.libreTranslateApiKey,
                                                        onConfirm = { newValue ->
                                                            state.aiConfigState.setLibreTranslateApiKey(newValue)
                                                        },
                                                    ),
                                                )
                                            },
                                        ) {
                                            Text(stringResource(Res.string.edit))
                                        }
                                    },
                                )
                            }

                            TranslateProviderOption.GoogleWeb -> {
                                Unit
                            }
                        }
                    }
                }
            }

            Header(stringResource(Res.string.settings_about_title))
            Expander(
                icon = null,
                heading = {
                    Text(stringResource(Res.string.app_name))
                },
                caption = {
                    Text("${BuildConfig.versionName} (${BuildConfig.versionCode})")
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
                        Text(text = stringResource(resource = Res.string.settings_about_discord))
                    },
                    caption = {
                        Text(text = stringResource(resource = Res.string.settings_about_discord_description))
                    },
                    trailing = {
                        HyperlinkButton(
                            "https://discord.gg/De9NhXBryT",
                        ) {
                            Text(
                                text = "https://discord.gg/De9NhXBryT",
                                maxLines = 1,
                            )
                        }
                    },
                    icon = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Brands.Discord,
                            contentDescription = stringResource(resource = Res.string.settings_about_discord),
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
    val presenter = remember { StoragePresenter() }
    val state = presenter.invoke()

    val notification = koinInject<ComposeInAppNotification>()
    val exportPresenter = remember { ExportDataPresenter() }
    val exportState = exportPresenter.body()
    val scope = rememberCoroutineScope()
    var isClearingImageCache by remember { mutableStateOf(false) }
    var isClearingDatabaseCache by remember { mutableStateOf(false) }

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
        val isClearingImageCache = isClearingImageCache
        val isClearingDatabaseCache = isClearingDatabaseCache
        val isClearingStorage = isClearingImageCache || isClearingDatabaseCache

        fun clearImageCache() {
            if (isClearingStorage) {
                return
            }
            scope.launch {
                isClearingImageCache = true
                try {
                    withContext(Dispatchers.IO) {
                        SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache?.clear()
                    }
                    refreshKey++
                } finally {
                    isClearingImageCache = false
                }
            }
        }

        fun clearCacheDatabase() {
            if (isClearingStorage) {
                return
            }
            scope.launch {
                isClearingDatabaseCache = true
                try {
                    presenter.clearCacheSuspend()
                    refreshKey++
                } finally {
                    isClearingDatabaseCache = false
                }
            }
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
private fun accountsPresenter() =
    run {
        var expanded by remember { mutableStateOf(false) }
        val state =
            remember {
                AccountManagementPresenter()
            }.invoke()
        val activeAccountState = remember { ActiveAccountPresenter() }.invoke()

        object : AccountManagementPresenter.State by state, UserState by activeAccountState {
            val expanded = expanded

            fun setExpanded(value: Boolean) {
                expanded = value
            }

            fun moveItem(
                fromIndex: Int,
                toIndex: Int,
            ) {
                state.accounts.onSuccess { accounts ->
                    accounts
                        .map { it.account.accountKey }
                        .toMutableList()
                        .apply {
                            add(toIndex, removeAt(fromIndex))
                        }.let {
                            state.setOrder(it)
                            state.updateOrder(it)
                        }
                }
            }

            fun deleteItem(accountKey: MicroBlogKey) {
                state.logout(accountKey)
            }
        }
    }

@Composable
private fun aiConfigPresenter() =
    run {
        var expanded by remember { mutableStateOf(false) }
        var translationExpanded by remember { mutableStateOf(false) }
        val state = remember { AiConfigPresenter() }.invoke()
        val aiTranslationTestState = remember { AiTranslationTestPresenter() }.invoke()
        var showTypeDropdown by remember { mutableStateOf(false) }
        var showModelDropdown by remember { mutableStateOf(false) }
        var showReasoningEffortDropdown by remember { mutableStateOf(false) }
        var showProviderDropdown by remember { mutableStateOf(false) }
        var showExcludedLanguagesDialog by remember { mutableStateOf(false) }
        var textEditDialog by remember { mutableStateOf<TextEditDialogState?>(null) }
        object : AiConfigPresenter.State by state {
            val aiTranslationTestState = aiTranslationTestState
            val expanded = expanded
            val translationExpanded = translationExpanded
            val showTypeDropdown = showTypeDropdown
            val showModelDropdown = showModelDropdown
            val showReasoningEffortDropdown = showReasoningEffortDropdown
            val showProviderDropdown = showProviderDropdown
            val showExcludedLanguagesDialog = showExcludedLanguagesDialog
            val textEditDialog = textEditDialog

            fun setExpanded(value: Boolean) {
                expanded = value
            }

            fun setTranslationExpanded(value: Boolean) {
                translationExpanded = value
            }

            fun setShowTypeDropdown(value: Boolean) {
                showTypeDropdown = value
            }

            fun setShowModelDropdown(value: Boolean) {
                showModelDropdown = value
            }

            fun setShowReasoningEffortDropdown(value: Boolean) {
                showReasoningEffortDropdown = value
            }

            fun setShowProviderDropdown(value: Boolean) {
                showProviderDropdown = value
            }

            fun setExcludedLanguagesDialog(value: Boolean) {
                showExcludedLanguagesDialog = value
            }

            fun setTextEditDialog(value: TextEditDialogState?) {
                textEditDialog = value
            }
        }
    }

@Composable
private fun translateProviderOptionLabel(provider: TranslateProviderOption): String =
    when (provider) {
        TranslateProviderOption.AI -> stringResource(Res.string.settings_ai_config_translate_provider_ai)
        TranslateProviderOption.GoogleWeb -> stringResource(Res.string.settings_ai_config_translate_provider_google_web)
        TranslateProviderOption.DeepL -> stringResource(Res.string.settings_ai_config_translate_provider_deepl)
        TranslateProviderOption.GoogleCloud -> stringResource(Res.string.settings_ai_config_translate_provider_google_cloud)
        TranslateProviderOption.LibreTranslate -> stringResource(Res.string.settings_ai_config_translate_provider_libretranslate)
    }

@Composable
private fun openAIReasoningEffortLabel(option: AiReasoningEffortOption): String =
    when (option) {
        AiReasoningEffortOption.Default -> stringResource(Res.string.settings_ai_config_reasoning_effort_default)
        AiReasoningEffortOption.Low -> stringResource(Res.string.settings_ai_config_reasoning_effort_low)
        AiReasoningEffortOption.Medium -> stringResource(Res.string.settings_ai_config_reasoning_effort_medium)
        AiReasoningEffortOption.High -> stringResource(Res.string.settings_ai_config_reasoning_effort_high)
    }

private data class TextEditDialogState(
    val title: String,
    val placeholder: String,
    val value: String,
    val suggestions: ImmutableList<String> = persistentListOf(),
    val hint: String = "",
    val onConfirm: (String) -> Unit,
)

private data class LanguageOption(
    val tag: String,
    val label: String,
)

private fun translationLanguageOptions(selectedLanguages: ImmutableList<String>): ImmutableList<LanguageOption> {
    val displayLocale = Locale.getDefault()
    val baseOptions =
        Locale
            .getISOLanguages()
            .map { code ->
                @Suppress("DEPRECATION")
                LanguageOption(tag = code, label = Locale(code).getDisplayLanguage(displayLocale))
            }.filter { it.label.isNotBlank() }
    val specialOptions =
        listOf(
            LanguageOption("zh-CN", Locale.forLanguageTag("zh-CN").getDisplayName(displayLocale)),
            LanguageOption("zh-TW", Locale.forLanguageTag("zh-TW").getDisplayName(displayLocale)),
        )
    val knownTags = (specialOptions + baseOptions).map(LanguageOption::tag).toSet()
    val customOptions = selectedLanguages.filterNot(knownTags::contains).map { LanguageOption(tag = it, label = it) }
    return (specialOptions + baseOptions + customOptions)
        .distinctBy(LanguageOption::tag)
        .sortedBy(LanguageOption::label)
        .toImmutableList()
}

private fun excludedLanguageSummary(
    selectedLanguages: ImmutableList<String>,
    emptyPlaceholder: String,
): String {
    if (selectedLanguages.isEmpty()) {
        return emptyPlaceholder
    }
    val labels = translationLanguageOptions(selectedLanguages).associate { it.tag to it.label }
    return selectedLanguages.joinToString { tag -> labels[tag] ?: tag }
}

@Composable
private fun ExcludedLanguagesDialog(
    title: String,
    selectedLanguages: ImmutableList<String>,
    onDismiss: () -> Unit,
    onConfirm: (ImmutableList<String>) -> Unit,
) {
    val options = remember(selectedLanguages) { translationLanguageOptions(selectedLanguages) }
    var selectedTags by remember(selectedLanguages) { mutableStateOf(selectedLanguages.toSet()) }
    ContentDialog(
        title = title,
        visible = true,
        content = {
            val scrollState = rememberLazyListState()
            FlareScrollBar(state = scrollState) {
                LazyColumn(
                    state = scrollState,
                    modifier =
                        Modifier
                            .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                ) {
                    items(options, key = { it.tag }) { option ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTags =
                                            if (option.tag in selectedTags) {
                                                selectedTags - option.tag
                                            } else {
                                                selectedTags + option.tag
                                            }
                                    }.padding(vertical = 6.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(option.label)
                                if (option.label != option.tag) {
                                    Text(
                                        option.tag,
                                        color = FluentTheme.colors.text.text.secondary,
                                        style = FluentTheme.typography.caption,
                                    )
                                }
                            }
                            CheckBox(
                                checked = option.tag in selectedTags,
                                onCheckStateChange = { checked ->
                                    selectedTags =
                                        if (checked) {
                                            selectedTags + option.tag
                                        } else {
                                            selectedTags - option.tag
                                        }
                                },
                            )
                        }
                    }
                }
            }
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    onConfirm(options.map(LanguageOption::tag).filter { tag -> tag in selectedTags }.toImmutableList())
                }

                else -> {
                    onDismiss()
                }
            }
        },
    )
}
