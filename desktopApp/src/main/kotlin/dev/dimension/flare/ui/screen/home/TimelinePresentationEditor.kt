package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelinePostContent
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.edit_tab_enabled
import dev.dimension.flare.edit_tab_with_avatar
import dev.dimension.flare.ok
import dev.dimension.flare.settings_appearance_absolute_timestamp
import dev.dimension.flare.settings_appearance_absolute_timestamp_description
import dev.dimension.flare.settings_appearance_avatar_shape
import dev.dimension.flare.settings_appearance_avatar_shape_description
import dev.dimension.flare.settings_appearance_avatar_shape_round
import dev.dimension.flare.settings_appearance_avatar_shape_square
import dev.dimension.flare.settings_appearance_compat_link_previews
import dev.dimension.flare.settings_appearance_compat_link_previews_description
import dev.dimension.flare.settings_appearance_display_group_subtitle
import dev.dimension.flare.settings_appearance_display_group_title
import dev.dimension.flare.settings_appearance_expand_content_warning
import dev.dimension.flare.settings_appearance_expand_content_warning_description
import dev.dimension.flare.settings_appearance_expand_media
import dev.dimension.flare.settings_appearance_expand_media_description
import dev.dimension.flare.settings_appearance_full_width_post
import dev.dimension.flare.settings_appearance_full_width_post_description
import dev.dimension.flare.settings_appearance_layout_group_subtitle
import dev.dimension.flare.settings_appearance_layout_group_title
import dev.dimension.flare.settings_appearance_limit_media_grid_to_nine
import dev.dimension.flare.settings_appearance_limit_media_grid_to_nine_description
import dev.dimension.flare.settings_appearance_media_group_subtitle
import dev.dimension.flare.settings_appearance_media_group_title
import dev.dimension.flare.settings_appearance_post_action_style
import dev.dimension.flare.settings_appearance_post_action_style_description
import dev.dimension.flare.settings_appearance_post_action_style_hidden
import dev.dimension.flare.settings_appearance_post_action_style_left_aligned
import dev.dimension.flare.settings_appearance_post_action_style_right_aligned
import dev.dimension.flare.settings_appearance_post_action_style_stretch
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
import dev.dimension.flare.settings_appearance_theme_group_subtitle
import dev.dimension.flare.settings_appearance_theme_group_title
import dev.dimension.flare.settings_appearance_timeline_display_mode
import dev.dimension.flare.settings_appearance_timeline_display_mode_card
import dev.dimension.flare.settings_appearance_timeline_display_mode_description
import dev.dimension.flare.settings_appearance_timeline_display_mode_gallery
import dev.dimension.flare.settings_appearance_timeline_display_mode_plain
import dev.dimension.flare.settings_appearance_video_autoplay
import dev.dimension.flare.settings_appearance_video_autoplay_always
import dev.dimension.flare.settings_appearance_video_autoplay_description
import dev.dimension.flare.settings_appearance_video_autoplay_never
import dev.dimension.flare.settings_appearance_video_autoplay_wifi
import dev.dimension.flare.tab_settings_filter_content_group
import dev.dimension.flare.tab_settings_filter_desc
import dev.dimension.flare.tab_settings_filter_image
import dev.dimension.flare.tab_settings_filter_kind_group
import dev.dimension.flare.tab_settings_filter_quote
import dev.dimension.flare.tab_settings_filter_reply
import dev.dimension.flare.tab_settings_filter_repost
import dev.dimension.flare.tab_settings_filter_text_only
import dev.dimension.flare.tab_settings_filter_title
import dev.dimension.flare.tab_settings_filter_video
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.UiText
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.Flyout
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TimelinePresentationEditor(
    text: TextFieldState,
    icon: IconType,
    availableIcons: List<IconType>,
    showIconPicker: Boolean,
    onShowIconPickerChange: (Boolean) -> Unit,
    withAvatar: Boolean,
    canUseAvatar: Boolean,
    onWithAvatarChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    filterConfig: TimelineFilterConfig,
    onFilterConfigChange: (TimelineFilterConfig) -> Unit,
    timelineAppearance: TimelineAppearance,
    appearancePatch: AppearancePatch,
    onAppearancePatchChange: (AppearancePatch) -> Unit,
    onIconChange: (IconType) -> Unit,
    modifier: Modifier = Modifier,
    showEnabled: Boolean = true,
    showAppearanceOverrides: Boolean = true,
    behaviorContent: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val layoutOverridesEnabled =
        appearancePatch.contains(AppearanceKeys.TimelineDisplayMode) ||
            appearancePatch.contains(AppearanceKeys.FullWidthPost) ||
            appearancePatch.contains(AppearanceKeys.PostActionStyle) ||
            appearancePatch.contains(AppearanceKeys.ShowNumbers)
    val displayOverridesEnabled =
        appearancePatch.contains(AppearanceKeys.AbsoluteTimestamp) ||
            appearancePatch.contains(AppearanceKeys.ShowPlatformLogo) ||
            appearancePatch.contains(AppearanceKeys.ShowLinkPreview) ||
            appearancePatch.contains(AppearanceKeys.CompatLinkPreview)
    val mediaOverridesEnabled =
        appearancePatch.contains(AppearanceKeys.ShowMedia) ||
            appearancePatch.contains(AppearanceKeys.ShowSensitiveContent) ||
            appearancePatch.contains(AppearanceKeys.ExpandContentWarning) ||
            appearancePatch.contains(AppearanceKeys.ExpandMediaSize) ||
            appearancePatch.contains(AppearanceKeys.LimitMediaGridToNine) ||
            appearancePatch.contains(AppearanceKeys.VideoAutoplay)
    val themeOverridesEnabled = appearancePatch.contains(AppearanceKeys.AvatarShape)
    var showFilterDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimelinePresentationHeaderEditor(
            text = text,
            icon = icon,
            availableIcons = availableIcons,
            showIconPicker = showIconPicker,
            onShowIconPickerChange = onShowIconPickerChange,
            onIconChange = onIconChange,
            header = header,
            placeholder = placeholder,
        )

        if (canUseAvatar) {
            Row(
                modifier = Modifier.clickable { onWithAvatarChange(!withAvatar) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CheckBox(
                    checked = withAvatar,
                    onCheckStateChange = onWithAvatarChange,
                )
                Text(text = stringResource(Res.string.edit_tab_with_avatar))
            }
        }
        if (showEnabled) {
            Switcher(
                checked = enabled,
                onCheckStateChange = onEnabledChange,
                textBefore = true,
                text = stringResource(Res.string.edit_tab_enabled),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showAppearanceOverrides) {
                behaviorContent?.invoke()
                TimelineFilterSettingsItem(
                    onClick = { showFilterDialog = true },
                )
                AppearanceOverrideExpander(
                    title = stringResource(Res.string.settings_appearance_layout_group_title),
                    subtitle = stringResource(Res.string.settings_appearance_layout_group_subtitle),
                    overridesEnabled = layoutOverridesEnabled,
                    onOverridesEnabledChange = {
                        onAppearancePatchChange(
                            if (it) {
                                appearancePatch
                                    .set(
                                        AppearanceKeys.TimelineDisplayMode,
                                        timelineAppearance.timelineDisplayMode,
                                    ).set(
                                        AppearanceKeys.FullWidthPost,
                                        timelineAppearance.fullWidthPost,
                                    ).set(
                                        AppearanceKeys.PostActionStyle,
                                        timelineAppearance.postActionStyle,
                                    ).set(AppearanceKeys.ShowNumbers, timelineAppearance.showNumbers)
                            } else {
                                appearancePatch.clearAll(
                                    AppearanceKeys.TimelineDisplayMode,
                                    AppearanceKeys.FullWidthPost,
                                    AppearanceKeys.PostActionStyle,
                                    AppearanceKeys.ShowNumbers,
                                )
                            },
                        )
                    },
                ) {
                    ChoiceItem(
                        title = stringResource(Res.string.settings_appearance_timeline_display_mode),
                        caption = stringResource(Res.string.settings_appearance_timeline_display_mode_description),
                        items =
                            persistentMapOf(
                                TimelineDisplayMode.Card to Res.string.settings_appearance_timeline_display_mode_card,
                                TimelineDisplayMode.Plain to Res.string.settings_appearance_timeline_display_mode_plain,
                                TimelineDisplayMode.Gallery to Res.string.settings_appearance_timeline_display_mode_gallery,
                            ),
                        selected = timelineAppearance.timelineDisplayMode,
                        onSelected = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.TimelineDisplayMode,
                                    it,
                                ),
                            )
                        },
                    )
                    ExpanderItemSeparator()
                    SwitchItem(
                        title = stringResource(Res.string.settings_appearance_full_width_post),
                        caption = stringResource(Res.string.settings_appearance_full_width_post_description),
                        checked = timelineAppearance.fullWidthPost,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.FullWidthPost,
                                    it,
                                ),
                            )
                        },
                    )
                    ExpanderItemSeparator()
                    ChoiceItem(
                        title = stringResource(Res.string.settings_appearance_post_action_style),
                        caption = stringResource(Res.string.settings_appearance_post_action_style_description),
                        items =
                            persistentMapOf(
                                PostActionStyle.Hidden to Res.string.settings_appearance_post_action_style_hidden,
                                PostActionStyle.LeftAligned to Res.string.settings_appearance_post_action_style_left_aligned,
                                PostActionStyle.RightAligned to Res.string.settings_appearance_post_action_style_right_aligned,
                                PostActionStyle.Stretch to Res.string.settings_appearance_post_action_style_stretch,
                            ),
                        selected = timelineAppearance.postActionStyle,
                        onSelected = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.PostActionStyle,
                                    it,
                                ),
                            )
                        },
                    )
                    AnimatedVisibility(timelineAppearance.postActionStyle != PostActionStyle.Hidden) {
                        Column {
                            ExpanderItemSeparator()
                            SwitchItem(
                                title = stringResource(Res.string.settings_appearance_show_numbers),
                                caption = stringResource(Res.string.settings_appearance_show_numbers_description),
                                checked = timelineAppearance.showNumbers,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.ShowNumbers,
                                            it,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                AppearanceOverrideExpander(
                    title = stringResource(Res.string.settings_appearance_display_group_title),
                    subtitle = stringResource(Res.string.settings_appearance_display_group_subtitle),
                    overridesEnabled = displayOverridesEnabled,
                    onOverridesEnabledChange = {
                        onAppearancePatchChange(
                            if (it) {
                                appearancePatch
                                    .set(
                                        AppearanceKeys.AbsoluteTimestamp,
                                        timelineAppearance.absoluteTimestamp,
                                    ).set(
                                        AppearanceKeys.ShowPlatformLogo,
                                        timelineAppearance.showPlatformLogo,
                                    ).set(
                                        AppearanceKeys.ShowLinkPreview,
                                        timelineAppearance.showLinkPreview,
                                    ).set(
                                        AppearanceKeys.CompatLinkPreview,
                                        timelineAppearance.compatLinkPreview,
                                    )
                            } else {
                                appearancePatch.clearAll(
                                    AppearanceKeys.AbsoluteTimestamp,
                                    AppearanceKeys.ShowPlatformLogo,
                                    AppearanceKeys.ShowLinkPreview,
                                    AppearanceKeys.CompatLinkPreview,
                                )
                            },
                        )
                    },
                ) {
                    SwitchItem(
                        title = stringResource(Res.string.settings_appearance_absolute_timestamp),
                        caption = stringResource(Res.string.settings_appearance_absolute_timestamp_description),
                        checked = timelineAppearance.absoluteTimestamp,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.AbsoluteTimestamp,
                                    it,
                                ),
                            )
                        },
                    )
                    ExpanderItemSeparator()
                    SwitchItem(
                        title = stringResource(Res.string.settings_appearance_show_platform_logo),
                        caption = stringResource(Res.string.settings_appearance_show_platform_logo_description),
                        checked = timelineAppearance.showPlatformLogo,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.ShowPlatformLogo,
                                    it,
                                ),
                            )
                        },
                    )
                    ExpanderItemSeparator()
                    SwitchItem(
                        title = stringResource(Res.string.settings_appearance_show_link_previews),
                        caption = stringResource(Res.string.settings_appearance_show_link_previews_description),
                        checked = timelineAppearance.showLinkPreview,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.ShowLinkPreview,
                                    it,
                                ),
                            )
                        },
                    )
                    AnimatedVisibility(timelineAppearance.showLinkPreview) {
                        Column {
                            ExpanderItemSeparator()
                            SwitchItem(
                                title = stringResource(Res.string.settings_appearance_compat_link_previews),
                                caption = stringResource(Res.string.settings_appearance_compat_link_previews_description),
                                checked = timelineAppearance.compatLinkPreview,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.CompatLinkPreview,
                                            it,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                AppearanceOverrideExpander(
                    title = stringResource(Res.string.settings_appearance_media_group_title),
                    subtitle = stringResource(Res.string.settings_appearance_media_group_subtitle),
                    overridesEnabled = mediaOverridesEnabled,
                    onOverridesEnabledChange = {
                        onAppearancePatchChange(
                            if (it) {
                                appearancePatch
                                    .set(AppearanceKeys.ShowMedia, timelineAppearance.showMedia)
                                    .set(
                                        AppearanceKeys.ShowSensitiveContent,
                                        timelineAppearance.showSensitiveContent,
                                    ).set(
                                        AppearanceKeys.ExpandContentWarning,
                                        timelineAppearance.expandContentWarning,
                                    ).set(
                                        AppearanceKeys.ExpandMediaSize,
                                        timelineAppearance.expandMediaSize,
                                    ).set(
                                        AppearanceKeys.LimitMediaGridToNine,
                                        timelineAppearance.limitMediaGridToNine,
                                    ).set(
                                        AppearanceKeys.VideoAutoplay,
                                        timelineAppearance.videoAutoplay,
                                    )
                            } else {
                                appearancePatch.clearAll(
                                    AppearanceKeys.ShowMedia,
                                    AppearanceKeys.ShowSensitiveContent,
                                    AppearanceKeys.ExpandContentWarning,
                                    AppearanceKeys.ExpandMediaSize,
                                    AppearanceKeys.LimitMediaGridToNine,
                                    AppearanceKeys.VideoAutoplay,
                                )
                            },
                        )
                    },
                ) {
                    SwitchItem(
                        title = stringResource(Res.string.settings_appearance_show_media),
                        caption = stringResource(Res.string.settings_appearance_show_media_description),
                        checked = timelineAppearance.showMedia,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.ShowMedia,
                                    it,
                                ),
                            )
                        },
                    )
                    AnimatedVisibility(timelineAppearance.showMedia) {
                        Column {
                            ExpanderItemSeparator()
                            SwitchItem(
                                title = stringResource(Res.string.settings_appearance_show_cw_img),
                                caption = stringResource(Res.string.settings_appearance_show_cw_img_description),
                                checked = timelineAppearance.showSensitiveContent,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.ShowSensitiveContent,
                                            it,
                                        ),
                                    )
                                },
                            )
                            ExpanderItemSeparator()
                            SwitchItem(
                                title = stringResource(Res.string.settings_appearance_expand_content_warning),
                                caption = stringResource(Res.string.settings_appearance_expand_content_warning_description),
                                checked = timelineAppearance.expandContentWarning,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.ExpandContentWarning,
                                            it,
                                        ),
                                    )
                                },
                            )
                            ExpanderItemSeparator()
                            SwitchItem(
                                title = stringResource(Res.string.settings_appearance_limit_media_grid_to_nine),
                                caption = stringResource(Res.string.settings_appearance_limit_media_grid_to_nine_description),
                                checked = timelineAppearance.limitMediaGridToNine,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.LimitMediaGridToNine,
                                            it,
                                        ),
                                    )
                                },
                            )
                            ExpanderItemSeparator()
                            SwitchItem(
                                title = stringResource(Res.string.settings_appearance_expand_media),
                                caption = stringResource(Res.string.settings_appearance_expand_media_description),
                                checked = timelineAppearance.expandMediaSize,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.ExpandMediaSize,
                                            it,
                                        ),
                                    )
                                },
                            )
                            ExpanderItemSeparator()
                            ChoiceItem(
                                title = stringResource(Res.string.settings_appearance_video_autoplay),
                                caption = stringResource(Res.string.settings_appearance_video_autoplay_description),
                                items =
                                    persistentMapOf(
                                        VideoAutoplay.WIFI to Res.string.settings_appearance_video_autoplay_wifi,
                                        VideoAutoplay.ALWAYS to Res.string.settings_appearance_video_autoplay_always,
                                        VideoAutoplay.NEVER to Res.string.settings_appearance_video_autoplay_never,
                                    ),
                                selected = timelineAppearance.videoAutoplay,
                                onSelected = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.VideoAutoplay,
                                            it,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                AppearanceOverrideExpander(
                    title = stringResource(Res.string.settings_appearance_theme_group_title),
                    subtitle = stringResource(Res.string.settings_appearance_theme_group_subtitle),
                    overridesEnabled = appearancePatch.contains(AppearanceKeys.AvatarShape),
                    onOverridesEnabledChange = {
                        onAppearancePatchChange(
                            if (it) {
                                appearancePatch.set(
                                    AppearanceKeys.AvatarShape,
                                    timelineAppearance.avatarShape,
                                )
                            } else {
                                appearancePatch.clear(AppearanceKeys.AvatarShape)
                            },
                        )
                    },
                ) {
                    ChoiceItem(
                        title = stringResource(Res.string.settings_appearance_avatar_shape),
                        caption = stringResource(Res.string.settings_appearance_avatar_shape_description),
                        items =
                            persistentMapOf(
                                AvatarShape.CIRCLE to Res.string.settings_appearance_avatar_shape_round,
                                AvatarShape.SQUARE to Res.string.settings_appearance_avatar_shape_square,
                            ),
                        selected = timelineAppearance.avatarShape,
                        onSelected = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.AvatarShape,
                                    it,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
    TimelineFilterDialog(
        visible = showFilterDialog,
        filterConfig = filterConfig,
        onDismissRequest = { showFilterDialog = false },
        onConfirm = {
            onFilterConfigChange(it)
            showFilterDialog = false
        },
    )
}

@Composable
private fun TimelineFilterSettingsItem(onClick: () -> Unit) {
    CardExpanderItem(
        icon = null,
        heading = {
            Text(stringResource(Res.string.tab_settings_filter_title))
        },
        caption = {
            Text(stringResource(Res.string.tab_settings_filter_desc))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TimelineFilterDialog(
    visible: Boolean,
    filterConfig: TimelineFilterConfig,
    onDismissRequest: () -> Unit,
    onConfirm: (TimelineFilterConfig) -> Unit,
) {
    val kindOptions =
        remember {
            listOf(
                TimelinePostKind.Reply,
                TimelinePostKind.Repost,
                TimelinePostKind.Quote,
            )
        }
    val contentOptions =
        remember {
            listOf(
                TimelinePostContent.Text,
                TimelinePostContent.Image,
                TimelinePostContent.Video,
            )
        }
    var selectedKinds by remember(filterConfig) {
        mutableStateOf(kindOptions.filterNot { it in filterConfig.excludedKinds }.toSet())
    }
    var selectedContents by remember(filterConfig) {
        mutableStateOf(contentOptions.filterNot { it in filterConfig.excludedContents }.toSet())
    }
    ContentDialog(
        visible = visible,
        title = stringResource(Res.string.tab_settings_filter_title),
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    onConfirm(
                        TimelineFilterConfig(
                            excludedKinds = kindOptions.filterNot { option -> option in selectedKinds },
                            excludedContents = contentOptions.filterNot { option -> option in selectedContents },
                        ),
                    )
                }

                ContentDialogButton.Secondary,
                ContentDialogButton.Close,
                -> {
                    onDismissRequest()
                }
            }
        },
        content = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterSection(
                    title = stringResource(Res.string.tab_settings_filter_kind_group),
                    options = kindOptions,
                    selected = selectedKinds,
                    label = ::filterKindLabel,
                    onToggle = { option ->
                        selectedKinds =
                            if (option in selectedKinds) {
                                selectedKinds - option
                            } else {
                                selectedKinds + option
                            }
                    },
                )
                FilterSection(
                    title = stringResource(Res.string.tab_settings_filter_content_group),
                    options = contentOptions,
                    selected = selectedContents,
                    label = ::filterContentLabel,
                    onToggle = { option ->
                        selectedContents =
                            if (option in selectedContents) {
                                selectedContents - option
                            } else {
                                selectedContents + option
                            }
                    },
                )
            }
        },
    )
}

@Composable
private fun <T> FilterSection(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title)
        LazyColumn(
            modifier = Modifier.heightIn(max = 160.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(options) { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(option) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CheckBox(
                        checked = option in selected,
                        onCheckStateChange = { onToggle(option) },
                    )
                    Text(label(option))
                }
            }
        }
    }
}

@Composable
private fun filterKindLabel(kind: TimelinePostKind): String =
    when (kind) {
        TimelinePostKind.Reply -> stringResource(Res.string.tab_settings_filter_reply)
        TimelinePostKind.Repost -> stringResource(Res.string.tab_settings_filter_repost)
        TimelinePostKind.Quote -> stringResource(Res.string.tab_settings_filter_quote)
        TimelinePostKind.Original -> error("Original is not exposed in timeline filter UI")
    }

@Composable
private fun filterContentLabel(content: TimelinePostContent): String =
    when (content) {
        TimelinePostContent.Text -> stringResource(Res.string.tab_settings_filter_text_only)
        TimelinePostContent.Image -> stringResource(Res.string.tab_settings_filter_image)
        TimelinePostContent.Video -> stringResource(Res.string.tab_settings_filter_video)
        TimelinePostContent.Other -> error("Other is not exposed in timeline filter UI")
    }

@Composable
internal fun TimelinePresentationHeaderEditor(
    text: TextFieldState,
    icon: IconType,
    availableIcons: List<IconType>,
    showIconPicker: Boolean,
    onShowIconPickerChange: (Boolean) -> Unit,
    onIconChange: (IconType) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val title = UiText.Raw(text.text.toString())
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TabIcon(
                icon = icon,
                title = title,
                size = 36.dp,
                modifier = Modifier.clickable { onShowIconPickerChange(true) },
            )
            Flyout(
                visible = showIconPicker,
                onDismissRequest = { onShowIconPickerChange(false) },
                placement = FlyoutPlacement.BottomAlignedStart,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.FixedSize(48.dp),
                    modifier = Modifier.heightIn(max = 300.dp).widthIn(max = 300.dp),
                ) {
                    items(availableIcons) { selectedIcon ->
                        TabIcon(
                            icon = selectedIcon,
                            title = title,
                            modifier =
                                Modifier
                                    .padding(4.dp)
                                    .clickable {
                                        onIconChange(selectedIcon)
                                        onShowIconPickerChange(false)
                                    },
                            size = 48.dp,
                        )
                    }
                }
            }
        }

        TextField(
            state = text,
            modifier = Modifier.weight(1f),
            header = header,
            placeholder = placeholder,
            lineLimits = TextFieldLineLimits.SingleLine,
        )
    }
}

@Composable
private fun AppearanceOverrideExpander(
    title: String,
    subtitle: String,
    overridesEnabled: Boolean,
    onOverridesEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(overridesEnabled) {
        expanded = overridesEnabled
    }
    Expander(
        icon = null,
        expanded = expanded && overridesEnabled,
        onExpandedChanged = { expanded = it },
        heading = { Text(title) },
        caption = { Text(subtitle) },
        trailing = {
            Switcher(
                checked = overridesEnabled,
                onCheckStateChange = onOverridesEnabledChange,
                textBefore = true,
            )
        },
    ) {
        content()
    }
}

@Composable
private fun SwitchItem(
    title: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ExpanderItem(
        heading = { Text(title) },
        caption = { Text(caption) },
        trailing = {
            Switcher(
                checked = checked,
                onCheckStateChange = onCheckedChange,
                textBefore = true,
            )
        },
    )
}

@Composable
private fun <T> ChoiceItem(
    title: String,
    caption: String,
    items: ImmutableMap<T, StringResource>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    ExpanderItem(
        heading = { Text(title) },
        caption = { Text(caption) },
        trailing = {
            MenuFlyoutContainer(
                flyout = {
                    items.forEach { (key, value) ->
                        MenuFlyoutItem(
                            onClick = {
                                onSelected(key)
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
                            items[selected]?.let {
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
}

private fun AppearancePatch.clearAll(vararg keys: AppearanceKey<*>): AppearancePatch =
    keys.fold(this) { patch, key ->
        patch.clear(key)
    }
