package dev.dimension.flare.ui.screen.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ColorPickerDialogRouteDestination
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.EmptyStatusEvent
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AppearancePresenter
import dev.dimension.flare.ui.presenter.settings.AppearanceState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalSharedTransitionApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun AppearanceRoute(navigator: ProxyDestinationsNavigator) {
    AnimatedVisibility(visible = true) {
        SharedTransitionScope {
            AppearanceScreen(
                onBack = navigator::navigateUp,
                toColorPicker = {
                    navigator.navigate(ColorPickerDialogRouteDestination)
                },
            )
        }
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun AppearanceScreen(
    onBack: () -> Unit,
    toColorPicker: () -> Unit,
) {
    val state by producePresenter { appearancePresenter() }
    val appearanceSettings = LocalAppearanceSettings.current
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(it),
        ) {
            state.sampleStatus.onSuccess {
                StatusItem(it, EmptyStatusEvent)
            }
            HorizontalDivider()
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState()),
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.settings_appearance_generic),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )
                BoxWithConstraints {
                    var showMenu by remember { mutableStateOf(false) }
                    ListItem(
                        modifier =
                            Modifier.clickable {
                                if (maxWidth < 400.dp) {
                                    showMenu = true
                                }
                            },
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_theme))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_theme_description))
                        },
                        trailingContent = {
                            if (maxWidth >= 400.dp) {
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        selected = appearanceSettings.theme == Theme.LIGHT,
                                        onClick = {
                                            state.updateSettings {
                                                copy(theme = Theme.LIGHT)
                                            }
                                        },
                                        shape =
                                            SegmentedButtonDefaults.itemShape(
                                                index = 0,
                                                count = 3,
                                            ),
                                    ) {
                                        Text(text = stringResource(id = R.string.settings_appearance_theme_light))
                                    }
                                    SegmentedButton(
                                        selected = appearanceSettings.theme == Theme.SYSTEM,
                                        onClick = {
                                            state.updateSettings {
                                                copy(theme = Theme.SYSTEM)
                                            }
                                        },
                                        shape =
                                            SegmentedButtonDefaults.itemShape(
                                                index = 1,
                                                count = 3,
                                            ),
                                    ) {
                                        Text(text = stringResource(id = R.string.settings_appearance_theme_auto))
                                    }
                                    SegmentedButton(
                                        selected = appearanceSettings.theme == Theme.DARK,
                                        onClick = {
                                            state.updateSettings {
                                                copy(theme = Theme.DARK)
                                            }
                                        },
                                        shape =
                                            SegmentedButtonDefaults.itemShape(
                                                index = 2,
                                                count = 3,
                                            ),
                                    ) {
                                        Text(text = stringResource(id = R.string.settings_appearance_theme_dark))
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
                                DropdownMenu(
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
                        Modifier.clickable {
                            state.updateSettings {
                                copy(pureColorMode = !pureColorMode)
                            }
                        },
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
                            Modifier.clickable {
                                state.updateSettings {
                                    copy(dynamicTheme = !dynamicTheme)
                                }
                            },
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
                            Modifier.clickable {
                                toColorPicker.invoke()
                            },
                    )
                }
                BoxWithConstraints {
                    var showMenu by remember { mutableStateOf(false) }
                    ListItem(
                        modifier =
                            Modifier.clickable {
                                if (maxWidth < 400.dp) {
                                    showMenu = true
                                }
                            },
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_description))
                        },
                        trailingContent = {
                            if (maxWidth >= 400.dp) {
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        selected = appearanceSettings.avatarShape == AvatarShape.CIRCLE,
                                        onClick = {
                                            state.updateSettings {
                                                copy(avatarShape = AvatarShape.CIRCLE)
                                            }
                                        },
                                        shape =
                                            SegmentedButtonDefaults.itemShape(
                                                index = 0,
                                                count = 2,
                                            ),
                                    ) {
                                        Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_round))
                                    }
                                    SegmentedButton(
                                        selected = appearanceSettings.avatarShape == AvatarShape.SQUARE,
                                        onClick = {
                                            state.updateSettings {
                                                copy(avatarShape = AvatarShape.SQUARE)
                                            }
                                        },
                                        shape =
                                            SegmentedButtonDefaults.itemShape(
                                                index = 1,
                                                count = 2,
                                            ),
                                    ) {
                                        Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_square))
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
                                DropdownMenu(
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
                        Modifier.clickable {
                            state.updateSettings {
                                copy(showActions = !showActions)
                            }
                        },
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
                            Modifier.clickable {
                                state.updateSettings {
                                    copy(showNumbers = !showNumbers)
                                }
                            },
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
                        Modifier.clickable {
                            state.updateSettings {
                                copy(showLinkPreview = !showLinkPreview)
                            }
                        },
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
                            Modifier.clickable {
                                state.updateSettings {
                                    copy(compatLinkPreview = !compatLinkPreview)
                                }
                            },
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
                        Modifier.clickable {
                            state.updateSettings {
                                copy(showMedia = !showMedia)
                            }
                        },
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
                            Modifier.clickable {
                                state.updateSettings {
                                    copy(showSensitiveContent = !showSensitiveContent)
                                }
                            },
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
                            Modifier.clickable {
                                state.updateSettings {
                                    copy(expandMediaSize = !expandMediaSize)
                                }
                            },
                    )
                }
                AnimatedVisibility(appearanceSettings.showMedia) {
                    BoxWithConstraints {
                        var showMenu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    if (maxWidth < 400.dp) {
                                        showMenu = true
                                    }
                                },
                            headlineContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_video_autoplay))
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_video_autoplay_description))
                            },
                            trailingContent = {
                                if (maxWidth >= 400.dp) {
                                    SingleChoiceSegmentedButtonRow {
                                        VideoAutoplay.entries.forEachIndexed { index, it ->
                                            SegmentedButton(
                                                selected = appearanceSettings.videoAutoplay == it,
                                                onClick = {
                                                    state.updateSettings {
                                                        copy(videoAutoplay = it)
                                                    }
                                                },
                                                shape =
                                                    SegmentedButtonDefaults.itemShape(
                                                        index = index,
                                                        count = VideoAutoplay.entries.size,
                                                    ),
                                            ) {
                                                Text(text = stringResource(id = it.id))
                                            }
                                        }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        showMenu = true
                                    }) {
                                        Text(text = stringResource(id = appearanceSettings.videoAutoplay.id))
                                    }
                                    DropdownMenu(
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
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_enable_swipe))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_enable_swipe_description))
                    },
                    trailingContent = {
                        Switch(
                            checked = appearanceSettings.swipeGestures,
                            onCheckedChange = {
                                state.updateSettings {
                                    copy(swipeGestures = it)
                                }
                            },
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            state.updateSettings {
                                copy(swipeGestures = !swipeGestures)
                            }
                        },
                )

                state.user.onSuccess { user ->
                    AnimatedVisibility(appearanceSettings.swipeGestures) {
                        var showMenu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    showMenu = true
                                },
                            headlineContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_swipe_left))
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_swipe_left_description))
                            },
                            trailingContent = {
                                val icon =
                                    when (user) {
                                        is UiUser.Bluesky ->
                                            appearanceSettings.bluesky.swipeLeft.icon

                                        is UiUser.Mastodon ->
                                            appearanceSettings.mastodon.swipeLeft.icon

                                        is UiUser.Misskey ->
                                            appearanceSettings.misskey.swipeLeft.icon

                                        is UiUser.XQT ->
                                            appearanceSettings.xqt.swipeLeft.icon

                                        is UiUser.VVO ->
                                            appearanceSettings.vvo.swipeLeft.icon
                                    }
                                val textId =
                                    when (user) {
                                        is UiUser.Bluesky ->
                                            appearanceSettings.bluesky.swipeLeft.id

                                        is UiUser.Mastodon ->
                                            appearanceSettings.mastodon.swipeLeft.id

                                        is UiUser.Misskey ->
                                            appearanceSettings.misskey.swipeLeft.id

                                        is UiUser.XQT ->
                                            appearanceSettings.xqt.swipeLeft.id

                                        is UiUser.VVO ->
                                            appearanceSettings.vvo.swipeLeft.id
                                    }
                                TextButton(onClick = {
                                    showMenu = true
                                }) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = stringResource(id = textId),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(id = textId))
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    when (user) {
                                        is UiUser.Bluesky ->
                                            AppearanceSettings.Bluesky.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                bluesky =
                                                                    bluesky.copy(
                                                                        swipeLeft = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.bluesky.swipeLeft == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.Mastodon ->
                                            AppearanceSettings.Mastodon.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                mastodon =
                                                                    mastodon.copy(
                                                                        swipeLeft = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.mastodon.swipeLeft == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.Misskey ->
                                            AppearanceSettings.Misskey.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                misskey =
                                                                    misskey.copy(
                                                                        swipeLeft = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.misskey.swipeLeft == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.XQT ->
                                            AppearanceSettings.XQT.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                xqt =
                                                                    xqt.copy(
                                                                        swipeLeft = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.xqt.swipeLeft == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.VVO ->
                                            AppearanceSettings.VVO.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                vvo =
                                                                    vvo.copy(
                                                                        swipeLeft = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.vvo.swipeLeft == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }
                                    }
                                }
                            },
                        )
                    }

                    AnimatedVisibility(appearanceSettings.swipeGestures) {
                        var showMenu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    showMenu = true
                                },
                            headlineContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_swipe_right))
                            },
                            supportingContent = {
                                Text(text = stringResource(id = R.string.settings_appearance_swipe_right_description))
                            },
                            trailingContent = {
                                val icon =
                                    when (user) {
                                        is UiUser.Bluesky ->
                                            appearanceSettings.bluesky.swipeRight.icon

                                        is UiUser.Mastodon ->
                                            appearanceSettings.mastodon.swipeRight.icon

                                        is UiUser.Misskey ->
                                            appearanceSettings.misskey.swipeRight.icon

                                        is UiUser.XQT ->
                                            appearanceSettings.xqt.swipeRight.icon

                                        is UiUser.VVO ->
                                            appearanceSettings.vvo.swipeRight.icon
                                    }
                                val textId =
                                    when (user) {
                                        is UiUser.Bluesky ->
                                            appearanceSettings.bluesky.swipeRight.id

                                        is UiUser.Mastodon ->
                                            appearanceSettings.mastodon.swipeRight.id

                                        is UiUser.Misskey ->
                                            appearanceSettings.misskey.swipeRight.id

                                        is UiUser.XQT ->
                                            appearanceSettings.xqt.swipeRight.id

                                        is UiUser.VVO ->
                                            appearanceSettings.vvo.swipeRight.id
                                    }
                                TextButton(onClick = {
                                    showMenu = true
                                }) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = stringResource(id = textId),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(id = textId))
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    when (user) {
                                        is UiUser.Bluesky ->
                                            AppearanceSettings.Bluesky.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                bluesky =
                                                                    bluesky.copy(
                                                                        swipeRight = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.bluesky.swipeRight == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.Mastodon ->
                                            AppearanceSettings.Mastodon.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                mastodon =
                                                                    mastodon.copy(
                                                                        swipeRight = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.mastodon.swipeRight == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.Misskey ->
                                            AppearanceSettings.Misskey.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                misskey =
                                                                    misskey.copy(
                                                                        swipeRight = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.misskey.swipeRight == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.XQT ->
                                            AppearanceSettings.XQT.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                xqt =
                                                                    xqt.copy(
                                                                        swipeRight = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.xqt.swipeRight == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }

                                        is UiUser.VVO ->
                                            AppearanceSettings.VVO.SwipeActions.entries.forEach {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = it.icon,
                                                            contentDescription = stringResource(id = it.id),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = it.id))
                                                    },
                                                    onClick = {
                                                        state.updateSettings {
                                                            copy(
                                                                vvo =
                                                                    vvo.copy(
                                                                        swipeRight = it,
                                                                    ),
                                                            )
                                                        }
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        RadioButton(
                                                            selected = appearanceSettings.vvo.swipeRight == it,
                                                            onClick = null,
                                                        )
                                                    },
                                                )
                                            }
                                    }
                                }
                            },
                        )
                    }

                    ListItem(
                        headlineContent = {
                            when (user) {
                                is UiUser.Bluesky -> Unit
                                is UiUser.XQT -> Unit

                                is UiUser.Mastodon ->
                                    Text(
                                        text = "Mastodon",
                                        style = MaterialTheme.typography.titleMedium,
                                    )

                                is UiUser.Misskey ->
                                    Text(
                                        text = "Misskey",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                is UiUser.VVO -> Unit
                            }
                        },
                    )

                    when (user) {
                        is UiUser.Bluesky -> Unit
                        is UiUser.XQT -> Unit
                        is UiUser.Mastodon ->
                            MastodonAppearance(
                                appearanceSettings,
                                updateSettings = {
                                    state.updateSettings {
                                        it
                                    }
                                },
                            )

                        is UiUser.Misskey ->
                            MisskeyAppearance(
                                appearanceSettings,
                                updateSettings = {
                                    state.updateSettings {
                                        it
                                    }
                                },
                            )
                        is UiUser.VVO -> Unit
                    }
                }
            }
        }
    }
}

// @Composable
// private fun ColumnScope.BlueskyAppearance(
//    state: AppearanceState,
//    appearanceSettings: AppearanceSettings,
// ) {
// }

@Composable
private fun ColumnScope.MastodonAppearance(
    appearanceSettings: AppearanceSettings,
    updateSettings: (AppearanceSettings) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.settings_appearance_mastodon_show_visibility))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.settings_appearance_mastodon_show_visibility_description))
        },
        trailingContent = {
            Switch(
                checked = appearanceSettings.mastodon.showVisibility,
                onCheckedChange = {
                    updateSettings(
                        appearanceSettings.copy(
                            mastodon =
                                appearanceSettings.mastodon.copy(
                                    showVisibility = it,
                                ),
                        ),
                    )
                },
            )
        },
        modifier =
            Modifier.clickable {
                updateSettings(
                    appearanceSettings.copy(
                        mastodon =
                            appearanceSettings.mastodon.copy(
                                showVisibility = !appearanceSettings.mastodon.showVisibility,
                            ),
                    ),
                )
            },
    )
}

@Composable
private fun ColumnScope.MisskeyAppearance(
    appearanceSettings: AppearanceSettings,
    updateSettings: (AppearanceSettings) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.settings_appearance_misskey_show_visibility))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.settings_appearance_misskey_show_visibility_description))
        },
        trailingContent = {
            Switch(
                checked = appearanceSettings.misskey.showVisibility,
                onCheckedChange = {
                    updateSettings(
                        appearanceSettings.copy(
                            misskey =
                                appearanceSettings.misskey.copy(
                                    showVisibility = it,
                                ),
                        ),
                    )
                },
            )
        },
        modifier =
            Modifier.clickable {
                updateSettings(
                    appearanceSettings.copy(
                        misskey =
                            appearanceSettings.misskey.copy(
                                showVisibility = !appearanceSettings.misskey.showVisibility,
                            ),
                    ),
                )
            },
    )
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.settings_appearance_misskey_show_reaction))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.settings_appearance_misskey_show_reaction_description))
        },
        trailingContent = {
            Switch(
                checked = appearanceSettings.misskey.showReaction,
                onCheckedChange = {
                    updateSettings(
                        appearanceSettings.copy(
                            misskey =
                                appearanceSettings.misskey.copy(
                                    showReaction = it,
                                ),
                        ),
                    )
                },
            )
        },
        modifier =
            Modifier.clickable {
                updateSettings(
                    appearanceSettings.copy(
                        misskey =
                            appearanceSettings.misskey.copy(
                                showReaction = !appearanceSettings.misskey.showReaction,
                            ),
                    ),
                )
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
