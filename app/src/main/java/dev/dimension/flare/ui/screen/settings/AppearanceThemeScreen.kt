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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.persistentMapOf
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceThemeScreen(
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
                    Text(text = stringResource(id = R.string.settings_appearance_theme_group_title))
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
                shapes = ListItemDefaults.item(),
            )

            var fontSizeDiff by remember { mutableFloatStateOf(appearanceSettings.fontSizeDiff) }
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.last(),
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
        }
    }
}
