package dev.dimension.flare.ui.screen.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AppearancePresenter
import dev.dimension.flare.ui.presenter.settings.AppearanceState
import dev.dimension.flare.ui.theme.ListCardShapes
import dev.dimension.flare.ui.theme.screenHorizontalPadding
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
                modifier =
                    Modifier
                        .clip(ListCardShapes.container()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BoxWithConstraints {
                    var showMenu by remember { mutableStateOf(false) }
                    ListItem(
                        modifier =
                            Modifier
                                .clickable {
                                    if (maxWidth < 400.dp) {
                                        showMenu = true
                                    }
                                }.clip(ListCardShapes.item()),
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_theme))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_theme_description))
                        },
                        trailingContent = {
                            if (maxWidth >= 400.dp) {
                                val items =
                                    persistentMapOf(
                                        Theme.LIGHT to stringResource(id = R.string.settings_appearance_theme_light),
                                        Theme.SYSTEM to stringResource(id = R.string.settings_appearance_theme_auto),
                                        Theme.DARK to stringResource(id = R.string.settings_appearance_theme_dark),
                                    )
                                ButtonGroup(
                                    overflowIndicator = {},
                                ) {
                                    items.forEach { (theme, label) ->
                                        toggleableItem(
                                            checked = appearanceSettings.theme == theme,
                                            onCheckedChange = {
                                                state.updateSettings {
                                                    copy(theme = theme)
                                                }
                                            },
                                            label = label,
                                        )
                                    }
                                }
                            } else {
                                TextButton(onClick = {
                                    showMenu = true
                                }) {
                                    when (appearanceSettings.theme) {
                                        Theme.LIGHT ->
                                            Text(text = stringResource(id = R.string.settings_appearance_theme_light))

                                        Theme.SYSTEM ->
                                            Text(text = stringResource(id = R.string.settings_appearance_theme_auto))

                                        Theme.DARK ->
                                            Text(text = stringResource(id = R.string.settings_appearance_theme_dark))
                                    }
                                }
                                FlareDropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(id = R.string.settings_appearance_theme_light))
                                        },
                                        onClick = {
                                            state.updateSettings {
                                                copy(theme = Theme.LIGHT)
                                            }
                                            showMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(id = R.string.settings_appearance_theme_auto))
                                        },
                                        onClick = {
                                            state.updateSettings {
                                                copy(theme = Theme.SYSTEM)
                                            }
                                            showMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(id = R.string.settings_appearance_theme_dark))
                                        },
                                        onClick = {
                                            state.updateSettings {
                                                copy(theme = Theme.DARK)
                                            }
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
                ListItem(
                    headlineContent = {
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
                    modifier =
                        Modifier
                            .clickable {
                                state.updateSettings {
                                    copy(pureColorMode = !pureColorMode)
                                }
                            }.clip(ListCardShapes.item()),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = {
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
                        modifier =
                            Modifier
                                .clickable {
                                    state.updateSettings {
                                        copy(dynamicTheme = !dynamicTheme)
                                    }
                                }.clip(ListCardShapes.item()),
                    )
                }
                AnimatedVisibility(visible = !appearanceSettings.dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = {
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
                        modifier =
                            Modifier
                                .clickable {
                                    toColorPicker.invoke()
                                }.clip(ListCardShapes.item()),
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .clip(ListCardShapes.container()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.sampleStatus.onSuccess {
                    StatusItem(
                        it,
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .clip(ListCardShapes.item()),
                    )
                }
                BoxWithConstraints {
                    var showMenu by remember { mutableStateOf(false) }
                    ListItem(
                        modifier =
                            Modifier
                                .clickable {
                                    if (maxWidth < 400.dp) {
                                        showMenu = true
                                    }
                                }.clip(ListCardShapes.item()),
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_description))
                        },
                        trailingContent = {
                            if (maxWidth >= 400.dp) {
                                val items =
                                    persistentMapOf(
                                        AvatarShape.CIRCLE to stringResource(id = R.string.settings_appearance_avatar_shape_round),
                                        AvatarShape.SQUARE to stringResource(id = R.string.settings_appearance_avatar_shape_square),
                                    )
                                ButtonGroup(
                                    overflowIndicator = {},
                                ) {
                                    items.forEach { (shape, label) ->
                                        toggleableItem(
                                            checked = appearanceSettings.avatarShape == shape,
                                            onCheckedChange = {
                                                state.updateSettings {
                                                    copy(avatarShape = shape)
                                                }
                                            },
                                            label = label,
                                        )
                                    }
                                }
                            } else {
                                TextButton(onClick = {
                                    showMenu = true
                                }) {
                                    when (appearanceSettings.avatarShape) {
                                        AvatarShape.CIRCLE ->
                                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_round))

                                        AvatarShape.SQUARE ->
                                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_square))
                                    }
                                }
                                FlareDropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_round))
                                        },
                                        onClick = {
                                            state.updateSettings {
                                                copy(avatarShape = AvatarShape.CIRCLE)
                                            }
                                            showMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_square))
                                        },
                                        onClick = {
                                            state.updateSettings {
                                                copy(avatarShape = AvatarShape.SQUARE)
                                            }
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_actions))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_show_actions_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.showActions,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(showActions = it)
                                }
                            },
                        )
                    },
                    modifier =
                        Modifier
                            .clickable {
                                state.updateSettings {
                                    copy(showActions = !showActions)
                                }
                            }.clip(ListCardShapes.item()),
                )
                AnimatedVisibility(appearanceSettings.showActions) {
                    ListItem(
                        headlineContent = {
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
                        modifier =
                            Modifier
                                .clickable {
                                    state.updateSettings {
                                        copy(showNumbers = !showNumbers)
                                    }
                                }.clip(ListCardShapes.item()),
                    )
                }
                ListItem(
                    headlineContent = {
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
                    modifier =
                        Modifier
                            .clickable {
                                state.updateSettings {
                                    copy(showLinkPreview = !showLinkPreview)
                                }
                            }.clip(ListCardShapes.item()),
                )
                AnimatedVisibility(visible = appearanceSettings.showLinkPreview) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_compat_link_previews))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_compat_link_previews_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = appearanceSettings.compatLinkPreview,
                                onCheckedChange = {
                                    state.updateSettings {
                                        copy(compatLinkPreview = it)
                                    }
                                },
                            )
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    state.updateSettings {
                                        copy(compatLinkPreview = !compatLinkPreview)
                                    }
                                }.clip(ListCardShapes.item()),
                    )
                }
                ListItem(
                    headlineContent = {
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
                    modifier =
                        Modifier
                            .clickable {
                                state.updateSettings {
                                    copy(showMedia = !showMedia)
                                }
                            }.clip(ListCardShapes.item()),
                )
                AnimatedVisibility(appearanceSettings.showMedia) {
                    ListItem(
                        headlineContent = {
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
                        modifier =
                            Modifier
                                .clickable {
                                    state.updateSettings {
                                        copy(showSensitiveContent = !showSensitiveContent)
                                    }
                                }.clip(ListCardShapes.item()),
                    )
                }
                AnimatedVisibility(appearanceSettings.showMedia) {
                    ListItem(
                        headlineContent = {
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
                        modifier =
                            Modifier
                                .clickable {
                                    state.updateSettings {
                                        copy(expandMediaSize = !expandMediaSize)
                                    }
                                }.clip(ListCardShapes.item()),
                    )
                }
                AnimatedVisibility(appearanceSettings.showMedia) {
                    BoxWithConstraints {
                        var showMenu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier =
                                Modifier
                                    .clickable {
                                        if (maxWidth < 400.dp) {
                                            showMenu = true
                                        }
                                    }.clip(ListCardShapes.item()),
                            headlineContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_video_autoplay))
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_video_autoplay_description))
                            },
                            trailingContent = {
                                if (maxWidth >= 400.dp) {
                                    val items =
                                        persistentMapOf(
                                            VideoAutoplay.WIFI to stringResource(id = R.string.settings_appearance_video_autoplay_wifi),
                                            VideoAutoplay.ALWAYS to stringResource(id = R.string.settings_appearance_video_autoplay_always),
                                            VideoAutoplay.NEVER to stringResource(id = R.string.settings_appearance_video_autoplay_never),
                                        )
                                    ButtonGroup(
                                        overflowIndicator = {},
                                    ) {
                                        items.forEach { (autoplay, label) ->
                                            toggleableItem(
                                                checked = appearanceSettings.videoAutoplay == autoplay,
                                                onCheckedChange = {
                                                    state.updateSettings {
                                                        copy(videoAutoplay = autoplay)
                                                    }
                                                },
                                                label = label,
                                            )
                                        }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        showMenu = true
                                    }) {
                                        Text(text = stringResource(id = appearanceSettings.videoAutoplay.id))
                                    }
                                    FlareDropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                    ) {
                                        VideoAutoplay.entries.forEach {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(text = stringResource(id = it.id))
                                                },
                                                onClick = {
                                                    state.updateSettings {
                                                        copy(videoAutoplay = it)
                                                    }
                                                    showMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
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
