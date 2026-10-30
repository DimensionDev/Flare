package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1.Capabilities
import dev.dimension.flare.feature.plugin.manifest.CapabilityManifestV1
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1

internal class PluginSessionCapabilitiesV1(
    private val declarations: Map<String, CapabilityManifestV1>,
    private val operations: Map<String, Set<String>>,
    private val authenticated: Boolean,
) {
    fun hasOperation(
        capability: String,
        operation: String,
    ): Boolean = operation in operations[capability].orEmpty()

    fun requireOperation(
        capability: String,
        operation: String,
    ) {
        require(hasOperation(capability, operation)) { "Plugin operation is unavailable: $capability/$operation" }
    }

    fun canExpose(capability: String): Boolean =
        (authenticated || capability !in ACCOUNT_CAPABILITIES) &&
            PluginAbiV1.hasRequiredOperations(capability, operations[capability].orEmpty())

    fun directions(
        capability: String,
        operation: String,
    ): Set<PageDirectionV1> =
        declarations[capability]
            ?.operations
            ?.get(operation)
            ?.directions
            .orEmpty()

    private companion object {
        val ACCOUNT_CAPABILITIES =
            setOf(
                Capabilities.COMPOSE,
                Capabilities.RELATION,
                Capabilities.NOTIFICATION,
                Capabilities.LIST,
                Capabilities.DIRECT_MESSAGE,
                Capabilities.TAB_CATALOG,
            )
    }
}
