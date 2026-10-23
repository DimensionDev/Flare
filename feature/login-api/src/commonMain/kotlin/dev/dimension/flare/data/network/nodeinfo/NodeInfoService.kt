package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.nodeinfo.model.NodeInfo
import dev.dimension.flare.data.network.nodeinfo.model.Schema10
import dev.dimension.flare.data.network.nodeinfo.model.Schema11
import dev.dimension.flare.data.network.nodeinfo.model.Schema20
import dev.dimension.flare.data.network.nodeinfo.model.Schema21
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object NodeInfoService {
    private val supportedSchemas =
        listOf(
            "http://nodeinfo.diaspora.software/ns/schema/1.0",
            "http://nodeinfo.diaspora.software/ns/schema/1.1",
            "http://nodeinfo.diaspora.software/ns/schema/2.0",
            "http://nodeinfo.diaspora.software/ns/schema/2.1",
        )

    public val pleromaNodeInfoName: List<String> =
        listOf(
            "pleroma",
            "akkoma",
        )

    private val unsupportedNodeInfoName =
        listOf(
            "wafrn",
        )

    public fun isUnsupportedSoftware(name: String?): Boolean = unsupportedNodeInfoName.any { it.equals(name, ignoreCase = true) }

    internal fun normalizeHost(host: String): String =
        host
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")

    public suspend fun fetchNodeInfo(host: String, proxy: dev.dimension.flare.data.model.ProxyRef = dev.dimension.flare.data.model.ProxyRef.Global): String? {
        val normalizedHost = normalizeHost(host)
        val response =
            ktorClient(proxy)
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
                    "http://nodeinfo.diaspora.software/ns/schema/1.0" -> {
                        ktorClient(proxy)
                            .get(
                                it.href,
                            ).body<Schema10>()
                            .software
                            ?.name
                            ?.value
                    }

                    "http://nodeinfo.diaspora.software/ns/schema/1.1" -> {
                        ktorClient(proxy)
                            .get(
                                it.href,
                            ).body<Schema11>()
                            .software
                            ?.name
                            ?.value
                    }

                    "http://nodeinfo.diaspora.software/ns/schema/2.0" -> {
                        ktorClient(proxy)
                            .get(it.href)
                            .body<Schema20>()
                            .software
                            ?.name
                    }

                    "http://nodeinfo.diaspora.software/ns/schema/2.1" -> {
                        ktorClient(proxy)
                            .get(it.href)
                            .body<Schema21>()
                            .software
                            ?.name
                    }

                    else -> {
                        throw IllegalArgumentException("Unsupported schema: ${it.rel}")
                    }
                }
            }.first()
    }
}
