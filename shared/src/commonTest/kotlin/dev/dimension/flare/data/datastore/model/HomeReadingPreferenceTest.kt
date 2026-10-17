package dev.dimension.flare.data.datastore.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class HomeReadingPreferenceTest {
    @Serializable
    private data class PreviousSettings(
        @ProtoNumber(1) val version: String,
        @ProtoNumber(7) val refreshHomeTimelineOnLaunch: Boolean,
        @ProtoNumber(8) val homeTimelineAutoRefreshInterval: TimelineAutoRefreshInterval,
    )

    @Test
    fun persistedSettingsWithoutTheNewFieldRemainOptedOut() {
        val old = PreviousSettings("old", false, TimelineAutoRefreshInterval.FIVE_MINUTES)
        val encoded = ProtoBuf.encodeToByteArray(PreviousSettings.serializer(), old)
        val restored = ProtoBuf.decodeFromByteArray(AppSettings.serializer(), encoded)
        assertFalse(restored.rememberHomeTimelineReadingPosition)
        assertFalse(restored.refreshHomeTimelineOnLaunch)
        assertEquals(TimelineAutoRefreshInterval.FIVE_MINUTES, restored.homeTimelineAutoRefreshInterval)
        val enabled = restored.copy(rememberHomeTimelineReadingPosition = true)
        val saved = ProtoBuf.encodeToByteArray(AppSettings.serializer(), enabled)
        assertTrue(ProtoBuf.decodeFromByteArray(AppSettings.serializer(), saved).rememberHomeTimelineReadingPosition)
    }
}
