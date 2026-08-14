package dev.dimension.flare.feature.plugin.manifest

import dev.dimension.flare.feature.plugin.wire.WireTextV1
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTextArgument
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable(with = PluginTextV1Serializer::class)
public sealed interface PluginTextV1 {
    public data class Literal(
        val value: String,
    ) : PluginTextV1

    @Serializable
    public data class Localized(
        val key: String,
        val fallback: String,
    ) : PluginTextV1
}

public object PluginTextV1Serializer : KSerializer<PluginTextV1> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PluginTextV1") {
            element<String>("key", isOptional = true)
            element<String>("fallback", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): PluginTextV1 {
        require(decoder is JsonDecoder) { "PluginTextV1 supports JSON only" }
        return when (val element = decoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                require(element.isString) { "Plugin text literal must be a string" }
                PluginTextV1.Literal(element.content)
            }

            is JsonObject -> {
                decoder.json.decodeFromJsonElement<PluginTextV1.Localized>(element)
            }

            else -> {
                error("Plugin text must be a string or localized object")
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: PluginTextV1,
    ) {
        require(encoder is JsonEncoder) { "PluginTextV1 supports JSON only" }
        val element =
            when (value) {
                is PluginTextV1.Literal -> JsonPrimitive(value.value)
                is PluginTextV1.Localized -> encoder.json.encodeToJsonElement(value)
            }
        encoder.encodeJsonElement(element)
    }
}

public fun PluginTextV1.toUiText(
    pluginId: String,
    args: Map<String, UiTextArgument> = emptyMap(),
): UiText =
    when (this) {
        is PluginTextV1.Literal -> {
            UiText.Raw(value)
        }

        is PluginTextV1.Localized -> {
            UiText.ExternalRef(
                namespace = pluginId,
                key = key,
                fallback = fallback,
                args = args,
            )
        }
    }

public fun WireTextV1.toUiText(pluginId: String): UiText =
    value?.let { UiText.Raw(it) }
        ?: UiText.ExternalRef(
            namespace = pluginId,
            key = requireNotNull(key),
            fallback = requireNotNull(fallback),
            args = args,
        )

@Serializable
public data class PluginCatalogBundleV1(
    val pluginId: String,
    val defaultLocale: String,
    val catalogs: Map<String, Map<String, String>>,
) {
    public fun resolve(
        text: UiText.ExternalRef,
        locale: String,
    ): String {
        if (text.namespace != pluginId) return text.fallbackText()
        val normalizedCatalogs = catalogs.entries.associate { normalizeLocale(it.key) to it.value }
        val value =
            localeCandidates(locale)
                .plus(normalizeLocale(defaultLocale))
                .distinct()
                .firstNotNullOfOrNull { candidate -> normalizedCatalogs[candidate]?.get(text.key) }
                ?: text.fallback
        return value.interpolate(text.args)
    }
}

internal fun PluginTextV1.requireValid(path: String) {
    when (this) {
        is PluginTextV1.Literal -> {
            require(value.isNotBlank() && value.length <= 4_096) { "Invalid text at $path" }
        }

        is PluginTextV1.Localized -> {
            require(LOCALIZATION_KEY.matches(key)) { "Invalid localization key at $path" }
            require(fallback.isNotBlank() && fallback.length <= 4_096) { "Invalid localization fallback at $path" }
        }
    }
}

internal fun isValidLocaleTag(value: String): Boolean = LOCALE_TAG.matches(value)

private fun localeCandidates(locale: String): List<String> {
    val parts = normalizeLocale(locale).split('-').filter(String::isNotBlank)
    if (parts.isEmpty()) return emptyList()
    val language = parts.first()
    val script = parts.drop(1).firstOrNull { it.length == 4 && it.all(Char::isLetter) }
    return buildList {
        add(parts.joinToString("-"))
        if (script != null) add("$language-$script")
        add(language)
    }.distinct()
}

private fun normalizeLocale(value: String): String = value.replace('_', '-').lowercase()

private fun String.interpolate(args: Map<String, UiTextArgument>): String =
    ARGUMENT.replace(this) { match -> args[match.groupValues[1]]?.text ?: match.value }

private val LOCALE_TAG = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")
private val LOCALIZATION_KEY = Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")
private val ARGUMENT = Regex("\\{([A-Za-z][A-Za-z0-9_.-]{0,63})\\}")
