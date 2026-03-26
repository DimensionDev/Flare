package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.nodeinfo.model.NodeInfo
import dev.dimension.flare.data.network.nodeinfo.model.Schema10
import dev.dimension.flare.data.network.nodeinfo.model.Schema11
import dev.dimension.flare.data.network.nodeinfo.model.Schema20
import dev.dimension.flare.data.network.nodeinfo.model.Schema21
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.spec
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

internal data object NodeInfoService {
    private val supportedSchemas =
        listOf(
            "http://nodeinfo.diaspora.software/ns/schema/1.0",
            "http://nodeinfo.diaspora.software/ns/schema/1.1",
            "http://nodeinfo.diaspora.software/ns/schema/2.0",
            "http://nodeinfo.diaspora.software/ns/schema/2.1",
        )

    internal val pleromaNodeInfoName =
        listOf(
            "pleroma",
            "akkoma",
        )

    internal fun normalizeHost(host: String): String =
        host
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")

    suspend fun fetchNodeInfo(host: String): String? {
        val normalizedHost = normalizeHost(host)
        val response =
            ktorClient()
                .get(
                    URLBuilder(
                        protocol = URLProtocol.HTTPS,
                        host = normalizedHost,
                        pathSegments = listOf(".well-known", "nodeinfo"),
                    ).build(),
                ).body<NodeInfo>()
        return response.links
            .filter { it.rel in supportedSchemas }
            .map {
                when (it.rel) {
                    "http://nodeinfo.diaspora.software/ns/schema/1.0" ->
                        ktorClient()
                            .get(
                                it.href,
                            ).body<Schema10>()
                            .software
                            ?.name
                            ?.value

                    "http://nodeinfo.diaspora.software/ns/schema/1.1" ->
                        ktorClient()
                            .get(
                                it.href,
                            ).body<Schema11>()
                            .software
                            ?.name
                            ?.value

                    "http://nodeinfo.diaspora.software/ns/schema/2.0" ->
                        ktorClient()
                            .get(it.href)
                            .body<Schema20>()
                            .software
                            ?.name

                    "http://nodeinfo.diaspora.software/ns/schema/2.1" ->
                        ktorClient()
                            .get(it.href)
                            .body<Schema21>()
                            .software
                            ?.name

                    else -> throw IllegalArgumentException("Unsupported schema: ${it.rel}")
                }
            }.first()
    }

    suspend fun detectPlatformType(host: String): NodeData {
        val hostCleaned = normalizeHost(host)
        return PlatformType.entries
            .map { it.spec.detector }
            .distinct()
            .sortedByDescending { it.priority }
            .firstNotNullOfOrNull { detector -> detector.detect(hostCleaned) }
            ?: throw IllegalArgumentException("Unsupported platform: $hostCleaned")
    }
}

public data class NodeData(
    val host: String,
    val platformType: PlatformType,
    val software: String,
    // not officially supported, but works fine for basic features
    val compatibleMode: Boolean,
)
