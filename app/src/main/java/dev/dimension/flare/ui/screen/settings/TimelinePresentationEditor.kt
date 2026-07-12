package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.dimension.flare.R
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
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.screen.home.TimelineFilterDialog
import dev.dimension.flare.ui.screen.home.TimelineFilterSettingsItem
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.single
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TimelinePresentationEditor(
    text: TextFieldState,
    icon: IconType,
    availableIcons: ImmutableList<IconType>,
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
    behaviorContent: (@Composable ColumnScope.() -> Unit)? = null,
    label: @Composable (() -> Unit)? = { Text(text = stringResource(id = R.string.edit_tab_name)) },
    placeholder: @Composable (() -> Unit)? = { Text(text = stringResource(id = R.string.edit_tab_name_placeholder)) },
) {
    val layoutOverridesEnabled =
        appearancePatch.contains(AppearanceKeys.TimelineDisplayMode) ||
            appearancePatch.contains(AppearanceKeys.FullWidthPost) ||
            appearancePatch.contains(AppearanceKeys.PostActionStyle) ||
            appearancePatch.contains(AppearanceKeys.PostActionFixedWidth) ||
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimelinePresentationHeaderEditor(
            text = text,
            icon = icon,
            availableIcons = availableIcons,
            showIconPicker = showIconPicker,
            onShowIconPickerChange = onShowIconPickerChange,
            onIconChange = onIconChange,
            label = label,
            placeholder = placeholder,
        )

        if (canUseAvatar || showEnabled) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (canUseAvatar) {
                    SegmentedListItem(
                        onClick = { onWithAvatarChange(!withAvatar) },
                        shapes =
                            if (showEnabled || behaviorContent == null) {
                                ListItemDefaults.first()
                            } else {
                                ListItemDefaults.single()
                            },
                        content = { Text(text = stringResource(id = R.string.edit_tab_with_avatar)) },
                        trailingContent = {
                            Checkbox(
                                checked = withAvatar,
                                onCheckedChange = onWithAvatarChange,
                            )
                        },
                    )
                }
                if (showEnabled) {
                    SegmentedListItem(
                        onClick = { onEnabledChange(!enabled) },
                        shapes =
                            when {
                                canUseAvatar -> ListItemDefaults.item()
                                behaviorContent == null -> ListItemDefaults.first()
                                else -> ListItemDefaults.first()
                            },
                        content = { Text(text = stringResource(id = R.string.edit_tab_enabled)) },
                        supportingContent = { Text(text = stringResource(id = R.string.edit_tab_enabled_description)) },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = onEnabledChange,
                            )
                        },
                    )
                }
                if (behaviorContent == null) {
                    TimelineFilterSettingsItem(
                        filterConfig = filterConfig,
                        onClick = { showFilterDialog = true },
                        shapes =
                            if (canUseAvatar || showEnabled) {
                                ListItemDefaults.last()
                            } else {
                                ListItemDefaults.single()
                            },
                    )
                } else {
                    behaviorContent()
                    TimelineFilterSettingsItem(
                        filterConfig = filterConfig,
                        onClick = { showFilterDialog = true },
                        shapes = ListItemDefaults.last(),
                    )
                }
            }
        } else {
            behaviorContent?.let {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    it()
                    TimelineFilterSettingsItem(
                        filterConfig = filterConfig,
                        onClick = { showFilterDialog = true },
                        shapes = ListItemDefaults.last(),
                    )
                }
            } ?: TimelineFilterSettingsItem(
                filterConfig = filterConfig,
                onClick = { showFilterDialog = true },
                shapes = ListItemDefaults.single(),
            )
        }
        if (showAppearanceOverrides) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                AppearanceExpander(
                    title = stringResource(id = R.string.settings_appearance_layout_group_title),
                    shapes = ListItemDefaults.first(),
                    overridesEnabled = layoutOverridesEnabled,
                    onOverridesEnabledChange = {
                        onAppearancePatchChange(
                            if (it) {
                                appearancePatch
                                    .set(
                                        AppearanceKeys.TimelineDisplayMode,
                                        timelineAppearance.timelineDisplayMode,
                                    ).set(AppearanceKeys.FullWidthPost, timelineAppearance.fullWidthPost)
                                    .set(
                                        AppearanceKeys.PostActionStyle,
                                        timelineAppearance.postActionStyle,
                                    ).set(AppearanceKeys.ShowNumbers, timelineAppearance.showNumbers)
                                    .set(
                                        AppearanceKeys.PostActionFixedWidth,
                                        timelineAppearance.postActionFixedWidth,
                                    )
                            } else {
                                appearancePatch.clearAll(
                                    AppearanceKeys.TimelineDisplayMode,
                                    AppearanceKeys.FullWidthPost,
                                    AppearanceKeys.PostActionStyle,
                                    AppearanceKeys.PostActionFixedWidth,
                                    AppearanceKeys.ShowNumbers,
                                )
                            },
                        )
                    },
                ) {
                    SingleChoiceSettingsItem(
                        headline = { Text(text = stringResource(id = R.string.settings_appearance_timeline_display_mode)) },
                        supporting = { Text(text = stringResource(id = R.string.settings_appearance_timeline_display_mode_description)) },
                        items =
                            persistentMapOf(
                                TimelineDisplayMode.Card to stringResource(id = R.string.settings_appearance_timeline_display_mode_card),
                                TimelineDisplayMode.Plain to stringResource(id = R.string.settings_appearance_timeline_display_mode_plain),
                                TimelineDisplayMode.Gallery to
                                    stringResource(id = R.string.settings_appearance_timeline_display_mode_gallery),
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
                        shapes = ListItemDefaults.first(),
                    )
                    SwitchSettingsItem(
                        title = stringResource(id = R.string.settings_appearance_full_width_post),
                        description = stringResource(id = R.string.settings_appearance_full_width_post_description),
                        checked = timelineAppearance.fullWidthPost,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.FullWidthPost,
                                    it,
                                ),
                            )
                        },
                        shapes = ListItemDefaults.item(),
                    )
                    SingleChoiceSettingsItem(
                        headline = { Text(text = stringResource(id = R.string.settings_appearance_post_action_style)) },
                        supporting = { Text(text = stringResource(id = R.string.settings_appearance_post_action_style_description)) },
                        items =
                            persistentMapOf(
                                PostActionStyle.Hidden to stringResource(id = R.string.settings_appearance_post_action_style_hidden),
                                PostActionStyle.LeftAligned to
                                    stringResource(id = R.string.settings_appearance_post_action_style_left_aligned),
                                PostActionStyle.RightAligned to
                                    stringResource(id = R.string.settings_appearance_post_action_style_right_aligned),
                                PostActionStyle.Stretch to stringResource(id = R.string.settings_appearance_post_action_style_stretch),
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
                        shapes =
                            if (timelineAppearance.postActionStyle ==
                                PostActionStyle.Hidden
                            ) {
                                ListItemDefaults.last()
                            } else {
                                ListItemDefaults.item()
                            },
                    )
                    AnimatedVisibility(timelineAppearance.postActionStyle != PostActionStyle.Hidden) {
                        SwitchSettingsItem(
                            title = stringResource(id = R.string.settings_appearance_show_numbers),
                            description = stringResource(id = R.string.settings_appearance_show_numbers_description),
                            checked = timelineAppearance.showNumbers,
                            onCheckedChange = {
                                onAppearancePatchChange(
                                    appearancePatch.set(
                                        AppearanceKeys.ShowNumbers,
                                        it,
                                    ),
                                )
                            },
                            shapes = ListItemDefaults.item(),
                        )
                        SwitchSettingsItem(
                            title = stringResource(id = R.string.settings_post_action_fixed_width),
                            description = stringResource(id = R.string.settings_post_action_fixed_width_description),
                            checked = timelineAppearance.postActionFixedWidth,
                            onCheckedChange = {
                                onAppearancePatchChange(
                                    appearancePatch.set(
                                        AppearanceKeys.PostActionFixedWidth,
                                        it,
                                    ),
                                )
                            },
                            shapes = ListItemDefaults.last(),
                        )
                    }
                }

                AppearanceExpander(
                    title = stringResource(id = R.string.settings_appearance_display_group_title),
                    shapes = ListItemDefaults.item(),
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
                    SwitchSettingsItem(
                        title = stringResource(id = R.string.settings_appearance_absolute_timestamp),
                        description = stringResource(id = R.string.settings_appearance_absolute_timestamp_description),
                        checked = timelineAppearance.absoluteTimestamp,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.AbsoluteTimestamp,
                                    it,
                                ),
                            )
                        },
                        shapes = ListItemDefaults.first(),
                    )
                    SwitchSettingsItem(
                        title = stringResource(id = R.string.settings_appearance_show_platform_logo),
                        description = stringResource(id = R.string.settings_appearance_show_platform_logo_description),
                        checked = timelineAppearance.showPlatformLogo,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.ShowPlatformLogo,
                                    it,
                                ),
                            )
                        },
                        shapes = ListItemDefaults.item(),
                    )
                    SwitchSettingsItem(
                        title = stringResource(id = R.string.settings_appearance_show_link_previews),
                        description = stringResource(id = R.string.settings_appearance_show_link_previews_description),
                        checked = timelineAppearance.showLinkPreview,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.ShowLinkPreview,
                                    it,
                                ),
                            )
                        },
                        shapes = if (timelineAppearance.showLinkPreview) ListItemDefaults.item() else ListItemDefaults.last(),
                    )
                    AnimatedVisibility(timelineAppearance.showLinkPreview) {
                        SwitchSettingsItem(
                            title = stringResource(id = R.string.settings_appearance_compat_link_previews),
                            description = stringResource(id = R.string.settings_appearance_compat_link_previews_description),
                            checked = timelineAppearance.compatLinkPreview,
                            onCheckedChange = {
                                onAppearancePatchChange(
                                    appearancePatch.set(
                                        AppearanceKeys.CompatLinkPreview,
                                        it,
                                    ),
                                )
                            },
                            shapes = ListItemDefaults.last(),
                        )
                    }
                }

                AppearanceExpander(
                    title = stringResource(id = R.string.settings_appearance_media_group_title),
                    shapes = ListItemDefaults.item(),
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
                                    ).set(AppearanceKeys.VideoAutoplay, timelineAppearance.videoAutoplay)
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
                    SwitchSettingsItem(
                        title = stringResource(id = R.string.settings_appearance_show_media),
                        description = stringResource(id = R.string.settings_appearance_show_media_description),
                        checked = timelineAppearance.showMedia,
                        onCheckedChange = {
                            onAppearancePatchChange(
                                appearancePatch.set(
                                    AppearanceKeys.ShowMedia,
                                    it,
                                ),
                            )
                        },
                        shapes = if (timelineAppearance.showMedia) ListItemDefaults.first() else ListItemDefaults.single(),
                    )
                    AnimatedVisibility(timelineAppearance.showMedia) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            SwitchSettingsItem(
                                title = stringResource(id = R.string.settings_appearance_show_cw_img),
                                description = stringResource(id = R.string.settings_appearance_show_cw_img_description),
                                checked = timelineAppearance.showSensitiveContent,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.ShowSensitiveContent,
                                            it,
                                        ),
                                    )
                                },
                                shapes = ListItemDefaults.item(),
                            )
                            SwitchSettingsItem(
                                title = stringResource(id = R.string.settings_appearance_expand_content_warning),
                                description = stringResource(id = R.string.settings_appearance_expand_content_warning_description),
                                checked = timelineAppearance.expandContentWarning,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.ExpandContentWarning,
                                            it,
                                        ),
                                    )
                                },
                                shapes = ListItemDefaults.item(),
                            )
                            SwitchSettingsItem(
                                title = stringResource(id = R.string.settings_appearance_limit_media_grid_to_nine),
                                description = stringResource(id = R.string.settings_appearance_limit_media_grid_to_nine_description),
                                checked = timelineAppearance.limitMediaGridToNine,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.LimitMediaGridToNine,
                                            it,
                                        ),
                                    )
                                },
                                shapes = ListItemDefaults.item(),
                            )
                            SwitchSettingsItem(
                                title = stringResource(id = R.string.settings_appearance_expand_media),
                                description = stringResource(id = R.string.settings_appearance_expand_media_description),
                                checked = timelineAppearance.expandMediaSize,
                                onCheckedChange = {
                                    onAppearancePatchChange(
                                        appearancePatch.set(
                                            AppearanceKeys.ExpandMediaSize,
                                            it,
                                        ),
                                    )
                                },
                                shapes = ListItemDefaults.item(),
                            )
                            SingleChoiceSettingsItem(
                                headline = { Text(text = stringResource(id = R.string.settings_appearance_video_autoplay)) },
                                supporting = { Text(text = stringResource(id = R.string.settings_appearance_video_autoplay_description)) },
                                items =
                                    persistentMapOf(
                                        VideoAutoplay.WIFI to stringResource(id = R.string.settings_appearance_video_autoplay_wifi),
                                        VideoAutoplay.ALWAYS to stringResource(id = R.string.settings_appearance_video_autoplay_always),
                                        VideoAutoplay.NEVER to stringResource(id = R.string.settings_appearance_video_autoplay_never),
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
                                shapes = ListItemDefaults.last(),
                            )
                        }
                    }
                }

                AppearanceExpander(
                    title = stringResource(id = R.string.settings_appearance_theme_group_title),
                    shapes = ListItemDefaults.last(),
                    overridesEnabled = themeOverridesEnabled,
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
                    SingleChoiceSettingsItem(
                        headline = { Text(text = stringResource(id = R.string.settings_appearance_avatar_shape)) },
                        supporting = { Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_description)) },
                        items =
                            persistentMapOf(
                                AvatarShape.CIRCLE to stringResource(id = R.string.settings_appearance_avatar_shape_round),
                                AvatarShape.SQUARE to stringResource(id = R.string.settings_appearance_avatar_shape_square),
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
                        shapes = ListItemDefaults.single(),
                    )
                }
            }
        }
    }
    if (showFilterDialog) {
        TimelineFilterDialog(
            filterConfig = filterConfig,
            onDismissRequest = { showFilterDialog = false },
            onConfirm = {
                onFilterConfigChange(it)
                showFilterDialog = false
            },
        )
    }
}

@Composable
internal fun TimelinePresentationHeaderEditor(
    text: TextFieldState,
    icon: IconType,
    availableIcons: ImmutableList<IconType>,
    showIconPicker: Boolean,
    onShowIconPickerChange: (Boolean) -> Unit,
    onIconChange: (IconType) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
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
                size = 64.dp,
                modifier = Modifier.clickable { onShowIconPickerChange(true) },
            )

            if (showIconPicker) {
                Popup(
                    onDismissRequest = { onShowIconPickerChange(false) },
                    alignment = Alignment.BottomCenter,
                    properties =
                        PopupProperties(
                            usePlatformDefaultWidth = true,
                            focusable = true,
                        ),
                ) {
                    Card(
                        modifier = Modifier.sizeIn(maxHeight = 256.dp, maxWidth = 384.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                    ) {
                        LazyVerticalGrid(columns = GridCells.FixedSize(48.dp)) {
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
            }
        }

        OutlinedTextField(
            state = text,
            modifier = Modifier.weight(1f),
            label = label?.let { content -> { content() } },
            placeholder = placeholder?.let { content -> { content() } },
        )
    }
}

@Composable
private fun AppearanceExpander(
    title: String,
    shapes: ListItemShapes,
    overridesEnabled: Boolean,
    onOverridesEnabledChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(overridesEnabled) {
        expanded = overridesEnabled
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        SegmentedListItem(
            onClick = { expanded = !expanded },
            shapes = shapes,
            content = { Text(text = title) },
            trailingContent = {
                Switch(
                    checked = overridesEnabled,
                    onCheckedChange = onOverridesEnabledChange,
                )
            },
        )
        AnimatedVisibility(expanded && overridesEnabled) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                content = content,
            )
        }
    }
}

@Composable
private fun SwitchSettingsItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        content = { Text(text = title) },
        supportingContent = { Text(text = description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

private fun AppearancePatch.clearAll(vararg keys: AppearanceKey<*>): AppearancePatch =
    keys.fold(this) { patch, key ->
        patch.clear(key)
    }
