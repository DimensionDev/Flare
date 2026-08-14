package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.RequireReLoginException
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.host.PluginCredentialAccess
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.login.accountHost
import dev.dimension.flare.feature.plugin.manifest.PluginTextV1
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.wire.AccountPluginSnapshotV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.feature.plugin.wire.WireLimitsV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath

public fun RunningPluginV1.accountCredential(success: LoginSuccessV1): PluginAccountCredentialV1 {
    success.requireValid()
    mergePluginComposeConfigV1(installed.manifest.platform.composeDefaults, success.composeConfig)
    val manifestCapabilities =
        installed.manifest.platform.capabilities
            .mapValues { it.value.operations.keys }
    return PluginAccountCredentialV1(
        snapshot =
            AccountPluginSnapshotV1(
                pluginId = installed.pluginId,
                platformId = installed.manifest.platform.id,
                packageHash = installed.packageHash,
                origin = PluginUrlPolicy.requireOrigin(success.origin),
                accountId = success.accountId,
                manifestCapabilities = manifestCapabilities,
                negotiatedCapabilities = success.capabilities,
                composeConfig = success.composeConfig,
                credentialSchemaVersion = installed.manifest.platform.credentialSchemaVersion,
                timelineSchemaVersion = installed.manifest.platform.timelineSchemaVersion,
            ),
        credential = success.credential,
    ).also { it.requireValid(this) }
}

internal fun RunningPluginV1.platformMetadata(): PlatformMetadata {
    val platform = installed.manifest.platform
    return PlatformMetadata(
        displayName = platform.name.fallbackText,
        icon = UiIcon.World,
        displayNameText = platform.name.toUiText(installed.pluginId),
        iconUrl = "file://$iconPath",
    )
}

internal suspend fun AccountService.addPluginAccount(
    plugin: RunningPluginV1,
    success: LoginSuccessV1,
): MicroBlogKey {
    val metadata = plugin.platformMetadata()
    val accountKey =
        MicroBlogKey(
            success.accountId,
            Url(success.origin).accountHost(),
        )
    val accountMetadata = metadata.copy(iconUrl = plugin.stableAccountIconUrl() ?: metadata.iconUrl)
    addAccount(
        account =
            UiAccount(
                accountKey = accountKey,
                platformId = plugin.installed.manifest.platform.id,
                platformDisplayName = accountMetadata.displayName,
                platformIcon = accountMetadata.icon,
                platformDisplayNameText = accountMetadata.displayNameText,
                platformIconUrl = accountMetadata.iconUrl,
            ),
        credential = plugin.accountCredential(success),
        serializer = PluginAccountCredentialV1.serializer(),
    ).join()
    return accountKey
}

private fun RunningPluginV1.stableAccountIconUrl(): String? =
    runCatching {
        val source = iconPath.toPath()
        val directory = requireNotNull(source.parent)
        val target =
            directory / "platform-${installed.pluginId.encodeUtf8().sha256().hex()}-${installed.packageHash}.png"
        if (source != target && !FileSystem.SYSTEM.exists(target)) {
            val bytes = FileSystem.SYSTEM.read(source) { readByteArray() }
            val temp = directory / "${target.name}.next"
            FileSystem.SYSTEM.write(temp) { write(bytes) }
            try {
                FileSystem.SYSTEM.atomicMove(temp, target)
            } catch (error: Throwable) {
                FileSystem.SYSTEM.delete(temp, mustExist = false)
                throw error
            }
        }
        "file://$target"
    }.getOrNull()

private val PluginTextV1.fallbackText: String
    get() =
        when (this) {
            is PluginTextV1.Literal -> value
            is PluginTextV1.Localized -> fallback
        }

internal fun PluginAccountCredentialV1.requireValid(plugin: RunningPluginV1) {
    val manifest = plugin.installed.manifest
    require(snapshot.pluginId == plugin.installed.pluginId && snapshot.platformId == manifest.platform.id) {
        "Plugin account identity mismatch"
    }
    require(PACKAGE_HASH.matches(snapshot.packageHash)) { "Invalid plugin account package hash" }
    require(snapshot.accountId.isNotBlank() && snapshot.accountId.length <= WireLimitsV1.MAX_ID_LENGTH) {
        "Invalid plugin account ID"
    }
    require(PluginUrlPolicy.requireOrigin(snapshot.origin) == snapshot.origin) { "Invalid plugin account origin" }
    require(snapshot.credentialSchemaVersion > 0 && snapshot.timelineSchemaVersion > 0) { "Invalid plugin account schema" }
    require(snapshot.manifestCapabilities.isCapabilitySnapshot() && snapshot.negotiatedCapabilities.isCapabilitySnapshot()) {
        "Invalid plugin account capabilities"
    }
    snapshot.composeConfig?.requireValid()
    mergePluginComposeConfigV1(manifest.platform.composeDefaults, snapshot.composeConfig)
    require(
        dev.dimension.flare.feature.plugin.abi.PluginJsonV1
            .encodeToString(JsonElement.serializer(), credential)
            .encodeToByteArray()
            .size <= WireLimitsV1.MAX_CREDENTIAL_BYTES,
    ) { "Plugin credential is too large" }
}

internal fun PluginAccountCredentialV1.effectiveCapabilities(plugin: RunningPluginV1): Map<String, Set<String>> {
    requireValid(plugin)
    if (snapshot.credentialSchemaVersion != plugin.installed.manifest.platform.credentialSchemaVersion) {
        throw RequireReLoginException(
            accountKey =
                dev.dimension.flare.model
                    .MicroBlogKey(
                        snapshot.accountId,
                        io.ktor.http
                            .Url(snapshot.origin)
                            .accountHost(),
                    ),
            platformId = snapshot.platformId,
        )
    }
    return plugin.installed.manifest.platform.capabilities
        .mapValues { (capability, declaration) ->
            val previousManifest = snapshot.manifestCapabilities[capability].orEmpty()
            val negotiated = snapshot.negotiatedCapabilities[capability].orEmpty()
            declaration.operations.keys.filterTo(linkedSetOf()) { operation ->
                operation !in previousManifest || operation in negotiated
            }
        }.filterValues(Set<String>::isNotEmpty)
}

internal class PlatformDataSourceCredentialAccessV1(
    private val plugin: RunningPluginV1,
    private val context: PlatformDataSourceContext,
) : PluginCredentialAccess {
    private val packageRefreshMutex = Mutex()

    override suspend fun read(): JsonElement = current().credential

    override suspend fun replace(value: JsonElement) {
        val current = current()
        val replacement = current.copy(credential = value).also { it.requireValid(plugin) }
        context.updateCredential(PluginAccountCredentialV1.serializer(), replacement)
    }

    fun initial(): PluginAccountCredentialV1 =
        context
            .credential(PluginAccountCredentialV1.serializer())
            .also(::requireCurrentAccount)

    suspend fun current(): PluginAccountCredentialV1 =
        persisted().let { current ->
            if (current.snapshot.packageHash == plugin.installed.packageHash) {
                current
            } else {
                packageRefreshMutex.withLock {
                    val latest = persisted()
                    if (latest.snapshot.packageHash == plugin.installed.packageHash) {
                        latest
                    } else {
                        context.invalidateCachedContent()
                        latest
                            .copy(snapshot = latest.snapshot.copy(packageHash = plugin.installed.packageHash))
                            .also { replacement ->
                                replacement.requireValid(plugin)
                                context.updateCredential(PluginAccountCredentialV1.serializer(), replacement)
                            }
                    }
                }
            }
        }

    private suspend fun persisted(): PluginAccountCredentialV1 =
        context
            .credentialFlow(PluginAccountCredentialV1.serializer())
            .first()
            .also(::requireCurrentAccount)

    private fun requireCurrentAccount(account: PluginAccountCredentialV1) {
        account.requireValid(plugin)
        val expectedKey = MicroBlogKey(account.snapshot.accountId, Url(account.snapshot.origin).accountHost())
        if (expectedKey != context.accountKey) {
            throw RequireReLoginException(context.accountKey, plugin.installed.manifest.platform.id)
        }
    }
}

internal fun RunningPluginV1.accountInvocationContext(
    account: PluginAccountCredentialV1,
    locale: String,
    credentialAccess: PluginCredentialAccess,
    assets: Map<String, dev.dimension.flare.feature.plugin.host.PluginAsset> = emptyMap(),
): PluginInvocationContextV1 =
    PluginInvocationContextV1.account(
        pluginId = installed.pluginId,
        platformId = installed.manifest.platform.id,
        packageHash = installed.packageHash,
        origin = account.snapshot.origin,
        accountId = account.snapshot.accountId,
        locale = locale,
        credential = credentialAccess,
        assets = assets,
    )

private fun Map<String, Set<String>>.isCapabilitySnapshot(): Boolean =
    size <= 32 &&
        all { (capability, operations) ->
            capability in PluginAbiV1.knownCapabilityOperations &&
                operations.isNotEmpty() &&
                operations.size <= 32 &&
                PluginAbiV1.knownCapabilityOperations.getValue(capability).containsAll(operations)
        }

private val PACKAGE_HASH = Regex("[0-9a-f]{64}")
