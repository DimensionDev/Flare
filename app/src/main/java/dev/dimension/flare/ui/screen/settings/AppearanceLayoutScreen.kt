package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.BottomBarStyle
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.persistentMapOf
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceLayoutScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    val appearanceSettings = LocalAppearanceSettings.current
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_layout_group_title))
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
                    shapes = ListItemDefaults.first(),
                )
                SingleChoiceSettingsItem(
                    headline = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_behavior)) },
                    supporting = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_behavior_description)) },
                    items =
                        persistentMapOf(
                            BottomBarBehavior.AlwaysShow to stringResource(id = R.string.settings_appearance_bottombar_behavior_fixed),
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
                SingleChoiceSettingsItem(
                    headline = { Text(text = stringResource(id = R.string.settings_appearance_timeline_display_mode)) },
                    supporting = { Text(text = stringResource(id = R.string.settings_appearance_timeline_display_mode_description)) },
                    items =
                        persistentMapOf(
                            TimelineDisplayMode.Card to stringResource(id = R.string.settings_appearance_timeline_display_mode_card),
                            TimelineDisplayMode.Plain to stringResource(id = R.string.settings_appearance_timeline_display_mode_plain),
                            TimelineDisplayMode.Gallery to stringResource(id = R.string.settings_appearance_timeline_display_mode_gallery),
                        ),
                    selected = appearanceSettings.timelineDisplayMode,
                    onSelected = {
                        state.updateSettings {
                            copy(timelineDisplayMode = it)
                        }
                    },
                    shapes = ListItemDefaults.last(),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                state.sampleStatus.onSuccess { sample ->
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.first(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        StatusItem(
                            sample,
                            modifier =
                                Modifier
                                    .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(fullWidthPost = !fullWidthPost)
                        }
                    },
                    shapes =
                        if (state.sampleStatus.isSuccess) {
                            ListItemDefaults.item()
                        } else {
                            ListItemDefaults.first()
                        },
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
                            PostActionStyle.LeftAligned to
                                stringResource(id = R.string.settings_appearance_post_action_style_left_aligned),
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
                    shapes =
                        if (appearanceSettings.postActionStyle != PostActionStyle.Hidden) {
                            ListItemDefaults.item()
                        } else {
                            ListItemDefaults.last()
                        },
                )
                AnimatedVisibility(appearanceSettings.postActionStyle != PostActionStyle.Hidden) {
                    SegmentedListItem(
                        onClick = {
                            state.updateSettings {
                                copy(showNumbers = !showNumbers)
                            }
                        },
                        shapes = ListItemDefaults.last(),
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
            }
        }
    }
}
