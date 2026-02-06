package dev.dimension.flare.ui.screen.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Minus
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.BottomBarStyle
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AppearancePresenter
import dev.dimension.flare.ui.presenter.settings.AppearanceState
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceScreen(
    onBack: () -> Unit,
    toColorPicker: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    val hapticFeedback = LocalHapticFeedback.current
    val appearanceSettings = LocalAppearanceSettings.current
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_title))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SingleChoiceSettingsItem(
                    headline = { Text(text = stringResource(id = R.string.settings_appearance_theme)) },
                    supporting = { Text(text = stringResource(id = R.string.settings_appearance_theme_description)) },
                    items =
                        persistentMapOf(
                            Theme.LIGHT to stringResource(id = R.string.settings_appearance_theme_light),
                            Theme.SYSTEM to stringResource(id = R.string.settings_appearance_theme_auto),
                            Theme.DARK to stringResource(id = R.string.settings_appearance_theme_dark),
                        ),
                    selected = appearanceSettings.theme,
                    onSelected = {
                        state.updateSettings {
                            copy(theme = it)
                        }
                    },
                    shapes = ListItemDefaults.first(),
                )
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(pureColorMode = !pureColorMode)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_theme_pure_color))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_theme_pure_color_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.pureColorMode,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(pureColorMode = it)
                                }
                            },
                        )
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SegmentedListItem(
                        onClick = {
                            state.updateSettings {
                                copy(dynamicTheme = !dynamicTheme)
                            }
                        },
                        shapes = ListItemDefaults.item(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_appearance_dynamic_theme))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_dynamic_theme_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = appearanceSettings.dynamicTheme,
                                onCheckedChange = {
                                    state.updateSettings {
                                        copy(dynamicTheme = it)
                                    }
                                },
                            )
                        },
                    )
                }
                AnimatedVisibility(visible = !appearanceSettings.dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    SegmentedListItem(
                        onClick = {
                            toColorPicker.invoke()
                        },
                        shapes = ListItemDefaults.item(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_appearance_theme_color))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_theme_color_description))
                        },
                        trailingContent = {
                            Box(
                                modifier =
                                    Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                        ).size(36.dp),
                            )
                        },
                    )
                }

                SingleChoiceSettingsItem(
                    headline = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_style)) },
                    supporting = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_style_description)) },
                    items =
                        persistentMapOf(
                            BottomBarStyle.Floating to stringResource(id = R.string.settings_appearance_bottombar_style_floating),
                            BottomBarStyle.Classic to stringResource(id = R.string.settings_appearance_bottombar_style_classic),
                        ),
                    selected = appearanceSettings.bottomBarStyle,
                    onSelected = {
                        state.updateSettings {
                            copy(bottomBarStyle = it)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                )

                SingleChoiceSettingsItem(
                    headline = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_behavior)) },
                    supporting = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_behavior_description)) },
                    items =
                        persistentMapOf(
                            BottomBarBehavior.AlwaysShow to
                                stringResource(id = R.string.settings_appearance_bottombar_behavior_fixed),
                            BottomBarBehavior.MinimizeOnScroll to
                                stringResource(id = R.string.settings_appearance_bottombar_behavior_minimize_on_scroll),
                            BottomBarBehavior.HideOnScroll to
                                stringResource(id = R.string.settings_appearance_bottombar_behavior_hide_on_scroll),
                        ),
                    selected = appearanceSettings.bottomBarBehavior,
                    onSelected = {
                        state.updateSettings {
                            copy(bottomBarBehavior = it)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                )

                var fontSizeDiff by remember { mutableFloatStateOf(appearanceSettings.fontSizeDiff) }
                SegmentedListItem(
                    onClick = {},
                    shapes = ListItemDefaults.item(),
                    content = {
                        Column {
                            Text(
                                text = stringResource(id = R.string.settings_appearance_font_size_diff),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = stringResource(id = R.string.settings_appearance_font_size_diff_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        if (fontSizeDiff > -4f) {
                                            fontSizeDiff -= 1f
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            state.updateSettings {
                                                copy(
                                                    fontSizeDiff = fontSizeDiff,
                                                    lineHeightDiff = fontSizeDiff * 2,
                                                )
                                            }
                                        }
                                    },
                                    enabled = fontSizeDiff > -4f,
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Minus,
                                        contentDescription = stringResource(R.string.settings_appearance_font_size_diff_decrease),
                                    )
                                }
                                Slider(
                                    value = fontSizeDiff,
                                    onValueChange = {
                                        fontSizeDiff = it
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    },
                                    onValueChangeFinished = {
                                        state.updateSettings {
                                            copy(
                                                fontSizeDiff = fontSizeDiff,
                                                lineHeightDiff = fontSizeDiff * 2,
                                            )
                                        }
                                    },
                                    valueRange = -4f..4f,
                                    steps = 7,
                                    modifier =
                                        Modifier
                                            .weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        if (fontSizeDiff < 4f) {
                                            fontSizeDiff += 1f
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            state.updateSettings {
                                                copy(
                                                    fontSizeDiff = fontSizeDiff,
                                                    lineHeightDiff = fontSizeDiff * 2,
                                                )
                                            }
                                        }
                                    },
                                    enabled = fontSizeDiff < 4f,
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Plus,
                                        contentDescription = stringResource(R.string.settings_appearance_font_size_diff_increase),
                                    )
                                }
                            }
                        }
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(inAppBrowser = !inAppBrowser)
                        }
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_in_app_browser))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_in_app_browser_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.inAppBrowser,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(inAppBrowser = it)
                                }
                            },
                        )
                    },
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                state.sampleStatus.onSuccess {
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.first(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        StatusItem(
                            it,
                            modifier =
                                Modifier
                                    .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
                SingleChoiceSettingsItem(
                    headline = { Text(text = stringResource(id = R.string.settings_appearance_avatar_shape)) },
                    supporting = { Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_description)) },
                    items =
                        persistentMapOf(
                            AvatarShape.CIRCLE to stringResource(id = R.string.settings_appearance_avatar_shape_round),
                            AvatarShape.SQUARE to stringResource(id = R.string.settings_appearance_avatar_shape_square),
                        ),
                    selected = appearanceSettings.avatarShape,
                    onSelected = {
                        state.updateSettings {
                            copy(avatarShape = it)
                        }
                    },
                    shapes =
                        if (state.sampleStatus.isSuccess) {
                            ListItemDefaults.item()
                        } else {
                            ListItemDefaults.first()
                        },
                )
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(fullWidthPost = !fullWidthPost)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_full_width_post))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_full_width_post_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.fullWidthPost,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(fullWidthPost = it)
                                }
                            },
                        )
                    },
                )
                SingleChoiceSettingsItem(
                    headline = { Text(text = stringResource(id = R.string.settings_appearance_post_action_style)) },
                    supporting = { Text(text = stringResource(id = R.string.settings_appearance_post_action_style_description)) },
                    items =
                        persistentMapOf(
                            PostActionStyle.Hidden to stringResource(id = R.string.settings_appearance_post_action_style_hidden),
                            PostActionStyle.LeftAligned to stringResource(id = R.string.settings_appearance_post_action_style_left_aligned),
                            PostActionStyle.RightAligned to
                                stringResource(id = R.string.settings_appearance_post_action_style_right_aligned),
                            PostActionStyle.Stretch to stringResource(id = R.string.settings_appearance_post_action_style_stretch),
                        ),
                    selected = appearanceSettings.postActionStyle,
                    onSelected = {
                        state.updateSettings {
                            copy(postActionStyle = it)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                )
                AnimatedVisibility(appearanceSettings.postActionStyle != PostActionStyle.Hidden) {
                    SegmentedListItem(
                        onClick = {
                            state.updateSettings {
                                copy(showNumbers = !showNumbers)
                            }
                        },
                        shapes = ListItemDefaults.item(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_appearance_show_numbers))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_show_numbers_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = appearanceSettings.showNumbers,
                                onCheckedChange = {
                                    state.updateSettings {
                                        copy(showNumbers = it)
                                    }
                                },
                            )
                        },
                    )
                }
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(absoluteTimestamp = !absoluteTimestamp)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_absolute_timestamp))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_absolute_timestamp_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.absoluteTimestamp,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(absoluteTimestamp = it)
                                }
                            },
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(showPlatformLogo = !showPlatformLogo)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_platform_logo))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_platform_logo_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.showPlatformLogo,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(showPlatformLogo = it)
                                }
                            },
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(showLinkPreview = !showLinkPreview)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_link_previews))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_link_previews_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.showLinkPreview,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(showLinkPreview = it)
                                }
                            },
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(hideReposts = !hideReposts)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_hide_reposts))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_hide_reposts_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.hideReposts,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(hideReposts = it)
                                }
                            },
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(showMedia = !showMedia)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_media))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_media_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.showMedia,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(showMedia = it)
                                }
                            },
                        )
                    },
                )
                AnimatedVisibility(appearanceSettings.showMedia) {
                    SegmentedListItem(
                        onClick = {
                            state.updateSettings {
                                copy(showSensitiveContent = !showSensitiveContent)
                            }
                        },
                        shapes = ListItemDefaults.item(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_appearance_show_cw_img))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_show_cw_img_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = appearanceSettings.showSensitiveContent,
                                onCheckedChange = {
                                    state.updateSettings {
                                        copy(showSensitiveContent = it)
                                    }
                                },
                            )
                        },
                    )
                }
                AnimatedVisibility(appearanceSettings.showMedia) {
                    SegmentedListItem(
                        onClick = {
                            state.updateSettings {
                                copy(expandMediaSize = !expandMediaSize)
                            }
                        },
                        shapes = ListItemDefaults.item(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_appearance_expand_media))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_expand_media_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = appearanceSettings.expandMediaSize,
                                onCheckedChange = {
                                    state.updateSettings {
                                        copy(expandMediaSize = it)
                                    }
                                },
                            )
                        },
                    )
                }
                AnimatedVisibility(appearanceSettings.showMedia) {
                    SingleChoiceSettingsItem(
                        headline = { Text(text = stringResource(id = R.string.settings_appearance_video_autoplay)) },
                        supporting = { Text(text = stringResource(id = R.string.settings_appearance_video_autoplay_description)) },
                        items =
                            persistentMapOf(
                                VideoAutoplay.WIFI to stringResource(id = R.string.settings_appearance_video_autoplay_wifi),
                                VideoAutoplay.ALWAYS to stringResource(id = R.string.settings_appearance_video_autoplay_always),
                                VideoAutoplay.NEVER to stringResource(id = R.string.settings_appearance_video_autoplay_never),
                            ),
                        selected = appearanceSettings.videoAutoplay,
                        onSelected = {
                            state.updateSettings {
                                copy(videoAutoplay = it)
                            }
                        },
                        shapes = ListItemDefaults.last(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SingleChoiceSettingsItem(
    headline: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    items: ImmutableMap<T, String>,
    selected: T,
    onSelected: (T) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    val isBigScreen = isBigScreen()
    var showMenu by remember { mutableStateOf(false) }
    SegmentedListItem(
        modifier = modifier,
        checked = showMenu,
        onCheckedChange = {
            if (!isBigScreen) {
                showMenu = it
            }
        },
        shapes = shapes,
        content = headline,
        supportingContent = supporting,
        trailingContent = {
            if (isBigScreen) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val entries = items.entries.toList()
                    entries.forEachIndexed { index, (value, label) ->
                        ToggleButton(
                            checked = selected == value,
                            onCheckedChange = { onSelected(value) },
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                        ) {
                            Text(text = label)
                        }
                    }
                }
            } else {
                TextButton(onClick = { showMenu = true }) {
                    Text(text = items[selected] ?: "")
                }
                FlareDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    items.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                onSelected(value)
                                showMenu = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun appearancePresenter() =
    run {
        val scope = rememberCoroutineScope()
        val settingsRepository = koinInject<SettingsRepository>()
        val activeAccountState = remember { ActiveAccountPresenter() }.invoke()
        val appearanceState = remember { AppearancePresenter() }.invoke()

        object : UserState by activeAccountState, AppearanceState by appearanceState {
            fun updateSettings(block: AppearanceSettings.() -> AppearanceSettings) {
                scope.launch {
                    settingsRepository.updateAppearanceSettings(block)
                }
            }
        }
    }

private val VideoAutoplay.id: Int
    get() {
        return when (this) {
            VideoAutoplay.WIFI -> R.string.settings_appearance_video_autoplay_wifi
            VideoAutoplay.ALWAYS -> R.string.settings_appearance_video_autoplay_always
            VideoAutoplay.NEVER -> R.string.settings_appearance_video_autoplay_never
        }
    }
