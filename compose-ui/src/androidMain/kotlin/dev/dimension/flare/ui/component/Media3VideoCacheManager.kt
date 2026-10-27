package dev.dimension.flare.ui.component

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.dimension.flare.data.network.proxyCallFactory
import dev.dimension.flare.ui.model.UiMedia
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File

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

    public fun mediaSourceFactory(media: UiMedia): DefaultMediaSourceFactory =
        DefaultMediaSourceFactory(dataSourceFactory(media = media))

    public fun dataSourceFactory(
        media: UiMedia,
        allowNetwork: Boolean = true,
        writeToCache: Boolean = true,
    ): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(cache)
            .setCacheKeyFactory(CacheKeyFactory { dataSpec -> mediaKey(media, dataSpec.key ?: dataSpec.uri.toString()) })
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
            .apply {
                if (allowNetwork) {
                    setUpstreamDataSourceFactory(upstreamDataSourceFactory(media))
                    if (!writeToCache) {
                        setCacheWriteDataSinkFactory(null)
                    }
                } else {
                    setUpstreamDataSourceFactory(null)
                    setCacheWriteDataSinkFactory(null)
                }
            }

    public fun cacheOnlyDataSourceFactory(media: UiMedia): CacheDataSource.Factory =
        dataSourceFactory(
            media = media,
            allowNetwork = false,
            writeToCache = false,
        )

    public fun downloadingDataSourceFactory(media: UiMedia): CacheDataSource.Factory =
        dataSourceFactory(
            media = media,
            allowNetwork = true,
            writeToCache = true,
        )

    public fun isFullyCached(
        media: UiMedia,
    ): Boolean {
        val key = mediaKey(media, media.urlForDownload)
        val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return contentLength > 0 && contentLength != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0, contentLength)
    }

    private fun upstreamDataSourceFactory(media: UiMedia): DataSource.Factory =
        DefaultDataSource.Factory(applicationContext,
            OkHttpDataSource.Factory(proxyCallFactory(media))
                .setDefaultRequestProperties(media.customHeaders.orEmpty()),
        )

    private fun mediaKey(media: UiMedia, uri: String): String =
        media.cacheKeyFor(uri)

    private companion object {
        private const val CACHE_DIR_NAME = "media3-video"
        private const val MAX_CACHE_SIZE_BYTES = 512L * 1024L * 1024L
    }
}
