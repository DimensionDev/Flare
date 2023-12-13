package dev.dimension.flare.ui.screen.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.rememberKoinInject

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun AppearanceRoute(navigator: ProxyDestinationsNavigator) {
    AppearanceScreen(
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceScreen(onBack: () -> Unit) {
    val state by producePresenter { appearancePresenter() }
    val appearanceSettings = LocalAppearanceSettings.current
    Scaffold(
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
                    .padding(it)
                    .verticalScroll(rememberScrollState()),
        ) {
            state.sampleStatus.onSuccess {
                StatusItem(it, StatusEvent.empty)
            }
            HorizontalDivider()
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
                                }
                            val textId =
                                when (user) {
                                    is UiUser.Bluesky ->
                                        appearanceSettings.bluesky.swipeLeft.id

                                    is UiUser.Mastodon ->
                                        appearanceSettings.mastodon.swipeLeft.id

                                    is UiUser.Misskey ->
                                        appearanceSettings.misskey.swipeLeft.id
                                }
                            TextButton(onClick = {
                                showMenu = true
                            }) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(id = textId),
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
                                                    Checkbox(
                                                        checked = appearanceSettings.bluesky.swipeLeft == it,
                                                        onCheckedChange = null,
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
                                                    Checkbox(
                                                        checked = appearanceSettings.mastodon.swipeLeft == it,
                                                        onCheckedChange = null,
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
                                                    Checkbox(
                                                        checked = appearanceSettings.misskey.swipeLeft == it,
                                                        onCheckedChange = null,
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
                                }
                            val textId =
                                when (user) {
                                    is UiUser.Bluesky ->
                                        appearanceSettings.bluesky.swipeRight.id

                                    is UiUser.Mastodon ->
                                        appearanceSettings.mastodon.swipeRight.id

                                    is UiUser.Misskey ->
                                        appearanceSettings.misskey.swipeRight.id
                                }
                            TextButton(onClick = {
                                showMenu = true
                            }) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(id = textId),
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
                                                    Checkbox(
                                                        checked = appearanceSettings.bluesky.swipeRight == it,
                                                        onCheckedChange = null,
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
                                                    Checkbox(
                                                        checked = appearanceSettings.mastodon.swipeRight == it,
                                                        onCheckedChange = null,
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
                                                    Checkbox(
                                                        checked = appearanceSettings.misskey.swipeRight == it,
                                                        onCheckedChange = null,
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
                        }
                    },
                )

                when (user) {
                    is UiUser.Bluesky -> Unit
                    is UiUser.Mastodon -> MastodonAppearance(state, appearanceSettings)
                    is UiUser.Misskey -> MisskeyAppearance(state, appearanceSettings)
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
    state: AppearanceState,
    appearanceSettings: AppearanceSettings,
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
                    state.updateSettings {
                        copy(
                            mastodon =
                                mastodon.copy(
                                    showVisibility = it,
                                ),
                        )
                    }
                },
            )
        },
        modifier =
            Modifier.clickable {
                state.updateSettings {
                    copy(
                        mastodon =
                            mastodon.copy(
                                showVisibility = !mastodon.showVisibility,
                            ),
                    )
                }
            },
    )
}

@Composable
private fun ColumnScope.MisskeyAppearance(
    state: AppearanceState,
    appearanceSettings: AppearanceSettings,
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
                    state.updateSettings {
                        copy(
                            misskey =
                                misskey.copy(
                                    showVisibility = it,
                                ),
                        )
                    }
                },
            )
        },
        modifier =
            Modifier.clickable {
                state.updateSettings {
                    copy(
                        misskey =
                            misskey.copy(
                                showVisibility = !misskey.showVisibility,
                            ),
                    )
                }
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
                    state.updateSettings {
                        copy(
                            misskey =
                                misskey.copy(
                                    showReaction = it,
                                ),
                        )
                    }
                },
            )
        },
        modifier =
            Modifier.clickable {
                state.updateSettings {
                    copy(
                        misskey =
                            misskey.copy(
                                showReaction = !misskey.showReaction,
                            ),
                    )
                }
            },
    )
}

@Composable
private fun appearancePresenter() =
    run {
        val scope = rememberCoroutineScope()
        val settingsRepository = rememberKoinInject<SettingsRepository>()
        val activeAccountState = remember { ActiveAccountPresenter() }.invoke()
        val sampleStatus =
            activeAccountState.user.map {
                when (it) {
                    is UiUser.Bluesky -> createBlueskyStatus(it)
                    is UiUser.Mastodon -> createMastodonStatus(it)
                    is UiUser.Misskey -> createMisskeyStatus(it)
                }
            }

        object : AppearanceState, ActiveAccountState by activeAccountState {
            override val sampleStatus = sampleStatus

            override fun updateSettings(block: AppearanceSettings.() -> AppearanceSettings) {
                scope.launch {
                    settingsRepository.updateAppearanceSettings(block)
                }
            }
        }
    }

interface AppearanceState : ActiveAccountState {
    val sampleStatus: UiState<UiStatus>

    fun updateSettings(block: AppearanceSettings.() -> AppearanceSettings)
}

fun createMastodonStatus(user: UiUser.Mastodon): UiStatus.Mastodon {
    return UiStatus.Mastodon(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        content = "Sample content for Mastodon status",
        contentWarningText = null,
        matrices =
            UiStatus.Mastodon.Matrices(
                replyCount = 10,
                reblogCount = 5,
                favouriteCount = 15,
            ),
        media = persistentListOf(),
        createdAt = Clock.System.now(),
        visibility = UiStatus.Mastodon.Visibility.Public,
        poll = null,
        card = null,
        reaction =
            UiStatus.Mastodon.Reaction(
                liked = false,
                reblogged = false,
                bookmarked = false,
            ),
        sensitive = false,
        reblogStatus = null,
        raw = Status(),
    )
}

fun createBlueskyStatus(user: UiUser.Bluesky): UiStatus.Bluesky {
    return UiStatus.Bluesky(
        accountKey = MicroBlogKey(id = "123", host = user.userKey.host),
        statusKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        indexedAt = Clock.System.now(),
        repostBy = null,
        quote = null,
        content = "Bluesky post content",
        medias = persistentListOf(),
        card = null,
        matrices =
            UiStatus.Bluesky.Matrices(
                replyCount = 20,
                likeCount = 30,
                repostCount = 40,
            ),
        reaction =
            UiStatus.Bluesky.Reaction(
                repostUri = null,
                likedUri = null,
            ),
        cid = "cid_sample",
        uri = "https://bluesky.post/uri",
    )
}

fun createMisskeyStatus(user: UiUser.Misskey): UiStatus.Misskey {
    return UiStatus.Misskey(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        content = "Misskey post content",
        contentWarningText = null,
        matrices =
            UiStatus.Misskey.Matrices(
                replyCount = 15,
                renoteCount = 25,
            ),
        media = persistentListOf(),
        createdAt = Clock.System.now(),
        visibility = UiStatus.Misskey.Visibility.Public,
        poll = null,
        card = null,
        reaction =
            UiStatus.Misskey.Reaction(
                emojiReactions = persistentListOf(),
                myReaction = null,
            ),
        sensitive = false,
        quote = null,
        renote = null,
    )
}
