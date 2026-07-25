package dev.dimension.flare.flareui

import androidx.compose.runtime.Immutable

/**
 * A stable resource identity owned by a module that consumes Flare UI.
 *
 * Flare UI itself never defines resource keys or bundles.
 */
@Immutable
public data class FlareResourceKey(
    public val namespace: String,
    public val name: String,
) {
    init {
        require(namespace.isNotBlank()) { "A resource namespace cannot be blank." }
        require(name.matches(RESOURCE_NAME)) {
            "A resource name must match ${RESOURCE_NAME.pattern}: $name"
        }
    }

    public val qualifiedName: String
        get() = "$namespace.$name"

    public val platformNamespace: String
        get() = namespace.toPlatformIdentifier()

    public val platformName: String
        get() = "${platformNamespace}__$name"
}

@Immutable
public data class FlareStringResource(
    public val key: FlareResourceKey,
)

@Immutable
public data class FlareImageResource(
    public val key: FlareResourceKey,
)

/**
 * Text remains unresolved until a native renderer handles it.
 *
 * Literal values cover dynamic or user-provided content, while resource values
 * let the host platform choose the appropriate localization.
 */
@Immutable
public data class FlareText private constructor(
    public val literal: String?,
    public val resource: FlareStringResource?,
) {
    init {
        require((literal == null) != (resource == null)) {
            "FlareText must contain exactly one literal or resource."
        }
    }

    public companion object {
        public fun literal(value: String): FlareText =
            FlareText(
                literal = value,
                resource = null,
            )

        public fun resource(value: FlareStringResource): FlareText =
            FlareText(
                literal = null,
                resource = value,
            )
    }
}

private fun String.toPlatformIdentifier(): String =
    lowercase()
        .map { character ->
            if (character.isLetterOrDigit()) character else '_'
        }.joinToString("")

private val RESOURCE_NAME = Regex("[a-z][a-z0-9_]*")
