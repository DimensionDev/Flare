@file:kotlin.OptIn(ExperimentalFoundationApi::class)

package dev.dimension.flare.ui.component

import android.content.Context
import androidx.annotation.OptIn
import androidx.collection.lruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import dev.dimension.flare.ui.component.status.LocalIsScrollingInProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.util.UUID
import kotlin.concurrent.timer
import kotlin.time.Duration.Companion.minutes

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
public fun VideoPlayer(
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
    autoPlay: Boolean = true,
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
    var isLoaded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (!isLoaded && LocalIsScrollingInProgress.current) {
            loadingPlaceholder()
        } else {
            var remainingTime by remember { mutableLongStateOf(0L) }
            val audioAttributes =
                remember {
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()
                }
            val player =
                remember(uri) {
                    playerPool
                        .get(uri)
                        .apply {
                            volume = if (muted) 0f else 1f
                        }
                }
            val state = rememberPresentationState(player)
            LaunchedEffect(muted, player) {
                player.volume = if (muted) 0f else 1f
                if (muted) {
                    player.setAudioAttributes(audioAttributes, false)
                } else {
                    player.setAudioAttributes(audioAttributes, true)
                }
            }
            LaunchedEffect(autoPlay) {
                if (autoPlay) {
                    player.play()
                }
            }
            LaunchedEffect(player) {
                while (true) {
                    isLoaded = !player.isLoading || player.duration > 0
                    if (remainingTimeContent != null) {
                        remainingTime = player.duration - player.currentPosition
                    }
                    delay(500)
                }
            }
            val isActive = rememberSurfaceBinding(uri)
            val playerModifier =
                Modifier
                    .clipToBounds()
                    .resizeWithContentScale(
                        contentScale = contentScale,
                        sourceSizeDp = state.videoSizeDp,
                    ).let {
                        if (onClick != null) {
                            it.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick,
                            )
                        } else {
                            it
                        }
                    }.matchParentSize()
                    .let {
                        if (keepScreenOn) {
                            it.keepScreenOn()
                        } else {
                            it
                        }
                    }
            if (isActive) {
                PlayerSurface(
                    player = player,
                    modifier = playerModifier,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                )
            } else {
                loadingPlaceholder.invoke(this)
            }
            if (!isLoaded) {
                loadingPlaceholder()
            } else {
                remainingTimeContent?.invoke(this, remainingTime)
            }
            DisposableEffect(uri) {
                onDispose {
                    player.volume = 0f
                    player.setAudioAttributes(audioAttributes, false)
                    playerPool.release(uri)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
public class VideoPlayerPool(
    private val context: Context,
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
                        setMediaItem(MediaItem.fromUri(uri))
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

    public fun peek(uri: String): ExoPlayer? = pool.get(uri)

    public fun get(uri: String): ExoPlayer {
        lock(uri)
        return pool.get(uri)!!
    }

    public fun lock(uri: String) {
        lockCount.put(uri, lockCount.getOrElse(uri) { 0 } + 1)
    }

    public fun release(uri: String): Boolean {
        lockCount.put(uri, lockCount.getOrElse(uri) { 0 } - 1)
        val count = lockCount.getOrElse(uri) { 0 }
        if (count == 0L) {
            pool.get(uri)?.pause()
        }

        return count == 0L
    }
}

@Composable
private fun rememberSurfaceBinding(
    uri: String,
    autoRequest: Boolean = true,
): Boolean {
    val surfaceKey = remember { UUID.randomUUID().toString() }
    var isActive by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        SurfaceBindingManager.register(uri, surfaceKey) { active ->
            isActive = active
        }

        if (autoRequest) {
            SurfaceBindingManager.requestBind(uri, surfaceKey)
        }
    }

    DisposableEffect(uri) {
        onDispose {
            SurfaceBindingManager.unregister(uri, surfaceKey)
        }
    }

    return isActive
}

private object SurfaceBindingManager {
    private val bindings = mutableMapOf<String, String>() // uri -> ownerKey
    private val listeners = mutableMapOf<String, MutableMap<String, (Boolean) -> Unit>>() // uri -> (ownerKey -> callback)

    fun register(
        uri: String,
        key: String,
        onActiveChanged: (Boolean) -> Unit,
    ) {
        val uriListeners = listeners.getOrPut(uri) { mutableMapOf() }
        uriListeners[key] = onActiveChanged

        val current = bindings[uri]
        if (current == null) {
            bindings[uri] = key
            onActiveChanged(true)
        } else {
            onActiveChanged(current == key)
        }
    }

    fun unregister(
        uri: String,
        key: String,
    ) {
        listeners[uri]?.remove(key)
        if (bindings[uri] == key) {
            bindings.remove(uri)
            listeners[uri]?.entries?.firstOrNull()?.let { (nextKey, callback) ->
                bindings[uri] = nextKey
                callback(true)
            }
        }
    }

    fun requestBind(
        uri: String,
        key: String,
    ) {
        if (bindings[uri] == key) return
        listeners[uri]?.get(bindings[uri])?.invoke(false)
        bindings[uri] = key
        listeners[uri]?.get(key)?.invoke(true)
    }
}
