package dev.dimension.flare.data.datastore.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Guards the ProtoBuf field numbering of [AppSettings.proxy].
 *
 * kotlinx ProtoBuf assigns field numbers in declaration order, so `proxy` has to stay the last
 * property of [AppSettings]. These tests decode bytes produced by a schema that does not know
 * `proxy` at all, which is exactly what an upgrading user has on disk.
 */
@OptIn(ExperimentalSerializationApi::class)
class ProxyConfigSerializationTest {
    @Test
    fun decodesLegacySettingsWithoutProxyAsDefaultProxyConfig() {
        val legacy =
            ProxyLegacyAppSettings(
                version = "legacy",
                aiConfig = AppSettings.AiConfig(tldr = true, agent = false),
                language = "zh-Hans",
                translateConfig =
                    AppSettings.TranslateConfig(
                        preTranslate = true,
                        preferPlatformTranslation = true,
                    ),
                mxgaEnabled = true,
                refreshHomeTimelineOnLaunch = false,
                homeTimelineAutoRefreshInterval = TimelineAutoRefreshInterval.FIFTEEN_MINUTES,
            )

        val bytes =
            ProtoBuf.encodeToByteArray(
                serializer = ProxyLegacyAppSettings.serializer(),
                legacy,
            )
        val decoded = ProtoBuf.decodeFromByteArray(AppSettings.serializer(), bytes)

        // Every pre-existing field keeps its value, and re-encoding with the current serializer
        // reproduces the legacy bytes byte-for-byte: no field number moved and the appended
        // `proxy` field is simply absent from the old payload.
        assertEquals(legacy.version, decoded.version)
        assertEquals(legacy.aiConfig, decoded.aiConfig)
        assertEquals(legacy.language, decoded.language)
        assertEquals(legacy.translateConfig, decoded.translateConfig)
        assertEquals(legacy.linkOpenDefaults, decoded.linkOpenDefaults)
        assertEquals(true, decoded.mxgaEnabled)
        assertEquals(false, decoded.refreshHomeTimelineOnLaunch)
        assertEquals(TimelineAutoRefreshInterval.FIFTEEN_MINUTES, decoded.homeTimelineAutoRefreshInterval)
        assertContentEquals(bytes, ProtoBuf.encodeToByteArray(AppSettings.serializer(), decoded))

        assertEquals(AppSettings.ProxyConfig(), decoded.proxy)
        assertEquals(AppSettings.ProxyConfig.Type.System, decoded.proxy.type)
        assertEquals("", decoded.proxy.host)
        assertEquals(0, decoded.proxy.port)
    }

    @Test
    fun encodesAndDecodesHttpProxyConfig() {
        val settings =
            AppSettings(
                version = "1",
                proxy =
                    AppSettings.ProxyConfig(
                        type = AppSettings.ProxyConfig.Type.Http,
                        host = "192.168.3.2",
                        port = 7890,
                    ),
            )

        val decoded = roundTrip(settings)

        assertEquals(AppSettings.ProxyConfig.Type.Http, decoded.proxy.type)
        assertEquals("192.168.3.2", decoded.proxy.host)
        assertEquals(7890, decoded.proxy.port)
        assertEquals(settings.proxy, decoded.proxy)
    }

    @Test
    fun encodesAndDecodesEveryProxyType() {
        val decodedTypes =
            AppSettings.ProxyConfig.Type.entries.map { type ->
                val decoded =
                    roundTrip(
                        AppSettings(
                            version = "1",
                            proxy = AppSettings.ProxyConfig(type = type, host = "proxy.local", port = 8080),
                        ),
                    )

                assertEquals(type, decoded.proxy.type)
                assertEquals("proxy.local", decoded.proxy.host)
                assertEquals(8080, decoded.proxy.port)
                decoded.proxy.type
            }

        assertEquals(
            listOf(
                AppSettings.ProxyConfig.Type.System,
                AppSettings.ProxyConfig.Type.Direct,
                AppSettings.ProxyConfig.Type.Http,
            ),
            decodedTypes,
        )
    }

    @Test
    fun encodesAndDecodesHalfConfiguredHttpProxy() {
        val settings =
            AppSettings(
                version = "1",
                proxy = AppSettings.ProxyConfig(type = AppSettings.ProxyConfig.Type.Http),
            )

        val decoded = roundTrip(settings)

        assertEquals(AppSettings.ProxyConfig.Type.Http, decoded.proxy.type)
        assertEquals("", decoded.proxy.host)
        assertEquals(0, decoded.proxy.port)
    }

    private fun roundTrip(settings: AppSettings): AppSettings =
        ProtoBuf.decodeFromByteArray(
            AppSettings.serializer(),
            ProtoBuf.encodeToByteArray(AppSettings.serializer(), settings),
        )
}

/** Mirror of [AppSettings] as it was serialized before the `proxy` field was appended. */
@Serializable
private data class ProxyLegacyAppSettings(
    val version: String = "",
    val aiConfig: AppSettings.AiConfig = AppSettings.AiConfig(),
    val language: String = "",
    val translateConfig: AppSettings.TranslateConfig = AppSettings.TranslateConfig(),
    val linkOpenDefaults: AppSettings.LinkOpenDefaults = AppSettings.LinkOpenDefaults(),
    val mxgaEnabled: Boolean = false,
    val refreshHomeTimelineOnLaunch: Boolean = true,
    val homeTimelineAutoRefreshInterval: TimelineAutoRefreshInterval = TimelineAutoRefreshInterval.DISABLED,
)
