@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import coil3.Extras
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.httpHeaders
import coil3.network.ktor3.asNetworkClient
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.data.network.ProxySnapshot
import dev.dimension.flare.data.network.mediaHttpClient
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.CancellationException

private val imageProxy = Extras.Key<ProxyRef>(ProxyRef.Unbound)

public fun proxyImageFetcherFactory(): NetworkFetcher.Factory =
    NetworkFetcher.Factory(
        networkClient = {
            object : NetworkClient {
                override suspend fun <T> executeRequest(
                    request: NetworkRequest,
                    block: suspend (NetworkResponse) -> T,
                ): T =
                    mediaHttpClient((request.extras[imageProxy] ?: ProxyRef.Unbound)).let { client ->
                        try {
                            client.asNetworkClient().executeRequest(request, block)
                        } finally {
                            client.close()
                            client.engine.close()
                        }
                    }
            }
        },
    )

public fun ImageRequest.Builder.media(
    data: UiMedia,
    snapshot: ProxySnapshot?,
): ImageRequest.Builder =
    apply {
        data(data.url)
        extras[imageProxy] = data.proxy
        memoryCacheKey("${snapshot?.key.orEmpty()}|${data.cacheKey}")
        placeholderMemoryCacheKey(data.preview?.let { "${snapshot?.key.orEmpty()}|${it.cacheKey}" })
        diskCacheKey(data.cacheKey)
        httpHeaders(
            NetworkHeaders
                .Builder()
                .apply {
                    data.customHeaders?.forEach { (key, value) -> set(key, value) }
                }.build(),
        )
    }

@Composable
public fun rememberMediaProxySnapshot(media: UiMedia?): Result<ProxySnapshot>? {
    if (media?.isRemote != true) return null
    val revision by ProxyNetwork.changes.collectAsState()
    val route by key(media.proxy) {
        produceState<Result<ProxySnapshot>?>(null, revision) {
            value =
                try {
                    Result.success(ProxyNetwork.snapshot(media.proxy))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
        }
    }
    return route
}

@Composable
public fun rememberMediaImageRequest(
    media: UiMedia?,
    crossfade: Boolean = true,
): ImageRequest {
    val context = LocalPlatformContext.current
    val route = rememberMediaProxySnapshot(media)
    val allowHardware = LocalNetworkImageAllowHardware.current
    return remember(media, route, context, allowHardware, crossfade) {
        ImageRequest
            .Builder(context)
            .apply {
                if (media != null && (!media.isRemote || route != null)) media(media, route?.getOrNull())
            }.crossfade(crossfade && allowHardware)
            .applyAllowHardware(allowHardware)
            .build()
    }
}
