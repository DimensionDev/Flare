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
import dev.dimension.flare.data.model.LocalAppearanceSettings
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
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceDisplayScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    val appearanceSettings = LocalAppearanceSettings.current
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_display_group_title))
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
                    state.updateSettings {
                        copy(absoluteTimestamp = !absoluteTimestamp)
                    }
                },
                shapes =
                    if (state.sampleStatus.isSuccess) {
                        ListItemDefaults.item()
                    } else {
                        ListItemDefaults.first()
                    },
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
            AnimatedVisibility(visible = appearanceSettings.showLinkPreview) {
                SegmentedListItem(
                    onClick = {
                        state.updateSettings {
                            copy(compatLinkPreview = !compatLinkPreview)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
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
                )
            }
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
    }
}
