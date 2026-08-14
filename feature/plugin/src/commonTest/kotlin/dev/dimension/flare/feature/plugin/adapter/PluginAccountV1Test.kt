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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    fun hostCredentialAccessReadsTheLatestPersistedEnvelope() =
        runTest {
            val plugin = plugin(setOf("detail"))
            val initial =
                credential(plugin, previous = setOf("detail"), negotiated = setOf("detail"))
                    .copy(credential = JsonObject(mapOf("token" to JsonPrimitive("old"))))
            val persisted = MutableStateFlow(initial)
            val context =
                object : PlatformDataSourceContext {
                    override val accountKey: MicroBlogKey = MicroBlogKey("me", "example.social")

                    @Suppress("UNCHECKED_CAST")
                    override fun <T : Any> credential(serializer: KSerializer<T>): T = initial as T

                    @Suppress("UNCHECKED_CAST")
                    override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = persisted as Flow<T>

                    @Suppress("UNCHECKED_CAST")
                    override suspend fun <T : Any> updateCredential(
                        serializer: KSerializer<T>,
                        credential: T,
                    ) {
                        persisted.value = credential as PluginAccountCredentialV1
                    }
                }
            val access = PlatformDataSourceCredentialAccessV1(plugin, context)
            val refreshed = JsonObject(mapOf("token" to JsonPrimitive("new")))

            persisted.value = initial.copy(credential = refreshed)

            assertEquals(initial.credential, access.initial().credential)
            assertEquals(refreshed, access.read())

            val rotated = JsonObject(mapOf("token" to JsonPrimitive("rotated")))
            access.replace(rotated)
            assertEquals(rotated, persisted.value.credential)
        }

    @Test
    fun mismatchedPersistedAccountRequiresReloginBeforeRuntimeCreation() {
        val plugin = plugin(setOf("detail"))
        val initial = credential(plugin, previous = setOf("detail"), negotiated = setOf("detail"))
        val context =
            object : PlatformDataSourceContext {
                override val accountKey: MicroBlogKey = MicroBlogKey("another-account", "example.social")

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> credential(serializer: KSerializer<T>): T = initial as T

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = MutableStateFlow(initial) as Flow<T>

                override suspend fun <T : Any> updateCredential(
                    serializer: KSerializer<T>,
                    credential: T,
                ) = Unit
            }

        assertFailsWith<RequireReLoginException> {
            PlatformDataSourceCredentialAccessV1(plugin, context).initial()
        }
    }

    @Test
    fun packageChangeInvalidatesContentAndAdvancesThePersistedSnapshotOnce() =
        runTest {
            val plugin = plugin(setOf("detail"))
            val previous =
                credential(plugin, previous = setOf("detail"), negotiated = setOf("detail"))
                    .let { it.copy(snapshot = it.snapshot.copy(packageHash = "b".repeat(64))) }
            val persisted = MutableStateFlow(previous)
            var invalidations = 0
            val context =
                object : PlatformDataSourceContext {
                    override val accountKey: MicroBlogKey = MicroBlogKey("me", "example.social")

                    @Suppress("UNCHECKED_CAST")
                    override fun <T : Any> credential(serializer: KSerializer<T>): T = previous as T

                    @Suppress("UNCHECKED_CAST")
                    override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = persisted as Flow<T>

                    @Suppress("UNCHECKED_CAST")
                    override suspend fun <T : Any> updateCredential(
                        serializer: KSerializer<T>,
                        credential: T,
                    ) {
                        persisted.value = credential as PluginAccountCredentialV1
                    }

                    override suspend fun invalidateCachedContent() {
                        invalidations += 1
                    }
                }
            val access = PlatformDataSourceCredentialAccessV1(plugin, context)

            access.read()
            access.read()

            assertEquals(1, invalidations)
            assertEquals(plugin.installed.packageHash, persisted.value.snapshot.packageHash)
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
