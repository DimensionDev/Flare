package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
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
import dev.dimension.flare.image_translation_manual_input
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
import dev.dimension.flare.image_translation_use_shared_description
import dev.dimension.flare.settings_ai_config_model_loading
import dev.dimension.flare.settings_ai_config_translate_provider_description
import dev.dimension.flare.settings_ai_config_use_shared
import dev.dimension.flare.settings_ai_config_value_empty_placeholder
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ImageTranslationSettingsContent() {
    val state = remember { ImageTranslationSettingsPresenter() }.invoke()
    ImageTranslationSettingsContent(state)
}

@Composable
internal fun ImageTranslationSettingsContent(state: ImageTranslationSettingsPresenter.State) {
    var showProviders by remember { mutableStateOf(false) }
    var expandedChoice by remember(state.config.provider) { mutableStateOf<String?>(null) }
    var editing by remember(state.config.provider) { mutableStateOf<TextEditDialogState?>(null) }
    ExpanderItem(
        heading = { Text(imageTranslationText("Image translation provider")) },
        caption = { Text(stringResource(Res.string.settings_ai_config_translate_provider_description)) },
        trailing = {
            DropDownButton(onClick = { showProviders = !showProviders }) { Text(imageTranslationText(state.config.provider.title)) }
            MenuFlyout(
                visible = showProviders,
                onDismissRequest = { showProviders = false },
                placement = FlyoutPlacement.BottomAlignedEnd,
                modifier = Modifier.heightIn(max = 200.dp),
            ) {
                state.providers.forEach { provider ->
                    MenuFlyoutItem(text = { Text(imageTranslationText(provider.title)) }, onClick = {
                        state.selectProvider(provider)
                        showProviders = false
                    })
                }
            }
        },
    )
    if (state.config.provider == ImageTranslationProvider.OpenAIImages) {
        val sharedTitle = stringResource(Res.string.settings_ai_config_use_shared)
        ExpanderItemSeparator()
        ExpanderItem(
            heading = { Text(sharedTitle) },
            caption = { Text(stringResource(Res.string.image_translation_use_shared_description)) },
            trailing = {
                Box(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = sharedTitle
                        role = Role.Switch
                        toggleableState = if (state.useSharedConfig) ToggleableState.On else ToggleableState.Off
                    },
                ) {
                    Switcher(state.useSharedConfig, state::setUseSharedConfig)
                }
            },
        )
    }
    state.fields.forEach { field ->
        val value = state.config.value(field.id)
        val title = imageTranslationText(field.title)
        val options = field.choices.ifEmpty { field.suggestions }
        AnimatedVisibility(visible = !state.useSharedConfig || !field.isSharedAIField) {
            Column {
                ExpanderItemSeparator()
                if (options.isNotEmpty() || field.loading) {
                    ExpanderItem(
                        heading = { Text(title) },
                        trailing = {
                            DropDownButton(onClick = { expandedChoice = field.id }) {
                                Text(
                                    if (value.isBlank()) {
                                        stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                    } else {
                                        imageTranslationText(field.choiceTitle(value))
                                    },
                                )
                            }
                            MenuFlyout(
                                visible = expandedChoice == field.id,
                                onDismissRequest = { expandedChoice = null },
                                placement = FlyoutPlacement.BottomAlignedEnd,
                                modifier = Modifier.heightIn(max = 200.dp),
                            ) {
                                if (field.loading) {
                                    MenuFlyoutItem(
                                        text = { Text(stringResource(Res.string.settings_ai_config_model_loading)) },
                                        onClick = {},
                                        enabled = false,
                                    )
                                }
                                options.forEach { choice ->
                                    MenuFlyoutItem(text = { Text(imageTranslationText(field.choiceTitle(choice))) }, onClick = {
                                        state.setField(field.id, choice)
                                        expandedChoice = null
                                    })
                                }
                                if (field.choices.isEmpty()) {
                                    MenuFlyoutItem(text = { Text(stringResource(Res.string.image_translation_manual_input)) }, onClick = {
                                        expandedChoice = null
                                        editing =
                                            TextEditDialogState(
                                                title = title,
                                                placeholder = field.placeholder,
                                                value = value,
                                                singleLine = !field.multiline,
                                                onConfirm = { state.setField(field.id, it.trim()) },
                                            )
                                    })
                                }
                            }
                        },
                    )
                } else {
                    key(state.config.provider, field.id) {
                        AiConfigTextSetting(
                            title = title,
                            value = value,
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
    }
    Text(
        stringResource(Res.string.image_translation_description),
        style = FluentTheme.typography.caption,
        color = FluentTheme.colors.text.text.secondary,
        modifier = Modifier.padding(16.dp),
    )
    if (state.config.provider == ImageTranslationProvider.Qwen) {
        Text(
            stringResource(Res.string.image_translation_qwen_note),
            style = FluentTheme.typography.caption,
            modifier = Modifier.padding(16.dp),
        )
    }
    editing?.let { dialog -> SettingsTextEditDialog(dialog, onDismiss = { editing = null }) }
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
