package dev.dimension.flare.ui.component

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.security.MessageDigest

@Stable
@Single
public class Media3VideoCacheManager(
    @Provided context: Context,
) {
    private val applicationContext = context.applicationContext
    private val databaseProvider by lazy {
        StandaloneDatabaseProvider(applicationContext)
    }

    public val cache: SimpleCache by lazy {
        SimpleCache(
            File(applicationContext.cacheDir, CACHE_DIR_NAME),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES),
            databaseProvider,
        )
    }

    public fun mediaSourceFactory(customHeaders: Map<String, String>? = null): DefaultMediaSourceFactory =
        DefaultMediaSourceFactory(dataSourceFactory(customHeaders = customHeaders))

    public fun dataSourceFactory(
        customHeaders: Map<String, String>? = null,
        allowNetwork: Boolean = true,
        writeToCache: Boolean = true,
    ): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(cache)
            .setCacheKeyFactory(cacheKeyFactory(customHeaders))
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
            .apply {
                if (allowNetwork) {
                    setUpstreamDataSourceFactory(upstreamDataSourceFactory(customHeaders))
                    if (!writeToCache) {
                        setCacheWriteDataSinkFactory(null)
                    }
                } else {
                    setUpstreamDataSourceFactory(null)
                    setCacheWriteDataSinkFactory(null)
                }
            }

    public fun cacheOnlyDataSourceFactory(customHeaders: Map<String, String>? = null): CacheDataSource.Factory =
        dataSourceFactory(
            customHeaders = customHeaders,
            allowNetwork = false,
            writeToCache = false,
        )

    public fun downloadingDataSourceFactory(customHeaders: Map<String, String>? = null): CacheDataSource.Factory =
        dataSourceFactory(
            customHeaders = customHeaders,
            allowNetwork = true,
            writeToCache = true,
        )

    public fun isFullyCached(
        uri: String,
        customHeaders: Map<String, String>? = null,
    ): Boolean {
        val key = cacheKey(uri, customHeaders)
        val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return contentLength > 0 && contentLength != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0, contentLength)
    }

    private fun upstreamDataSourceFactory(customHeaders: Map<String, String>?): DataSource.Factory {
        val httpFactory =
            DefaultHttpDataSource
                .Factory()
                .setAllowCrossProtocolRedirects(true)
        if (!customHeaders.isNullOrEmpty()) {
            httpFactory.setDefaultRequestProperties(customHeaders)
        }
        return DefaultDataSource.Factory(applicationContext, httpFactory)
    }

    private fun cacheKeyFactory(customHeaders: Map<String, String>?): CacheKeyFactory =
        CacheKeyFactory { dataSpec ->
            cacheKey(dataSpec.key ?: dataSpec.uri.toString(), customHeaders)
        }

    private fun cacheKey(
        baseKey: String,
        customHeaders: Map<String, String>?,
    ): String {
        if (customHeaders.isNullOrEmpty()) {
            return baseKey
        }
        val headerFingerprint =
            customHeaders
                .toSortedMap()
                .entries
                .joinToString(separator = "\n") { (key, value) -> "$key:$value" }
                .sha256()
        return "$baseKey#headers=$headerFingerprint"
    }

    private fun String.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

    private companion object {
        private const val CACHE_DIR_NAME = "media3-video"
        private const val MAX_CACHE_SIZE_BYTES = 512L * 1024L * 1024L
    }
}
