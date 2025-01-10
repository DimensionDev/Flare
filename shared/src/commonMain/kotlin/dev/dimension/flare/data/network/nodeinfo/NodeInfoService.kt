package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.nodeinfo.model.NodeInfo
import dev.dimension.flare.data.network.nodeinfo.model.Schema10
import dev.dimension.flare.data.network.nodeinfo.model.Schema11
import dev.dimension.flare.data.network.nodeinfo.model.Schema20
import dev.dimension.flare.data.network.nodeinfo.model.Schema21
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvo
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.model.xqtHost
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data object NodeInfoService {
    private val supportedSchemas =
        listOf(
            "http://nodeinfo.diaspora.software/ns/schema/1.0",
            "http://nodeinfo.diaspora.software/ns/schema/1.1",
            "http://nodeinfo.diaspora.software/ns/schema/2.0",
            "http://nodeinfo.diaspora.software/ns/schema/2.1",
        )

    private val mastodonNodeInfoName =
        listOf(
            "mastodon",
            "kmyblue",
            "fedibird",
        )

    private val misskeyNodeInfoName =
        listOf(
            "misskey",
            "sharkey",
            "cherrypick",
        )

    suspend fun fetchNodeInfo(host: String): String? {
        val response =
            ktorClient()
                .get(
                    URLBuilder(
                        protocol = URLProtocol.HTTPS,
                        host = host,
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

    suspend fun detectPlatformType(host: String): PlatformType =
        coroutineScope {
            val xqt = listOf(xqtHost, "xqt.social", "x.com")
            if (xqt.any { it.equals(host, ignoreCase = true) }) {
                return@coroutineScope PlatformType.xQt
            }
            val vvo = listOf(vvoHost, vvo, vvoHostShort, "vvo.social", vvoHostLong)
            if (vvo.any { it.equals(host, ignoreCase = true) }) {
                return@coroutineScope PlatformType.VVo
            }
            val nodeInfo =
                async {
                    runCatching {
                        val nodeInfo = fetchNodeInfo(host)
                        when {
                            mastodonNodeInfoName.any { it.equals(nodeInfo, ignoreCase = true) } -> PlatformType.Mastodon
                            misskeyNodeInfoName.any { it.equals(nodeInfo, ignoreCase = true) } -> PlatformType.Misskey
                            else -> throw IllegalArgumentException("Unsupported platform: $nodeInfo")
                        }
                    }.onFailure {
                        it.printStackTrace()
                    }.getOrNull()
                }

            val bluesky =
                async {
                    runCatching {
                        BlueskyService("https://$host").describeServer().requireResponse()
                        PlatformType.Bluesky
                    }.getOrNull()
                }

            listOf(
                nodeInfo,
                bluesky,
            ).awaitAll().firstOrNull { it != null }
                ?: throw IllegalArgumentException("Unsupported platform: $host")
        }
}
