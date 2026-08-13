package dev.dimension.flare.feature.plugin.login

import dev.dimension.flare.data.datastore.PlatformOAuthPending
import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.wire.WireLimitsV1
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement

@Serializable
public data class PluginOAuthPendingV1(
    val pluginId: String,
    val platformId: String,
    val packageHash: String,
    val methodId: String,
    val flowId: String,
    val origin: String,
    val state: String,
    val redirectUri: String,
    val createdAtEpochMillis: Long,
    val pendingPayload: JsonElement,
    val expectedAccountId: String? = null,
) {
    internal fun requireValid() {
        require(ID.matches(pluginId) && ID.matches(platformId) && ID.matches(methodId)) { "Invalid OAuth pending identity" }
        require(HASH.matches(packageHash)) { "Invalid OAuth pending package hash" }
        require(FLOW_ID.matches(flowId)) { "Invalid OAuth flow ID" }
        require(STATE.matches(state)) { "Invalid OAuth state" }
        require(PluginUrlPolicy.requireOrigin(origin) == origin) { "Invalid OAuth origin" }
        require(redirectUri == pluginOAuthRedirectUri(flowId)) { "Invalid OAuth redirect URI" }
        require(createdAtEpochMillis >= 0) { "Invalid OAuth pending timestamp" }
        require(expectedAccountId == null || expectedAccountId.length in 1..512) { "Invalid relogin account ID" }
        require(
            PluginJsonV1
                .encodeToString(JsonElement.serializer(), pendingPayload)
                .encodeToByteArray()
                .size <= WireLimitsV1.MAX_PENDING_PAYLOAD_BYTES,
        ) { "OAuth pending payload is too large" }
    }
}

public interface PluginOAuthPendingStoreV1 {
    public suspend fun save(pending: PluginOAuthPendingV1)

    public suspend fun load(flowId: String): PluginOAuthPendingV1?

    /** Atomically consumes the record only if it still equals [pending]. */
    public suspend fun consume(pending: PluginOAuthPendingV1): Boolean

    public suspend fun pruneCreatedBefore(epochMillis: Long) {
    }
}

public class PlatformOAuthPluginPendingStoreV1(
    private val repository: PlatformOAuthPendingRepository,
) : PluginOAuthPendingStoreV1 {
    private val mutex = Mutex()

    override suspend fun save(pending: PluginOAuthPendingV1): Unit =
        mutex.withLock {
            pending.requireValid()
            require(repository.allForPlatform(STORE_PLATFORM_ID).size < MAX_PENDING_RECORDS) {
                "Too many pending plugin OAuth flows"
            }
            repository.save(pending.toPlatformPending())
        }

    override suspend fun load(flowId: String): PluginOAuthPendingV1? {
        require(FLOW_ID.matches(flowId)) { "Invalid OAuth flow ID" }
        val records =
            repository
                .allByFlowId(flowId)
                .filter { it.platformId == STORE_PLATFORM_ID }
                .mapNotNull(::decode)
        if (records.size > 1) throw PluginOAuthPendingCorruptException("Duplicate plugin OAuth flow")
        return records.singleOrNull()
    }

    override suspend fun consume(pending: PluginOAuthPendingV1): Boolean {
        pending.requireValid()
        return repository.consume(pending.toPlatformPending())
    }

    override suspend fun pruneCreatedBefore(epochMillis: Long): Unit =
        mutex.withLock {
            require(epochMillis >= 0) { "Invalid OAuth expiry cutoff" }
            repository
                .allForPlatform(STORE_PLATFORM_ID)
                .filter { it.createdAtEpochMillis < epochMillis }
                .forEach { repository.consume(it) }
        }

    private fun decode(value: PlatformOAuthPending): PluginOAuthPendingV1? {
        val encoded = value.attributes[RECORD_ATTRIBUTE] ?: return null
        require(encoded.encodeToByteArray().size <= MAX_ENCODED_RECORD_BYTES) { "Plugin OAuth pending record is too large" }
        val pending = PluginJsonV1.decodeFromString<PluginOAuthPendingV1>(encoded)
        pending.requireValid()
        require(pending.flowId == value.flowId && pending.origin == value.host) { "Plugin OAuth pending key mismatch" }
        require(pending.createdAtEpochMillis == value.createdAtEpochMillis) { "Plugin OAuth pending timestamp mismatch" }
        return pending
    }
}

public class PluginOAuthPendingCorruptException(
    message: String,
) : IllegalStateException(message)

public fun pluginOAuthRedirectUri(flowId: String): String {
    require(FLOW_ID.matches(flowId)) { "Invalid OAuth flow ID" }
    return "flare://Callback/SignIn/Plugin/$flowId"
}

private fun PluginOAuthPendingV1.toPlatformPending(): PlatformOAuthPending =
    PlatformOAuthPending(
        platformId = STORE_PLATFORM_ID,
        host = origin,
        flowId = flowId,
        createdAtEpochMillis = createdAtEpochMillis,
        attributes = mapOf(RECORD_ATTRIBUTE to PluginJsonV1.encodeToString(this)),
    )

private const val STORE_PLATFORM_ID = "FlarePluginOAuthV1"
private const val RECORD_ATTRIBUTE = "record"
private const val MAX_ENCODED_RECORD_BYTES = 80 * 1_024
private const val MAX_PENDING_RECORDS = 64
private val ID = Regex("[A-Za-z][A-Za-z0-9_.-]{0,255}")
private val HASH = Regex("[0-9a-f]{64}")
private val FLOW_ID = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[1-5][0-9A-Fa-f]{3}-[89AaBb][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}")
private val STATE = Regex("[0-9a-f]{64}")
