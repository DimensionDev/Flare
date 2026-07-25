package dev.dimension.flare.flareui.codegen

internal data class ComponentMetadata(
    val type: String,
    val propsType: String,
    val properties: List<PropertyMetadata>,
)

internal data class PropertyMetadata(
    val name: String,
    val type: String,
    val eventParameters: List<EventParameterMetadata>? = null,
)

internal data class EventParameterMetadata(
    val name: String,
    val type: String,
)

internal val ComponentMetadata.className: String
    get() = type.substringAfterLast('.').removeSuffix("Type")

internal val ComponentMetadata.id: String
    get() = className.replaceFirstChar(Char::lowercaseChar)

internal val ComponentMetadata.kindName: String
    get() =
        className
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .uppercase()

internal val ComponentMetadata.payloadName: String
    get() = "Flare${className}Payload"

internal val ComponentMetadata.swiftPayloadName: String
    get() = "FlareUI${className}Payload"
