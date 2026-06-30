package dev.dimension.flare.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.AndroidMediaSaveLocationRepository
import dev.dimension.flare.common.MediaSaveLocationMode
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import kotlinx.collections.immutable.persistentMapOf
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceMediaScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    val globalAppearance = LocalGlobalAppearance.current
    val timelineAppearance = LocalTimelineAppearance.current
    val mediaSaveLocationRepository = koinInject<AndroidMediaSaveLocationRepository>()
    val mediaSaveLocation by mediaSaveLocationRepository.state.collectAsState()
    var showMediaSaveLocationMenu by remember { mutableStateOf(false) }
    val mediaDirectoryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                uri?.let { mediaSaveLocationRepository.setCustomDirectory(it) }
            },
        )
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_media_group_title))
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
                    state.update(AppearanceKeys.ShowMedia, !timelineAppearance.showMedia)
                },
                shapes =
                    if (!state.sampleStatus.isSuccess) {
                        ListItemDefaults.first()
                    } else {
                        ListItemDefaults.item()
                    },
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_media))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_media_description))
                },
                trailingContent = {
                    Switch(
                        checked = timelineAppearance.showMedia,
                        onCheckedChange = {
                            state.update(AppearanceKeys.ShowMedia, it)
                        },
                    )
                },
            )
            AnimatedVisibility(timelineAppearance.showMedia) {
                SegmentedListItem(
                    onClick = {
                        state.update(AppearanceKeys.ShowSensitiveContent, !timelineAppearance.showSensitiveContent)
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
                            checked = timelineAppearance.showSensitiveContent,
                            onCheckedChange = {
                                state.update(AppearanceKeys.ShowSensitiveContent, it)
                            },
                        )
                    },
                )
            }
            AnimatedVisibility(timelineAppearance.showMedia) {
                SegmentedListItem(
                    onClick = {
                        state.update(AppearanceKeys.ExpandContentWarning, !timelineAppearance.expandContentWarning)
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_appearance_expand_content_warning))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_expand_content_warning_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = timelineAppearance.expandContentWarning,
                            onCheckedChange = {
                                state.update(AppearanceKeys.ExpandContentWarning, it)
                            },
                        )
                    },
                )
            }
            AnimatedVisibility(timelineAppearance.showMedia) {
                SegmentedListItem(
                    onClick = {
                        state.update(AppearanceKeys.ExpandMediaSize, !timelineAppearance.expandMediaSize)
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
                            checked = timelineAppearance.expandMediaSize,
                            onCheckedChange = {
                                state.update(AppearanceKeys.ExpandMediaSize, it)
                            },
                        )
                    },
                )
            }
            AnimatedVisibility(timelineAppearance.showMedia) {
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
                        state.update(AppearanceKeys.VideoAutoplay, it)
                    },
                    shapes = ListItemDefaults.item(),
                )
            }
            val mediaSaveLocationText =
                when (mediaSaveLocation.mode) {
                    MediaSaveLocationMode.DefaultDownloads -> {
                        stringResource(id = R.string.settings_media_save_location_downloads)
                    }

                    MediaSaveLocationMode.CustomDirectory -> {
                        mediaSaveLocation.displayName
                            ?: stringResource(id = R.string.settings_media_save_location_custom_folder)
                    }

                    MediaSaveLocationMode.AskEveryTime -> {
                        stringResource(id = R.string.settings_media_save_location_ask_every_time)
                    }
                }
            SegmentedListItem(
                onClick = {
                    showMediaSaveLocationMenu = true
                },
                shapes = ListItemDefaults.last(),
                content = {
                    Text(text = stringResource(id = R.string.settings_media_save_location))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_media_save_location_description))
                },
                trailingContent = {
                    Box {
                        TextButton(
                            onClick = {
                                showMediaSaveLocationMenu = true
                            },
                        ) {
                            Text(text = mediaSaveLocationText)
                        }
                        FlareDropdownMenu(
                            expanded = showMediaSaveLocationMenu,
                            onDismissRequest = {
                                showMediaSaveLocationMenu = false
                            },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.settings_media_save_location_choose_folder))
                                },
                                onClick = {
                                    showMediaSaveLocationMenu = false
                                    mediaDirectoryLauncher.launch(null)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.settings_media_save_location_ask_every_time))
                                },
                                onClick = {
                                    showMediaSaveLocationMenu = false
                                    mediaSaveLocationRepository.setAskEveryTime()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.settings_media_save_location_reset_downloads))
                                },
                                onClick = {
                                    showMediaSaveLocationMenu = false
                                    mediaSaveLocationRepository.setDefaultDownloads()
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}
