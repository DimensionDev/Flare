package dev.dimension.flare.data.datastore

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.PlatformOAuthPendingData
import dev.dimension.flare.model.PlatformType
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class PlatformOAuthPending(
    val platformType: PlatformType,
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
                            it.platformType == pending.platformType &&
                                it.host == pending.host &&
                                it.flowId == pending.flowId
                        } + pending.toEntry(),
            )
        }
    }

    public suspend fun get(
        platformType: PlatformType,
        host: String,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ): PlatformOAuthPending? =
        store.data
            .first()
            .entries
            .firstOrNull {
                it.platformType == platformType &&
                    it.host == host &&
                    it.flowId == flowId
            }?.toPending()

    public suspend fun latest(
        platformType: PlatformType,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ): PlatformOAuthPending? =
        store.data
            .first()
            .entries
            .filter {
                it.platformType == platformType &&
                    it.flowId == flowId
            }.maxByOrNull { it.createdAtEpochMillis }
            ?.toPending()

    public suspend fun all(
        platformType: PlatformType,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ): List<PlatformOAuthPending> =
        store.data
            .first()
            .entries
            .filter {
                it.platformType == platformType &&
                    it.flowId == flowId
            }.map { it.toPending() }

    public suspend fun clear(
        platformType: PlatformType,
        host: String,
        flowId: String = PlatformOAuthPending.DEFAULT_FLOW_ID,
    ) {
        store.updateData { current ->
            current.copy(
                entries =
                    current.entries.filterNot {
                        it.platformType == platformType &&
                            it.host == host &&
                            it.flowId == flowId
                    },
            )
        }
    }

    public suspend fun clear(pending: PlatformOAuthPending) {
        clear(
            platformType = pending.platformType,
            host = pending.host,
            flowId = pending.flowId,
        )
    }
}

private fun PlatformOAuthPending.toEntry(): PlatformOAuthPendingData.Entry =
    PlatformOAuthPendingData.Entry(
        platformType = platformType,
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
        platformType = platformType,
        host = host,
        flowId = flowId,
        createdAtEpochMillis = createdAtEpochMillis,
        attributes = attributes.associate { it.key to it.value },
    )
