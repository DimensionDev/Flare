package dev.dimension.flare.common

import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CorruptImageRetryInterceptor(
    private val diskCache: () -> DiskCache?,
) : Interceptor {
    private val requests = mutableMapOf<String, PendingRequest>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString()
        if (Build.VERSION.SDK_INT < 28 || (!url.startsWith("https://") && !url.startsWith("http://"))) {
            return chain.proceed()
        }
        val key = request.diskCacheKey ?: url
        val pending =
            synchronized(requests) {
                requests.getOrPut(key) { PendingRequest() }.also { it.users++ }
            }
        try {
            // Serialize readers too, so a late failed decode cannot evict a repaired entry.
            return pending.mutex.withLock {
                val result = chain.proceed()
                if (!result.isCorruptImage()) return@withLock result
                evict(request, key)
                if (!request.networkCachePolicy.readEnabled) return@withLock result

                val retry =
                    request
                        .newBuilder()
                        .diskCachePolicy(if (request.diskCachePolicy.writeEnabled) CachePolicy.WRITE_ONLY else CachePolicy.DISABLED)
                        .memoryCachePolicy(if (request.memoryCachePolicy.writeEnabled) CachePolicy.WRITE_ONLY else CachePolicy.DISABLED)
                        .build()
                chain.withRequest(retry).proceed().also {
                    if (it.isCorruptImage()) evict(retry, key)
                }
            }
        } finally {
            synchronized(requests) {
                if (--pending.users == 0) requests.remove(key)
            }
        }
    }

    private suspend fun evict(
        request: ImageRequest,
        key: String,
    ) {
        if (request.diskCachePolicy != CachePolicy.DISABLED) {
            withContext(request.fetcherCoroutineContext) {
                diskCache()?.remove(key)
            }
        }
    }

    @RequiresApi(28)
    private fun ImageResult.isCorruptImage(): Boolean {
        val error = (this as? ErrorResult)?.throwable as? ImageDecoder.DecodeException ?: return false
        return error.error == ImageDecoder.DecodeException.SOURCE_INCOMPLETE ||
            error.error == ImageDecoder.DecodeException.SOURCE_MALFORMED_DATA
    }

    private class PendingRequest {
        val mutex = Mutex()
        var users = 0
    }
}
