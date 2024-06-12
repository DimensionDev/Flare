package dev.dimension.flare.ui.component

import android.content.Context
import android.util.Xml
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.collection.lruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import androidx.media3.ui.PlayerView
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import kotlin.concurrent.timer
import kotlin.time.Duration.Companion.minutes

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    showControls: Boolean = false,
    keepScreenOn: Boolean = false,
    aspectRatio: Float? = null,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    playerPool: VideoPlayerPool = koinInject(),
    remainingTimeContent: @Composable (BoxScope.(Long) -> Unit)? = null,
    loadingPlaceholder: @Composable BoxScope.() -> Unit = {
        if (previewUri != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    model = previewUri,
                    contentScale = contentScale,
                    contentDescription = contentDescription,
                    modifier =
                        Modifier
                            .let {
                                if (aspectRatio != null) {
                                    it.aspectRatio(
                                        aspectRatio,
                                        matchHeightConstraintsFirst = aspectRatio > 1f,
                                    )
                                } else {
                                    it
                                }
                            }.fillMaxSize(),
                )
            }
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
            )
        }
    },
) {
    val scope = rememberCoroutineScope()
    var isLoaded by remember { mutableStateOf(true) }
    var remainingTime by remember { mutableLongStateOf(0L) }
    Box(modifier = modifier) {
        AndroidView(
            modifier =
                Modifier
                    .clipToBounds()
                    .matchParentSize(),
            factory = { context ->
                val exoPlayer =
                    playerPool
                        .get(uri)
                        .apply {
                            play()
                            volume = if (muted) 0f else 1f
                        }
                val parser = context.resources.getXml(R.xml.video_view)
                var type = 0
                while (type != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG) {
                    type = parser.next()
                }
                val attrs = Xml.asAttributeSet(parser)
                PlayerView(context, attrs).apply {
                    controllerShowTimeoutMs = -1
                    useController = showControls
                    player = exoPlayer
                    scope.launch {
                        while (true) {
                            isLoaded = !exoPlayer.isLoading || exoPlayer.duration > 0
                            if (remainingTimeContent != null) {
                                remainingTime = exoPlayer.duration - exoPlayer.currentPosition
                            }
                            delay(500)
                        }
                    }
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
//                    if (aspectRatio == null) {
                    if (contentScale == ContentScale.Crop) {
                        this.resizeMode = RESIZE_MODE_ZOOM
                    }
//                    }
                    this.keepScreenOn = keepScreenOn
                    if (onClick != null) {
                        setOnClickListener {
                            onClick()
                        }
                    }
                    if (onLongClick != null) {
                        setOnLongClickListener {
                            onLongClick()
                            true
                        }
                    }
                }
            },
            onRelease = { playerView ->
                playerView.player = null
                playerPool.release(uri)
            },
        )
        if (!isLoaded) {
            loadingPlaceholder()
        } else {
            remainingTimeContent?.invoke(this, remainingTime)
        }
    }
}

@OptIn(UnstableApi::class)
class CacheDataSourceFactory(
    private val context: Context,
    private val maxFileSize: Long,
) : DataSource.Factory {
    private val simpleCache: SimpleCache by lazy {
        VideoCache.getInstance(context)
    }

    private val defaultDatasourceFactory: DefaultDataSource.Factory

    override fun createDataSource(): DataSource =
        CacheDataSource(
            simpleCache,
            defaultDatasourceFactory.createDataSource(),
            FileDataSource(),
            CacheDataSink(simpleCache, maxFileSize),
            CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            null,
        )

    init {
        val userAgent =
            Util.getUserAgent(
                context,
                BuildConfig.APPLICATION_ID,
            )
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        defaultDatasourceFactory =
            DefaultDataSource
                .Factory(
                    this.context,
                    DefaultHttpDataSource
                        .Factory()
                        .setUserAgent(userAgent)
                        .setTransferListener(bandwidthMeter),
                ).setTransferListener(bandwidthMeter)
    }
}

@UnstableApi
object VideoCache {
    private var cache: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024L)
        if (cache == null) {
            cache =
                SimpleCache(
                    File(context.cacheDir, "media"),
                    evictor,
                    StandaloneDatabaseProvider(context),
                )
        }
        return cache as SimpleCache
    }
}

@UnstableApi
class VideoPlayerPool(
    private val context: Context,
    private val factory: ProgressiveMediaSource.Factory,
    private val scope: CoroutineScope,
) {
    private val positionPool = mutableMapOf<String, Long>()
    private val lockCount = linkedMapOf<String, Long>()
    private val pool =
        lruCache<String, ExoPlayer>(
            maxSize = 10,
            create = { uri ->
                ExoPlayer
                    .Builder(context)
                    .build()
                    .apply {
                        setMediaSource(factory.createMediaSource(MediaItem.fromUri(uri)))
                        prepare()
                        playWhenReady = true
                        repeatMode = Player.REPEAT_MODE_ALL
                    }
            },
            onEntryRemoved = { evicted, key, oldValue, newValue ->
                if (evicted) {
                    positionPool.put(key, oldValue.currentPosition)
                    oldValue.release()
                } else if (newValue != null) {
                    val position = positionPool.get(key)
                    if (position != null) {
                        newValue.seekTo(position)
                        positionPool.remove(key)
                    }
                }
            },
        )

    private val clearTimer =
        timer(period = 1.minutes.inWholeMilliseconds) {
            pool.snapshot().forEach { (uri, _) ->
                if (lockCount.getOrElse(uri) { 0 } == 0L) {
                    pool.remove(uri)?.let {
                        scope.launch {
                            withContext(Dispatchers.Main) {
                                it.release()
                            }
                        }
                    }
                }
            }
        }

    fun get(uri: String): ExoPlayer {
        lock(uri)
        return pool.get(uri)!!
    }

    fun lock(uri: String) {
        lockCount.put(uri, lockCount.getOrElse(uri) { 0 } + 1)
    }

    fun release(uri: String): Boolean {
        lockCount.put(uri, lockCount.getOrElse(uri) { 0 } - 1)
        val count = lockCount.getOrElse(uri) { 0 }
        if (count == 0L) {
            pool.get(uri)?.pause()
        }

        return count == 0L
    }
}
