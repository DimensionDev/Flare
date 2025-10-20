package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.nodeinfo.model.NodeInfo
import dev.dimension.flare.data.network.nodeinfo.model.Schema10
import dev.dimension.flare.data.network.nodeinfo.model.Schema11
import dev.dimension.flare.data.network.nodeinfo.model.Schema20
import dev.dimension.flare.data.network.nodeinfo.model.Schema21
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvo
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
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

    internal val pleromaNodeInfoName =
        listOf(
            "pleroma",
            "akkoma",
        )

    private val mastodonNodeInfoName =
        listOf(
            "mastodon",
            "kmyblue",
            "fedibird",
            "snac",
        ) + pleromaNodeInfoName

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

    suspend fun detectPlatformType(host: String): NodeData =
        coroutineScope {
            val hostCleaned =
                host
                    .trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removeSuffix("/")
            val xqt = listOf(xqtOldHost, "xqt.social", xqtHost)
            if (xqt.any { it.equals(hostCleaned, ignoreCase = true) }) {
                return@coroutineScope NodeData(
                    host = hostCleaned,
                    PlatformType.xQt,
                    PlatformType.xQt.name,
                    compatibleMode = false,
                )
            }
            val vvo = listOf(vvoHost, vvo, vvoHostShort, "vvo.social", vvoHostLong)
            if (vvo.any { it.equals(hostCleaned, ignoreCase = true) }) {
                return@coroutineScope NodeData(
                    host = hostCleaned,
                    PlatformType.VVo,
                    PlatformType.VVo.name,
                    compatibleMode = false,
                )
            }
            val nodeInfo =
                async {
                    tryRun {
                        val nodeInfo =
                            fetchNodeInfo(hostCleaned)
                                ?: throw IllegalArgumentException("NodeInfo not found: $hostCleaned")
                        if (mastodonNodeInfoName.any { it.equals(nodeInfo, ignoreCase = true) }) {
                            NodeData(
                                host = hostCleaned,
                                platformType = PlatformType.Mastodon,
                                software = nodeInfo,
                                compatibleMode =
                                    !nodeInfo.equals(
                                        "mastodon",
                                        ignoreCase = true,
                                    ),
                            )
                        } else if (misskeyNodeInfoName.any { it.equals(nodeInfo, ignoreCase = true) }) {
                            NodeData(
                                host = hostCleaned,
                                platformType = PlatformType.Misskey,
                                software = nodeInfo,
                                compatibleMode = !nodeInfo.equals("misskey", ignoreCase = true),
                            )
                        } else {
                            tryRun {
                                MisskeyService(
                                    "https://$hostCleaned/api/",
                                    accessTokenFlow = null,
                                ).meta(MetaRequest()).let {
                                    requireNotNull(it.name)
                                    // should be able to use as misskey
                                    NodeData(
                                        host = hostCleaned,
                                        platformType = PlatformType.Misskey,
                                        software = nodeInfo,
                                        compatibleMode = true,
                                    )
                                }
                            }.getOrElse {
                                tryRun {
                                    MastodonInstanceService("https://$hostCleaned/").instance().let {
                                        requireNotNull(it.title)
                                        // should be able to use as mastodon
                                        NodeData(
                                            host = hostCleaned,
                                            platformType = PlatformType.Mastodon,
                                            software = nodeInfo,
                                            compatibleMode = true,
                                        )
                                    }
                                }.getOrNull()
                            }
                        }
                    }.getOrNull()
                }

            val bluesky =
                async {
                    tryRun {
                        BlueskyService("https://$hostCleaned").describeServer().requireResponse()
                        NodeData(
                            host = hostCleaned,
                            platformType = PlatformType.Bluesky,
                            software = PlatformType.Bluesky.name,
                            compatibleMode = false,
                        )
                    }.getOrNull()
                }

            listOf(
                nodeInfo,
                bluesky,
            ).awaitAll().firstOrNull { it != null }
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
