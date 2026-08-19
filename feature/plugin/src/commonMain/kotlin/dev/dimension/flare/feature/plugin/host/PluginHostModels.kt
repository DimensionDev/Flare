package dev.dimension.flare.feature.plugin.host

import dev.dimension.flare.feature.plugin.manifest.isValidLocaleTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okio.Source
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PluginCredentialAccess {
    public suspend fun read(): JsonElement

    /** The implementation must persist the replacement atomically. */
    public suspend fun replace(value: JsonElement)
}

@HiddenFromObjC
public interface PluginAsset {
    public val size: Long
    public val fileName: String?
    public val mimeType: String?

    /** Returns a new source for one upload attempt. */
    public fun openSource(): Source
}

@Serializable
public enum class PluginInvocationScopeV1 {
    @SerialName("detector")
    Detector,

    @SerialName("login")
    Login,

    @SerialName("account")
    Account,

    @SerialName("guest")
    Guest,
}

@Serializable
public data class PluginInvocationMetadataV1(
    val pluginId: String,
    val platformId: String,
    val packageHash: String,
    val scope: PluginInvocationScopeV1,
    val origin: String,
    val accountId: String? = null,
    val locale: String,
    val approvedOrigins: Set<String>,
    val credentialAvailable: Boolean,
    val assetHandles: Set<String>,
)

public class PluginInvocationContextV1 private constructor(
    public val metadata: PluginInvocationMetadataV1,
    internal val credential: PluginCredentialAccess?,
    internal val assets: Map<String, PluginAsset>,
) {
    public companion object {
        public fun detector(
            pluginId: String,
            platformId: String,
            packageHash: String,
            candidateOrigin: String,
            locale: String,
        ): PluginInvocationContextV1 =
            create(
                pluginId = pluginId,
                platformId = platformId,
                packageHash = packageHash,
                scope = PluginInvocationScopeV1.Detector,
                origin = candidateOrigin,
                locale = locale,
            )

        public fun login(
            pluginId: String,
            platformId: String,
            packageHash: String,
            candidateOrigin: String,
            authOrigins: Set<String>,
            locale: String,
        ): PluginInvocationContextV1 =
            create(
                pluginId = pluginId,
                platformId = platformId,
                packageHash = packageHash,
                scope = PluginInvocationScopeV1.Login,
                origin = candidateOrigin,
                additionalOrigins = authOrigins,
                locale = locale,
            )

        public fun account(
            pluginId: String,
            platformId: String,
            packageHash: String,
            origin: String,
            accountId: String,
            locale: String,
            credential: PluginCredentialAccess,
            assets: Map<String, PluginAsset> = emptyMap(),
        ): PluginInvocationContextV1 =
            create(
                pluginId = pluginId,
                platformId = platformId,
                packageHash = packageHash,
                scope = PluginInvocationScopeV1.Account,
                origin = origin,
                accountId = accountId,
                locale = locale,
                credential = credential,
                assets = assets,
            )

        public fun guest(
            pluginId: String,
            platformId: String,
            packageHash: String,
            origin: String,
            locale: String,
        ): PluginInvocationContextV1 =
            create(
                pluginId = pluginId,
                platformId = platformId,
                packageHash = packageHash,
                scope = PluginInvocationScopeV1.Guest,
                origin = origin,
                locale = locale,
            )

        private fun create(
            pluginId: String,
            platformId: String,
            packageHash: String,
            scope: PluginInvocationScopeV1,
            origin: String,
            locale: String,
            additionalOrigins: Set<String> = emptySet(),
            accountId: String? = null,
            credential: PluginCredentialAccess? = null,
            assets: Map<String, PluginAsset> = emptyMap(),
        ): PluginInvocationContextV1 {
            require(ID.matches(pluginId) && ID.matches(platformId)) { "Invalid invocation identity" }
            require(HASH.matches(packageHash)) { "Invalid invocation package hash" }
            require(isValidLocaleTag(locale)) { "Invalid invocation locale" }
            require(accountId == null || (accountId.isNotBlank() && accountId.length <= 512)) { "Invalid account ID" }
            require(assets.size <= MAX_ASSET_COUNT && assets.keys.all(ASSET_HANDLE::matches)) { "Invalid asset handles" }
            assets.values.forEach { asset ->
                require(asset.size in 0..MAX_ASSET_BYTES) { "Invalid asset size" }
                require(asset.fileName == null || asset.fileName!!.length <= 512) { "Invalid asset file name" }
                require(asset.mimeType == null || MIME_TYPE.matches(asset.mimeType!!)) { "Invalid asset MIME type" }
            }
            val normalizedOrigin = PluginUrlPolicy.requireOrigin(origin)
            val approvedOrigins =
                buildSet {
                    add(normalizedOrigin)
                    additionalOrigins.mapTo(this, PluginUrlPolicy::requireOrigin)
                }
            return PluginInvocationContextV1(
                metadata =
                    PluginInvocationMetadataV1(
                        pluginId = pluginId,
                        platformId = platformId,
                        packageHash = packageHash,
                        scope = scope,
                        origin = normalizedOrigin,
                        accountId = accountId,
                        locale = locale,
                        approvedOrigins = approvedOrigins,
                        credentialAvailable = credential != null,
                        assetHandles = assets.keys,
                    ),
                credential = credential,
                assets = assets.toMap(),
            )
        }
    }
}

public enum class PluginCallTimeoutV1(
    public val millis: Long,
) {
    Detector(10_000),
    Normal(30_000),
    Extended(120_000),
}

public class PluginHostException(
    public val code: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private const val MAX_ASSET_COUNT = 32
private const val MAX_ASSET_BYTES = 1024L * 1024 * 1024
private val ID = Regex("[A-Za-z][A-Za-z0-9_.-]{0,255}")
private val HASH = Regex("[0-9a-f]{64}")
private val ASSET_HANDLE = Regex("[A-Za-z0-9_-]{1,256}")
private val MIME_TYPE = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+*-]+")
