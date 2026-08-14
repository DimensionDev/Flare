package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.repository.RequireReLoginException
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.CapabilityManifestV1
import dev.dimension.flare.feature.plugin.manifest.CapabilityOperationManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginPlatformManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginTextV1
import dev.dimension.flare.feature.plugin.wire.AccountPluginSnapshotV1
import dev.dimension.flare.feature.plugin.wire.ComposeConfigV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginAccountV1Test {
    @Test
    fun newManifestOperationIsAvailableWithoutRequiringRelogin() {
        val plugin = plugin(setOf("detail", "context"))
        val account = credential(plugin, previous = setOf("detail"), negotiated = setOf("detail"))

        assertEquals(
            setOf("detail", "context"),
            account.effectiveCapabilities(plugin).getValue(PluginAbiV1.Capabilities.POST),
        )
    }

    @Test
    fun operationRejectedAtLoginStaysUnavailable() {
        val plugin = plugin(setOf("detail", "context"))
        val account = credential(plugin, previous = setOf("detail", "context"), negotiated = setOf("detail"))

        assertEquals(
            setOf("detail"),
            account.effectiveCapabilities(plugin).getValue(PluginAbiV1.Capabilities.POST),
        )
    }

    @Test
    fun credentialSchemaChangeRequiresRelogin() {
        val original = plugin(setOf("detail"))
        val account = credential(original, previous = setOf("detail"), negotiated = setOf("detail"))
        val changed = plugin(setOf("detail"), credentialSchemaVersion = 2)

        assertFailsWith<RequireReLoginException> { account.effectiveCapabilities(changed) }
    }

    @Test
    fun composeConstraintsUseStrictIntersection() {
        val merged =
            mergePluginComposeConfigV1(
                composeConfig(
                    minMedia = 1,
                    maxMedia = 4,
                    mimeTypes = setOf("IMAGE/*"),
                    visibility = setOf(VisibilityV1.Public, VisibilityV1.Unlisted),
                ),
                composeConfig(
                    minMedia = 2,
                    maxMedia = 3,
                    mimeTypes = setOf("image/jpeg", "video/mp4"),
                    visibility = setOf(VisibilityV1.Unlisted, VisibilityV1.Followers),
                ),
            )

        assertEquals(2, merged?.media?.minCountForNew)
        assertEquals(3, merged?.media?.maxCount)
        assertEquals(setOf("image/jpeg"), merged?.media?.supportedMimeTypes)
        assertEquals(setOf(VisibilityV1.Unlisted), merged?.visibility?.allowed)
        assertEquals(VisibilityV1.Unlisted, merged?.visibility?.default)
    }

    @Test
    fun composeConstraintConflictsAreRejectedBeforeDatasourceCreation() {
        assertFailsWith<IllegalArgumentException> {
            mergePluginComposeConfigV1(
                composeConfig(mimeTypes = setOf("image/jpeg")),
                composeConfig(mimeTypes = setOf("video/mp4")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            mergePluginComposeConfigV1(
                composeConfig(visibility = setOf(VisibilityV1.Public)),
                composeConfig(visibility = setOf(VisibilityV1.Followers)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            mergePluginComposeConfigV1(
                composeConfig(minMedia = 3, maxMedia = 4),
                composeConfig(minMedia = 0, maxMedia = 2),
            )
        }
    }

    private fun credential(
        plugin: RunningPluginV1,
        previous: Set<String>,
        negotiated: Set<String>,
    ): PluginAccountCredentialV1 =
        PluginAccountCredentialV1(
            snapshot =
                AccountPluginSnapshotV1(
                    pluginId = plugin.installed.pluginId,
                    platformId = plugin.installed.manifest.platform.id,
                    packageHash = plugin.installed.packageHash,
                    origin = "https://example.social",
                    accountId = "me",
                    manifestCapabilities = mapOf(PluginAbiV1.Capabilities.POST to previous),
                    negotiatedCapabilities = mapOf(PluginAbiV1.Capabilities.POST to negotiated),
                    credentialSchemaVersion = 1,
                    timelineSchemaVersion = 1,
                ),
            credential = JsonObject(emptyMap()),
        )

    private fun plugin(
        operations: Set<String>,
        credentialSchemaVersion: Int = 1,
    ): RunningPluginV1 {
        val manifest =
            PluginManifestV1(
                apiVersion = 1,
                id = "dev.dimension.flare.test.adapter",
                version = "1.0.0",
                name = PluginTextV1.Literal("Adapter"),
                platform =
                    PluginPlatformManifestV1(
                        id = "Adapter",
                        name = PluginTextV1.Literal("Adapter"),
                        credentialSchemaVersion = credentialSchemaVersion,
                        capabilities =
                            mapOf(
                                PluginAbiV1.Capabilities.POST to
                                    CapabilityManifestV1(
                                        operations.associateWith { CapabilityOperationManifestV1() },
                                    ),
                            ),
                    ),
            )
        return RunningPluginV1(
            installed =
                InstalledPluginV1(
                    pluginId = manifest.id,
                    version = manifest.version,
                    packageHash = "a".repeat(64),
                    packageSize = 1,
                    iconSize = 1,
                    manifest = manifest,
                ),
            packagePath = "/package.fpp",
            iconPath = "/icon.png",
        )
    }

    private fun composeConfig(
        minMedia: Int = 0,
        maxMedia: Int = 4,
        mimeTypes: Set<String> = emptySet(),
        visibility: Set<VisibilityV1> = setOf(VisibilityV1.Public),
    ): ComposeConfigV1 =
        ComposeConfigV1(
            media =
                ComposeConfigV1.MediaConfigV1(
                    minCountForNew = minMedia,
                    maxCount = maxMedia,
                    maxBytes = 10_000_000,
                    supportedMimeTypes = mimeTypes,
                ),
            visibility = ComposeConfigV1.VisibilityConfigV1(visibility, visibility.first()),
        )
}
