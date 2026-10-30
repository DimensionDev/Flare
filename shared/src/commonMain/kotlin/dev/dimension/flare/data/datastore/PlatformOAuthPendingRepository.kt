package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.PlatformOAuthPendingData
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class PlatformOAuthPending(
    val platformId: String,
    val host: String,
    val flowId: String = DEFAULT_FLOW_ID,
    val createdAtEpochMillis: Long,
    val attributes: Map<String, String> = emptyMap(),
) {
    public companion object {
        public const val DEFAULT_FLOW_ID: String = "OAuth"
    }
}

@Single
@HiddenFromObjC
public class PlatformOAuthPendingRepository internal constructor(
    appDataStore: AppDataStore,
) {
    private val store: DataStore<PlatformOAuthPendingData> = appDataStore.platformOAuthPendingStore

    public suspend fun save(pending: PlatformOAuthPending) {
        store.updateData { current ->
            current.copy(
                entries =
                    current.entries
                        .filterNot {
                            it.platformId == pending.platformId &&
                                it.host == pending.host &&
                                it.flowId == pending.flowId
                        } + pending.toEntry(),
            )
        }
    }

    public suspend fun get(
        platformId: String,
        host: String,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ): PlatformOAuthPending? =
        store.data
            .first()
            .entries
            .firstOrNull {
                it.platformId == platformId &&
                    it.host == host &&
                    it.flowId == flowId
            }?.toPending()

    public suspend fun latest(
        platformId: String,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ): PlatformOAuthPending? =
        store.data
            .first()
            .entries
            .filter {
                it.platformId == platformId &&
                    it.flowId == flowId
            }.maxByOrNull { it.createdAtEpochMillis }
            ?.toPending()

    public suspend fun all(
        platformId: String,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ): List<PlatformOAuthPending> =
        store.data
            .first()
            .entries
            .filter {
                it.platformId == platformId &&
                    it.flowId == flowId
            }.map { it.toPending() }

    public suspend fun allByFlowId(flowId: String): List<PlatformOAuthPending> =
        store.data
            .first()
            .entries
            .filter { it.flowId == flowId }
            .map { it.toPending() }

    public suspend fun allForPlatform(platformId: String): List<PlatformOAuthPending> =
        store.data
            .first()
            .entries
            .filter { it.platformId == platformId }
            .map { it.toPending() }

    /** Removes [pending] only when the persisted value still matches it exactly. */
    public suspend fun consume(pending: PlatformOAuthPending): Boolean {
        var consumed = false
        store.updateData { current ->
            val index = current.entries.indexOfFirst { it.toPending() == pending }
            if (index < 0) {
                current
            } else {
                consumed = true
                current.copy(entries = current.entries.filterIndexed { entryIndex, _ -> entryIndex != index })
            }
        }
        return consumed
    }

    public suspend fun clear(
        platformId: String,
        host: String,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ) {
        store.updateData { current ->
            current.copy(
                entries =
                    current.entries.filterNot {
                        it.platformId == platformId &&
                            it.host == host &&
                            it.flowId == flowId
                    },
            )
        }
    }

    public suspend fun clear(pending: PlatformOAuthPending) {
        clear(
            platformId = pending.platformId,
            host = pending.host,
            flowId = pending.flowId,
        )
    }
}

private fun PlatformOAuthPending.toEntry(): PlatformOAuthPendingData.Entry =
    PlatformOAuthPendingData.Entry(
        platformId = platformId,
        host = host,
        flowId = flowId,
        createdAtEpochMillis = createdAtEpochMillis,
        attributes =
            attributes.map { (key, value) ->
                PlatformOAuthPendingData.Attribute(key = key, value = value)
            },
    )

private fun PlatformOAuthPendingData.Entry.toPending(): PlatformOAuthPending =
    PlatformOAuthPending(
        platformId = platformId,
        host = host,
        flowId = flowId,
        createdAtEpochMillis = createdAtEpochMillis,
        attributes = attributes.associate { it.key to it.value },
    )
