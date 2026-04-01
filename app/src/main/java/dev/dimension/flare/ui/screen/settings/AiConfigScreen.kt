package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AiConfigPresenter
import dev.dimension.flare.ui.presenter.settings.AiTypeOption
import dev.dimension.flare.ui.presenter.settings.TranslateProviderOption
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import moe.tlaster.precompose.molecule.producePresenter

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
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val serverTitle = stringResource(id = R.string.settings_ai_config_server)
            val serverHint = stringResource(id = R.string.settings_ai_config_server_hint)
            val serverRequirementHint = stringResource(id = R.string.settings_ai_config_server_url_requirement)
            val apiKeyTitle = stringResource(id = R.string.settings_ai_config_api_key)
            val apiKeyHint = stringResource(id = R.string.settings_ai_config_api_key_hint)
            val tldrPromptTitle = stringResource(id = R.string.settings_ai_config_tldr_prompt)
            SegmentedListItem(
                checked = state.showTypeDropdown,
                onCheckedChange = {
                    state.setShowTypeDropdown(it)
                },
                shapes =
                    if (state.aiType == AiTypeOption.OpenAI) {
                        ListItemDefaults.first()
                    } else {
                        ListItemDefaults.single()
                    },
                content = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_type),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_type_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    androidx.compose.foundation.layout.Box {
                        TextButton(
                            onClick = {
                                state.setShowTypeDropdown(true)
                            },
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id =
                                            when (state.aiType) {
                                                AiTypeOption.OnDevice -> R.string.settings_ai_config_type_on_device
                                                AiTypeOption.OpenAI -> R.string.settings_ai_config_type_openai
                                            },
                                    ),
                            )
                        }
                        FlareDropdownMenu(
                            expanded = state.showTypeDropdown,
                            onDismissRequest = {
                                state.setShowTypeDropdown(false)
                            },
                        ) {
                            state.supportedTypes
                                .forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text =
                                                    stringResource(
                                                        id =
                                                            when (type) {
                                                                AiTypeOption.OnDevice -> R.string.settings_ai_config_type_on_device
                                                                AiTypeOption.OpenAI -> R.string.settings_ai_config_type_openai
                                                            },
                                                    ),
                                            )
                                        },
                                        onClick = {
                                            state.setShowTypeDropdown(false)
                                            state.selectType(type)
                                        },
                                    )
                                }
                        }
                    }
                },
            )
            AnimatedVisibility(visible = state.aiType == AiTypeOption.OpenAI) {
                SegmentedListItem(
                    checked = state.textEditDialog?.field == AiConfigEditField.ServerUrl,
                    onCheckedChange = { checked ->
                        if (checked) {
                            state.setTextEditDialog(
                                TextEditDialogState(
                                    field = AiConfigEditField.ServerUrl,
                                    title = serverTitle,
                                    placeholder = serverHint,
                                    value = state.openAIServerUrl,
                                    suggestions = state.serverSuggestions,
                                    hint = serverRequirementHint,
                                    onConfirm = { newValue ->
                                        state.setOpenAIServerUrl(newValue)
                                    },
                                ),
                            )
                        } else if (state.textEditDialog?.field == AiConfigEditField.ServerUrl) {
                            state.setTextEditDialog(null)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = serverTitle)
                    },
                    supportingContent = {
                        Text(
                            text =
                                state.openAIServerUrl.ifBlank {
                                    stringResource(id = R.string.settings_ai_config_value_empty_placeholder)
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
            AnimatedVisibility(visible = state.aiType == AiTypeOption.OpenAI) {
                SegmentedListItem(
                    checked = state.textEditDialog?.field == AiConfigEditField.ApiKey,
                    onCheckedChange = { checked ->
                        if (checked) {
                            state.setTextEditDialog(
                                TextEditDialogState(
                                    field = AiConfigEditField.ApiKey,
                                    title = apiKeyTitle,
                                    placeholder = apiKeyHint,
                                    value = state.openAIApiKey,
                                    onConfirm = { newValue ->
                                        state.setOpenAIApiKey(newValue)
                                    },
                                ),
                            )
                        } else if (state.textEditDialog?.field == AiConfigEditField.ApiKey) {
                            state.setTextEditDialog(null)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = apiKeyTitle)
                    },
                    supportingContent = {
                        Text(
                            text =
                                state.openAIApiKey.ifBlank {
                                    stringResource(id = R.string.settings_ai_config_value_empty_placeholder)
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
            AnimatedVisibility(visible = state.aiType == AiTypeOption.OpenAI) {
                SegmentedListItem(
                    checked = state.showModelDropdown,
                    onCheckedChange = { checked ->
                        state.setShowModelDropdown(checked)
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_ai_config_model))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.settings_ai_config_model_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Box {
                            TextButton(
                                onClick = {
                                    state.setShowModelDropdown(true)
                                },
                            ) {
                                Text(
                                    text =
                                        state.openAIModel.ifBlank {
                                            stringResource(id = R.string.settings_ai_config_model_select)
                                        },
                                )
                            }
                            FlareDropdownMenu(
                                expanded = state.showModelDropdown,
                                onDismissRequest = {
                                    state.setShowModelDropdown(false)
                                },
                            ) {
                                state.openAIModels
                                    .onLoading {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(id = R.string.settings_ai_config_model_loading)) },
                                            onClick = {},
                                            enabled = false,
                                        )
                                    }.onError {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(id = R.string.settings_ai_config_model_error)) },
                                            onClick = {},
                                            enabled = false,
                                        )
                                    }.onSuccess { models ->
                                        if (models.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(id = R.string.settings_ai_config_model_no_models)) },
                                                onClick = {},
                                                enabled = false,
                                            )
                                        } else {
                                            models.forEach { model ->
                                                DropdownMenuItem(
                                                    text = { Text(model) },
                                                    onClick = {
                                                        state.setShowModelDropdown(false)
                                                        state.setOpenAIModel(model)
                                                    },
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedListItem(
                onClick = {
                    state.setAITldr(!state.aiTldr)
                },
                shapes =
                    if (state.aiTldr) {
                        ListItemDefaults.first()
                    } else {
                        ListItemDefaults.single()
                    },
                content = {
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
                        checked = state.aiTldr,
                        onCheckedChange = {
                            state.setAITldr(it)
                        },
                    )
                },
            )
            AnimatedVisibility(visible = state.aiTldr) {
                SegmentedListItem(
                    checked = state.textEditDialog?.field == AiConfigEditField.TldrPrompt,
                    onCheckedChange = { checked ->
                        if (checked) {
                            state.setTextEditDialog(
                                TextEditDialogState(
                                    field = AiConfigEditField.TldrPrompt,
                                    title = tldrPromptTitle,
                                    placeholder = "",
                                    value = state.tldrPrompt,
                                    onConfirm = { newValue ->
                                        state.setTldrPrompt(newValue)
                                    },
                                ),
                            )
                        } else if (state.textEditDialog?.field == AiConfigEditField.TldrPrompt) {
                            state.setTextEditDialog(null)
                        }
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = tldrPromptTitle)
                    },
                    supportingContent = {
                        Text(
                            text =
                                state.tldrPrompt.ifBlank {
                                    stringResource(id = R.string.settings_ai_config_value_empty_placeholder)
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }
    }
    state.textEditDialog?.let { dialog ->
        TextEditDialog(
            title = dialog.title,
            placeholder = dialog.placeholder,
            value = dialog.value,
            suggestions = dialog.suggestions,
            hint = dialog.hint,
            onDismiss = {
                state.setTextEditDialog(null)
            },
            onConfirm = { newValue ->
                dialog.onConfirm(newValue)
                state.setTextEditDialog(null)
            },
        )
    }
}

@Composable
private fun presenter() =
    run {
        val businessState = remember { AiConfigPresenter() }.invoke()
        var showTypeDropdown by remember { mutableStateOf(false) }
        var showModelDropdown by remember { mutableStateOf(false) }
        var textEditDialog by remember { mutableStateOf<TextEditDialogState?>(null) }

        object : AiConfigPresenter.State by businessState {
            val showTypeDropdown = showTypeDropdown
            val showModelDropdown = showModelDropdown
            val textEditDialog = textEditDialog

            fun setShowTypeDropdown(value: Boolean) {
                showTypeDropdown = value
            }

            fun setShowModelDropdown(value: Boolean) {
                showModelDropdown = value
            }

            fun setTextEditDialog(value: TextEditDialogState?) {
                textEditDialog = value
            }
        }
    }

private data class TextEditDialogState(
    val field: AiConfigEditField,
    val title: String,
    val placeholder: String,
    val value: String,
    val suggestions: ImmutableList<String> = persistentListOf(),
    val hint: String = "",
    val onConfirm: (String) -> Unit,
)

private enum class AiConfigEditField {
    ServerUrl,
    ApiKey,
    TldrPrompt,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditDialog(
    title: String,
    placeholder: String,
    value: String,
    suggestions: ImmutableList<String>,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var showSuggestions by remember(value) { mutableStateOf(false) }
    val filteredSuggestions =
        remember(text, suggestions) {
            suggestions.filter { item ->
                text.isBlank() || item.contains(text, ignoreCase = true)
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column {
                if (suggestions.isEmpty()) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            if (placeholder.isNotBlank()) {
                                Text(text = placeholder)
                            }
                        },
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                        onExpandedChange = { showSuggestions = it },
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                showSuggestions = true
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                            placeholder = {
                                if (placeholder.isNotBlank()) {
                                    Text(text = placeholder)
                                }
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                                )
                            },
                        )
                        ExposedDropdownMenu(
                            expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                            onDismissRequest = { showSuggestions = false },
                            modifier = Modifier.heightIn(max = 240.dp),
                        ) {
                            filteredSuggestions.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        text = item
                                        showSuggestions = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (hint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslationConfigScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { translationPresenter() }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_translation_title))
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
            val translatePromptTitle = stringResource(id = R.string.settings_ai_config_translate_prompt)
            SegmentedListItem(
                checked = state.showProviderDropdown,
                onCheckedChange = { checked ->
                    state.setShowProviderDropdown(checked)
                },
                shapes = ListItemDefaults.first(),
                content = {
                    Text(text = stringResource(id = R.string.settings_ai_config_translate_provider))
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_translate_provider_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Box {
                        TextButton(
                            onClick = {
                                state.setShowProviderDropdown(true)
                            },
                        ) {
                            Text(
                                text =
                                    when (state.translateProvider) {
                                        TranslateProviderOption.AI ->
                                            stringResource(
                                                id = R.string.settings_ai_config_translate_provider_ai,
                                            )
                                        TranslateProviderOption.Google ->
                                            stringResource(
                                                id = R.string.settings_ai_config_translate_provider_google,
                                            )
                                    },
                            )
                        }
                        FlareDropdownMenu(
                            expanded = state.showProviderDropdown,
                            onDismissRequest = {
                                state.setShowProviderDropdown(false)
                            },
                        ) {
                            state.supportedTranslateProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text =
                                                when (provider) {
                                                    TranslateProviderOption.AI ->
                                                        stringResource(
                                                            id = R.string.settings_ai_config_translate_provider_ai,
                                                        )
                                                    TranslateProviderOption.Google ->
                                                        stringResource(
                                                            id = R.string.settings_ai_config_translate_provider_google,
                                                        )
                                                },
                                        )
                                    },
                                    onClick = {
                                        state.setShowProviderDropdown(false)
                                        state.selectTranslateProvider(provider)
                                    },
                                )
                            }
                        }
                    }
                },
            )
            SegmentedListItem(
                onClick = {
                    state.setPreTranslate(!state.preTranslate)
                },
                shapes =
                    if (state.translateProvider == TranslateProviderOption.AI) {
                        ListItemDefaults.item()
                    } else {
                        ListItemDefaults.last()
                    },
                content = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_enable_pre_translation),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_pre_translation_description),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.preTranslate,
                        onCheckedChange = {
                            state.setPreTranslate(it)
                        },
                    )
                },
            )
            AnimatedVisibility(
                visible = state.translateProvider == TranslateProviderOption.AI,
            ) {
                SegmentedListItem(
                    checked = state.textEditDialog?.field == TranslationEditField.TranslatePrompt,
                    onCheckedChange = { checked ->
                        if (checked) {
                            state.setTextEditDialog(
                                TranslationTextEditDialogState(
                                    field = TranslationEditField.TranslatePrompt,
                                    title = translatePromptTitle,
                                    placeholder = "",
                                    value = state.translatePrompt,
                                    onConfirm = { newValue ->
                                        state.setTranslatePrompt(newValue)
                                    },
                                ),
                            )
                        } else if (state.textEditDialog?.field == TranslationEditField.TranslatePrompt) {
                            state.setTextEditDialog(null)
                        }
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = translatePromptTitle)
                    },
                    supportingContent = {
                        Text(
                            text =
                                state.translatePrompt.ifBlank {
                                    stringResource(id = R.string.settings_ai_config_value_empty_placeholder)
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }
    }
    state.textEditDialog?.let { dialog ->
        TextEditDialog(
            title = dialog.title,
            placeholder = dialog.placeholder,
            value = dialog.value,
            suggestions = dialog.suggestions,
            hint = dialog.hint,
            onDismiss = {
                state.setTextEditDialog(null)
            },
            onConfirm = { newValue ->
                dialog.onConfirm(newValue)
                state.setTextEditDialog(null)
            },
        )
    }
}

@Composable
private fun translationPresenter() =
    run {
        val businessState = remember { AiConfigPresenter() }.invoke()
        var showProviderDropdown by remember { mutableStateOf(false) }
        var textEditDialog by remember { mutableStateOf<TranslationTextEditDialogState?>(null) }

        object : AiConfigPresenter.State by businessState {
            val showProviderDropdown = showProviderDropdown
            val textEditDialog = textEditDialog

            fun setShowProviderDropdown(value: Boolean) {
                showProviderDropdown = value
            }

            fun setTextEditDialog(value: TranslationTextEditDialogState?) {
                textEditDialog = value
            }
        }
    }

private data class TranslationTextEditDialogState(
    val field: TranslationEditField,
    val title: String,
    val placeholder: String,
    val value: String,
    val suggestions: ImmutableList<String> = persistentListOf(),
    val hint: String = "",
    val onConfirm: (String) -> Unit,
)

private enum class TranslationEditField {
    TranslatePrompt,
}
