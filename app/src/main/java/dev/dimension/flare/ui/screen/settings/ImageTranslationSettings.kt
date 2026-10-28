package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.ImageTranslationField
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import dev.dimension.flare.ui.theme.single
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImageTranslationSettingsSection() {
    val state by producePresenter("image_translation_settings") { remember { ImageTranslationSettingsPresenter() }() }
    var showProviders by remember { mutableStateOf(false) }
    var editing by remember(state.config.provider) { mutableStateOf<ImageTranslationField?>(null) }
    Text(
        imageTranslationText("Image translation"),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    SegmentedListItem(
        checked = showProviders,
        onCheckedChange = { showProviders = it },
        shapes = ListItemDefaults.single(),
        content = { Text(imageTranslationText("Image translation provider")) },
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
    state.fields.forEach { field ->
        SegmentedListItem(
            checked = editing?.id == field.id,
            onCheckedChange = { if (it) editing = field },
            shapes = ListItemDefaults.single(),
            content = { Text(imageTranslationText(field.title)) },
            supportingContent = {
                val value = state.config.value(field.id)
                Text(
                    if (field.secret && value.isNotEmpty()) {
                        "••••••••"
                    } else if (field.choices.isNotEmpty()) {
                        imageTranslationText(field.choiceTitle(value))
                    } else {
                        value.ifEmpty { field.placeholder }
                    },
                    maxLines = 2,
                )
            },
        )
    }
    Text(
        imageTranslationText(
            "Original images, including private images, are sent to the selected service. Choose a model that returns images.",
        ),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(16.dp),
    )
    if (state.config.provider == ImageTranslationProvider.Qwen) {
        Text(
            imageTranslationText(
                "Qwen requires a publicly accessible original URL. Private images cannot be translated with this provider.",
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    editing?.let { field ->
        var text by remember(field.id) { mutableStateOf(state.config.value(field.id)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(imageTranslationText(field.title)) },
            text = {
                if (field.choices.isNotEmpty()) {
                    Column {
                        field.choices.forEach { choice ->
                            TextButton(onClick = {
                                state.setField(field.id, choice)
                                editing = null
                            }) {
                                Text(
                                    imageTranslationText(field.choiceTitle(choice)),
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = !field.multiline,
                        minLines = if (field.multiline) 5 else 1,
                        maxLines = if (field.multiline) 8 else 1,
                        placeholder = { Text(field.placeholder) },
                        visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions =
                            KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text,
                            ),
                    )
                }
            },
            confirmButton = {
                if (field.choices.isEmpty()) {
                    TextButton(onClick = {
                        state.setField(field.id, text.trim())
                        editing = null
                    }) { Text(imageTranslationText("Save")) }
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(imageTranslationText("Cancel")) } },
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
