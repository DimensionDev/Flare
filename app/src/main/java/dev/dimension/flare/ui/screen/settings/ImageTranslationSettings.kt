package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.ImageTranslationField
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import dev.dimension.flare.ui.theme.segmentedShapes2
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun ImageTranslationSettingsSection() {
    val state by producePresenter("image_translation_settings") { remember { ImageTranslationSettingsPresenter() }() }
    ImageTranslationSettingsSection(state)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImageTranslationSettingsSection(state: ImageTranslationSettingsPresenter.State) {
    var showProviders by remember { mutableStateOf(false) }
    var editing by remember(state.config.provider) { mutableStateOf<ImageTranslationField?>(null) }
    var expandedChoice by remember(state.config.provider) { mutableStateOf<String?>(null) }
    val fieldOffset = if (state.config.provider == ImageTranslationProvider.OpenAIImages) 2 else 1
    val rowCount = state.fields.size + fieldOffset
    Text(
        imageTranslationText("Image translation"),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    SegmentedListItem(
        checked = showProviders,
        onCheckedChange = { showProviders = it },
        shapes = ListItemDefaults.segmentedShapes2(0, rowCount),
        content = { Text(imageTranslationText("Image translation provider")) },
        supportingContent = {
            Text(
                stringResource(R.string.settings_ai_config_translate_provider_description),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Box {
                TextButton(onClick = { showProviders = true }) { Text(imageTranslationText(state.config.provider.title)) }
                FlareDropdownMenu(expanded = showProviders, onDismissRequest = { showProviders = false }) {
                    state.providers.forEach { provider ->
                        DropdownMenuItem(text = { Text(imageTranslationText(provider.title)) }, onClick = {
                            state.selectProvider(provider)
                            showProviders = false
                        })
                    }
                }
            }
        },
    )
    if (state.config.provider == ImageTranslationProvider.OpenAIImages) {
        SegmentedListItem(
            onClick = { state.setUseSharedConfig(!state.useSharedConfig) },
            shapes = ListItemDefaults.segmentedShapes2(1, rowCount),
            content = { Text(stringResource(R.string.settings_ai_config_use_shared)) },
            supportingContent = { Text(stringResource(R.string.image_translation_use_shared_description)) },
            trailingContent = {
                Switch(checked = state.useSharedConfig, onCheckedChange = state::setUseSharedConfig)
            },
        )
    }
    state.fields.forEachIndexed { index, field ->
        val value = state.config.value(field.id)
        val options = field.choices.ifEmpty { field.suggestions }
        AnimatedVisibility(visible = !state.useSharedConfig || !field.isSharedAIField) {
            if (options.isNotEmpty() || field.loading) {
                SegmentedListItem(
                    checked = expandedChoice == field.id,
                    onCheckedChange = { expanded -> expandedChoice = if (expanded) field.id else null },
                    shapes = ListItemDefaults.segmentedShapes2(index + fieldOffset, rowCount),
                    content = { Text(imageTranslationText(field.title)) },
                    trailingContent = {
                        Box {
                            TextButton(onClick = { expandedChoice = field.id }) {
                                Text(
                                    if (value.isBlank()) {
                                        stringResource(
                                            R.string.settings_ai_config_value_empty_placeholder,
                                        )
                                    } else {
                                        imageTranslationText(field.choiceTitle(value))
                                    },
                                )
                            }
                            FlareDropdownMenu(expanded = expandedChoice == field.id, onDismissRequest = { expandedChoice = null }) {
                                if (field.loading) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.settings_ai_config_model_loading)) },
                                        onClick = {},
                                        enabled = false,
                                    )
                                }
                                options.forEach { choice ->
                                    DropdownMenuItem(text = { Text(imageTranslationText(field.choiceTitle(choice))) }, onClick = {
                                        state.setField(field.id, choice)
                                        expandedChoice = null
                                    })
                                }
                                if (field.choices.isEmpty()) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.image_translation_manual_input)) }, onClick = {
                                        expandedChoice = null
                                        editing = field
                                    })
                                }
                            }
                        }
                    },
                )
            } else {
                key(state.config.provider, field.id) {
                    AiConfigTextSetting(
                        title = imageTranslationText(field.title),
                        value = value,
                        shapes = ListItemDefaults.segmentedShapes2(index + fieldOffset, rowCount),
                        placeholder = field.placeholder,
                        suggestions = field.editSuggestions,
                        secret = field.secret,
                        singleLine = !field.multiline,
                        onConfirm = { state.setField(field.id, it.trim()) },
                    )
                }
            }
        }
    }
    Text(
        stringResource(R.string.image_translation_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
    if (state.config.provider == ImageTranslationProvider.Qwen) {
        Text(
            stringResource(R.string.image_translation_qwen_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
    editing?.let { field ->
        TextEditDialog(
            title = imageTranslationText(field.title),
            placeholder = field.placeholder,
            value = state.config.value(field.id),
            suggestions = field.editSuggestions.toImmutableList(),
            hint = "",
            secret = field.secret,
            singleLine = !field.multiline,
            onDismiss = { editing = null },
            onConfirm = { value ->
                state.setField(field.id, value.trim())
                editing = null
            },
        )
    }
}

@Composable
internal fun imageTranslationText(key: String): String {
    val resource =
        when (key) {
            "Automatic" -> R.string.image_translation_automatic
            "Original file" -> R.string.image_translation_original_file
            "Image translation" -> R.string.image_translation_title
            "Off" -> R.string.image_translation_off
            "Tencent Cloud" -> R.string.image_translation_tencent
            "OpenAI Images compatible" -> R.string.image_translation_openai
            "API key" -> R.string.image_translation_api_key
            "Secret ID" -> R.string.image_translation_secret_id
            "Secret key" -> R.string.image_translation_secret_key
            "Service URL" -> R.string.image_translation_service_url
            "Image model" -> R.string.image_translation_model
            "Region" -> R.string.image_translation_region
            "Mode" -> R.string.image_translation_mode
            "Translation prompt" -> R.string.image_translation_prompt
            "Image input format" -> R.string.image_translation_input_format
            "Image translation provider" -> R.string.image_translation_provider
            "Translate image" -> R.string.image_translation_action
            "Show original" -> R.string.image_translation_original
            "Show translation" -> R.string.image_translation_result
            "Translate again" -> R.string.image_translation_regenerate
            "Translating image" -> R.string.image_translation_loading
            "Configure image translation in Translation settings first." -> R.string.image_translation_not_configured
            "The provider does not support this image or input format." -> R.string.image_translation_unsupported
            "Check the provider credentials and service access." -> R.string.image_translation_authentication
            "The provider rate or quota limit was reached." -> R.string.image_translation_rate_limited
            "The provider rejected this image translation request." -> R.string.image_translation_rejected
            "The provider did not return a complete translated image." -> R.string.image_translation_no_image
            "The translated image could not be decoded." -> R.string.image_translation_invalid_image
            "Image translation failed. Check the connection and provider settings." -> R.string.image_translation_network
            "Image translation timed out. The provider may still be processing it." -> R.string.image_translation_timeout
            "Translation settings" -> R.string.image_translation_settings
            "Provider" -> R.string.image_translation_provider_label
            "Edit" -> R.string.image_translation_edit
            "Save" -> R.string.image_translation_save
            "Cancel" -> R.string.image_translation_cancel
            "Pro" -> R.string.image_translation_pro
            "Lite" -> R.string.image_translation_lite
            else -> null
        }
    return resource?.let { stringResource(it) } ?: key
}
