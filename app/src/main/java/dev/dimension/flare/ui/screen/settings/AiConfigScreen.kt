package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleXmark
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.FlareServerProviderPresenter
import dev.dimension.flare.ui.theme.listCardContainer
import dev.dimension.flare.ui.theme.listCardItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiConfigScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { presenter() }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_ai_config_title))
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
                    .padding(horizontal = screenHorizontalPadding)
                    .listCardContainer(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ListItem(
                modifier =
                    Modifier
                        .listCardItem()
                        .clickable {
                            state.setShowServerDialog(true)
                        },
                overlineContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_server),
                    )
                },
                headlineContent = {
                    state.currentServer.onSuccess {
                        Text(
                            text = it,
                        )
                    }
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_ai_config_server_self_host_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_entable_translation),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_translation_description),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.aiConfig.translation,
                        onCheckedChange = {
                            state.update {
                                copy(translation = it)
                            }
                        },
                    )
                },
                modifier =
                    Modifier
                        .listCardItem()
                        .clickable {
                            state.update {
                                copy(translation = !state.aiConfig.translation)
                            }
                        },
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_enable_tldr),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_tldr_description),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.aiConfig.tldr,
                        onCheckedChange = {
                            state.update {
                                copy(tldr = it)
                            }
                        },
                    )
                },
                modifier =
                    Modifier
                        .listCardItem()
                        .clickable {
                            state.update {
                                copy(tldr = !state.aiConfig.tldr)
                            }
                        },
            )
        }
    }
    if (state.showServerDialog) {
        AlertDialog(
            onDismissRequest = {
                state.setShowServerDialog(false)
            },
            confirmButton = {
                TextButton(
                    enabled = state.serverValidation.isSuccess,
                    onClick = {
                        state.confirm()
                        state.setShowServerDialog(false)
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.setShowServerDialog(false)
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
            title = {
                Text(
                    text = stringResource(id = R.string.settings_ai_config_server),
                )
            },
            text = {
                OutlinedTextField(
                    state = state.serverText,
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.settings_ai_config_server_hint),
                        )
                    },
                    trailingIcon = {
                        state.serverValidation
                            .onSuccess {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.CircleCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }.onError {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.CircleXmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }.onLoading {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                    },
                    label = {
                        Text(
                            text = stringResource(id = R.string.settings_ai_config_server),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun presenter(settingsRepository: SettingsRepository = koinInject<SettingsRepository>()) =
    run {
        var showServerDialog by remember { mutableStateOf(false) }
        val serverText = rememberTextFieldState()
        val scope = rememberCoroutineScope()
        val state = remember { FlareServerProviderPresenter() }.invoke()
        val aiConfig by remember { settingsRepository.appSettings.map { it.aiConfig } }
            .collectAsState(AppSettings.AiConfig())
        state.currentServer.onSuccess { currentServer ->
            LaunchedEffect(currentServer) {
                serverText.edit {
                    delete(0, length)
                    append(currentServer)
                }
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { serverText.text }
                .distinctUntilChanged()
                .debounce(666L)
                .collectLatest {
                    state.checkServer(it.toString())
                }
        }

        object : FlareServerProviderPresenter.State by state {
            val showServerDialog = showServerDialog
            val serverText = serverText
            val aiConfig = aiConfig

            fun update(block: AppSettings.AiConfig.() -> AppSettings.AiConfig) {
                scope.launch {
                    settingsRepository.updateAppSettings { copy(aiConfig = block.invoke(this.aiConfig)) }
                }
            }

            fun setShowServerDialog(value: Boolean) {
                showServerDialog = value
            }
        }
    }
