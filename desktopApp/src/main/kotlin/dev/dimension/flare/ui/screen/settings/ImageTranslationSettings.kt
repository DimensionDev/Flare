package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.data.datastore.model.ImageTranslationField
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.image_translation_action
import dev.dimension.flare.image_translation_api_key
import dev.dimension.flare.image_translation_authentication
import dev.dimension.flare.image_translation_automatic
import dev.dimension.flare.image_translation_cancel
import dev.dimension.flare.image_translation_description
import dev.dimension.flare.image_translation_edit
import dev.dimension.flare.image_translation_input_format
import dev.dimension.flare.image_translation_invalid_image
import dev.dimension.flare.image_translation_lite
import dev.dimension.flare.image_translation_loading
import dev.dimension.flare.image_translation_mode
import dev.dimension.flare.image_translation_model
import dev.dimension.flare.image_translation_network
import dev.dimension.flare.image_translation_no_image
import dev.dimension.flare.image_translation_not_configured
import dev.dimension.flare.image_translation_off
import dev.dimension.flare.image_translation_openai
import dev.dimension.flare.image_translation_original
import dev.dimension.flare.image_translation_original_file
import dev.dimension.flare.image_translation_pro
import dev.dimension.flare.image_translation_prompt
import dev.dimension.flare.image_translation_provider
import dev.dimension.flare.image_translation_provider_label
import dev.dimension.flare.image_translation_qwen_note
import dev.dimension.flare.image_translation_rate_limited
import dev.dimension.flare.image_translation_regenerate
import dev.dimension.flare.image_translation_region
import dev.dimension.flare.image_translation_rejected
import dev.dimension.flare.image_translation_result
import dev.dimension.flare.image_translation_save
import dev.dimension.flare.image_translation_secret_id
import dev.dimension.flare.image_translation_secret_key
import dev.dimension.flare.image_translation_service_url
import dev.dimension.flare.image_translation_settings
import dev.dimension.flare.image_translation_tencent
import dev.dimension.flare.image_translation_timeout
import dev.dimension.flare.image_translation_title
import dev.dimension.flare.image_translation_unsupported
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ImageTranslationSettingsContent() {
    val state = remember { ImageTranslationSettingsPresenter() }.invoke()
    var showProviders by remember { mutableStateOf(false) }
    var editing by remember(state.config.provider) { mutableStateOf<ImageTranslationField?>(null) }
    ExpanderItem(
        heading = { Text(imageTranslationText("Image translation")) },
        caption = {
            Text(
                imageTranslationText(
                    "Original images, including private images, are sent to the selected service. Choose a model that returns images.",
                ),
            )
        },
        trailing = {
            DropDownButton(onClick = { showProviders = !showProviders }) { Text(imageTranslationText(state.config.provider.title)) }
            MenuFlyout(visible = showProviders, onDismissRequest = { showProviders = false }) {
                state.providers.forEach { provider ->
                    MenuFlyoutItem(text = { Text(imageTranslationText(provider.title)) }, onClick = {
                        state.selectProvider(provider)
                        showProviders = false
                    })
                }
            }
        },
    )
    state.fields.forEach { field ->
        ExpanderItemSeparator()
        ExpanderItem(
            heading = { Text(imageTranslationText(field.title)) },
            caption = {
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
            trailing = { Button(onClick = { editing = field }) { Text(imageTranslationText("Edit")) } },
        )
    }
    if (state.config.provider == ImageTranslationProvider.Qwen) {
        Text(
            imageTranslationText(
                "Qwen requires a publicly accessible original URL. Private images cannot be translated with this provider.",
            ),
            style = FluentTheme.typography.caption,
            modifier = Modifier.padding(16.dp),
        )
    }
    editing?.let { field ->
        var text by remember(field.id) { mutableStateOf(state.config.value(field.id)) }
        ContentDialog(
            title = imageTranslationText(field.title),
            visible = true,
            content = {
                Column(Modifier.padding(16.dp)) {
                    if (field.choices.isNotEmpty()) {
                        field.choices.forEach { choice ->
                            RadioButton(
                                selected = text == choice,
                                onClick = { text = choice },
                                label = imageTranslationText(field.choiceTitle(choice)),
                            )
                        }
                    } else {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = !field.multiline,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            placeholder = { Text(field.placeholder) },
                            visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions =
                                KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text,
                                ),
                        )
                    }
                }
            },
            primaryButtonText = imageTranslationText("Save"),
            closeButtonText = imageTranslationText("Cancel"),
            onButtonClick = { button ->
                if (button == ContentDialogButton.Primary) state.setField(field.id, text.trim())
                editing = null
            },
        )
    }
}

@Composable
internal fun imageTranslationText(key: String): String {
    val resource =
        when (key) {
            "Automatic" -> Res.string.image_translation_automatic
            "Original file" -> Res.string.image_translation_original_file
            "Image translation" -> Res.string.image_translation_title
            "Off" -> Res.string.image_translation_off
            "Tencent Cloud" -> Res.string.image_translation_tencent
            "OpenAI Images compatible" -> Res.string.image_translation_openai
            "API key" -> Res.string.image_translation_api_key
            "Secret ID" -> Res.string.image_translation_secret_id
            "Secret key" -> Res.string.image_translation_secret_key
            "Service URL" -> Res.string.image_translation_service_url
            "Image model" -> Res.string.image_translation_model
            "Region" -> Res.string.image_translation_region
            "Mode" -> Res.string.image_translation_mode
            "Translation prompt" -> Res.string.image_translation_prompt
            "Image input format" -> Res.string.image_translation_input_format
            "Image translation provider" -> Res.string.image_translation_provider
            "Translate image" -> Res.string.image_translation_action
            "Show original" -> Res.string.image_translation_original
            "Show translation" -> Res.string.image_translation_result
            "Translate again" -> Res.string.image_translation_regenerate
            "Translating image" -> Res.string.image_translation_loading
            "Configure image translation in Translation settings first." -> Res.string.image_translation_not_configured
            "The provider does not support this image or input format." -> Res.string.image_translation_unsupported
            "Check the provider credentials and service access." -> Res.string.image_translation_authentication
            "The provider rate or quota limit was reached." -> Res.string.image_translation_rate_limited
            "The provider rejected this image translation request." -> Res.string.image_translation_rejected
            "The provider did not return a complete translated image." -> Res.string.image_translation_no_image
            "The translated image could not be decoded." -> Res.string.image_translation_invalid_image
            "Image translation failed. Check the connection and provider settings." -> Res.string.image_translation_network
            "Image translation timed out. The provider may still be processing it." -> Res.string.image_translation_timeout
            "Translation settings" -> Res.string.image_translation_settings
            "Provider" -> Res.string.image_translation_provider_label
            "Edit" -> Res.string.image_translation_edit
            "Save" -> Res.string.image_translation_save
            "Cancel" -> Res.string.image_translation_cancel
            "Pro" -> Res.string.image_translation_pro
            "Lite" -> Res.string.image_translation_lite
            else -> null
        }
    return resource?.let { stringResource(it) } ?: key
}
