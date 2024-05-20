package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.nodeinfo.model.NodeInfo
import dev.dimension.flare.data.network.nodeinfo.model.Schema10
import dev.dimension.flare.data.network.nodeinfo.model.Schema11
import dev.dimension.flare.data.network.nodeinfo.model.Schema20
import dev.dimension.flare.data.network.nodeinfo.model.Schema21
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.xqtHost
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

    suspend fun fetchNodeInfo(host: String): String {
        val response =
            ktorClient().get(
                URLBuilder(
                    protocol = URLProtocol.HTTPS,
                    host = host,
                    pathSegments = listOf(".well-known", "nodeinfo"),
                ).build(),
            ).body<NodeInfo>()
        return response.links.filter { it.rel in supportedSchemas }.map {
            when (it.rel) {
                "http://nodeinfo.diaspora.software/ns/schema/1.0" ->
                    ktorClient().get(
                        it.href,
                    ).body<Schema10.Coordinate>().software.name.value

                "http://nodeinfo.diaspora.software/ns/schema/1.1" ->
                    ktorClient().get(
                        it.href,
                    ).body<Schema11.Coordinate>().software.name.value

                "http://nodeinfo.diaspora.software/ns/schema/2.0" ->
                    ktorClient().get(it.href)
                        .body<Schema20.Coordinate>().software.name

                "http://nodeinfo.diaspora.software/ns/schema/2.1" ->
                    ktorClient().get(it.href)
                        .body<Schema21.Coordinate>().software.name

                else -> throw IllegalArgumentException("Unsupported schema: ${it.rel}")
            }
        }.first()
    }

    suspend fun detectPlatformType(host: String): PlatformType {
        if (host.equals(xqtHost, ignoreCase = true) || host.equals("x.social", ignoreCase = true)) {
            return PlatformType.xQt
        }
        if (host.equals(vvoHost, ignoreCase = true) || host.equals("vvo.social", ignoreCase = true)) {
            return PlatformType.VVo
        }
        return try {
            val nodeInfo = fetchNodeInfo(host)
            when {
                nodeInfo.contains("Mastodon", ignoreCase = true) -> PlatformType.Mastodon
                nodeInfo.contains("Misskey", ignoreCase = true) -> PlatformType.Misskey
                else -> throw IllegalArgumentException("Unsupported platform: $nodeInfo")
            }
        } catch (e1: Exception) {
            try {
                BlueskyService("https://$host").describeServer().requireResponse()
                PlatformType.Bluesky
            } catch (e2: Exception) {
                throw IllegalArgumentException("Unsupported platform: $e2")
            }
        }
    }
}
