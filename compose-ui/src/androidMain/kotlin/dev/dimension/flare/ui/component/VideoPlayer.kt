@file:kotlin.OptIn(ExperimentalFoundationApi::class)

package dev.dimension.flare.ui.component

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.ui.component.status.LocalIsScrollingInProgress
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.android.awaitFrame
import org.koin.compose.koinInject
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val audioAttributes by lazy {
    AudioAttributes
        .Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
}

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
public fun VideoPlayer(
    uri: String,
    customHeaders: ImmutableMap<String, String>? = null,
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
                    customHeaders = customHeaders,
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
    idlePlaceholder: @Composable BoxScope.() -> Unit = {
        if (previewUri != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    model = previewUri,
                    customHeaders = customHeaders,
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
                Box(
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = PlatformTheme.shapes.medium,
                            ).padding(horizontal = 8.dp, vertical = 4.dp)
                            .align(Alignment.BottomStart),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.CirclePlay,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(16.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    },
) {
    var isLoaded by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var currentRect by remember { mutableStateOf(IntRect.Zero) }
    val request =
        remember(uri, customHeaders) {
            VideoRequest(uri = uri, customHeaders = customHeaders?.toMap().orEmpty())
        }
    val binding = rememberSurfaceBinding(request)
    val player = binding.first

    LaunchedEffect(binding.second, currentRect, visible) {
        binding.second.update(currentRect, visible)
    }

    Box(
        modifier =
            modifier
                .onLayoutRectChanged(debounceMillis = 300) {
                    currentRect = it.boundsInWindow
                }.onVisibilityChanged(300, 0.66f) {
                    visible = it
                },
    ) {
        if ((!isLoaded && LocalIsScrollingInProgress.current) || !visible || player == null) {
            idlePlaceholder()
        } else {
            var remainingTime by remember { mutableLongStateOf(0L) }
            val playerState = rememberPresentationState(player)
            DisposableEffect(muted, player) {
                player.volume = if (muted) 0f else 1f
                if (muted) {
                    player.setAudioAttributes(audioAttributes, false)
                } else {
                    player.setAudioAttributes(audioAttributes, true)
                }
                onDispose {
//                    player.volume = 0f
//                    player.setAudioAttributes(audioAttributes, false)
                }
            }
            LaunchedEffect(autoPlay) {
                if (autoPlay) {
                    player.play()
                }
            }
            if (autoPlay) {
                LifecycleResumeEffect(player) {
                    player.play()
                    onPauseOrDispose {
                        player.pause()
                    }
                }
            }
            LaunchedEffect(player) {
                while (true) {
                    isLoaded = player.isPlaying || player.currentPosition > 0L
                    if (remainingTimeContent != null) {
                        remainingTime = player.duration - player.currentPosition
                    }
                    awaitFrame()
                }
            }
            AnimatedContent(
                isLoaded,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
            ) { isLoaded ->
                if (isLoaded) {
                    val playerModifier =
                        Modifier
                            .clipToBounds()
                            .resizeWithContentScale(
                                contentScale = contentScale,
                                sourceSizeDp = playerState.videoSizeDp,
                            ).let {
                                if (onClick != null) {
                                    it.combinedClickable(
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                    )
                                } else {
                                    it
                                }
                            }.let {
                                if (keepScreenOn) {
                                    it.keepScreenOn()
                                } else {
                                    it
                                }
                            }.let {
                                if (aspectRatio != null && playerState.videoSizeDp == null) {
                                    it.aspectRatio(aspectRatio)
                                } else {
                                    it
                                }
                            }
                    DisposableEffect(Unit) {
                        onDispose {
                            player.clearVideoSurface()
                        }
                    }
                    Box {
                        PlayerSurface(
                            player = player,
                            modifier = playerModifier,
                        )
                        remainingTimeContent?.invoke(this, remainingTime)
                    }
                } else {
                    Box {
                        loadingPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSurfaceBinding(request: VideoRequest): Pair<ExoPlayer?, SurfaceBindingManager.Binding> {
    val manager: SurfaceBindingManager = koinInject()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val binding =
        remember(request, manager) {
            manager.register(request) { exoPlayer ->
                player = exoPlayer
            }
        }

    DisposableEffect(binding) {
        onDispose {
            binding.dispose()
        }
    }

    return player to binding
}

internal data class VideoRequest(
    val uri: String,
    val customHeaders: Map<String, String> = emptyMap(),
)

@Stable
@Single
public class SurfaceBindingManager(
    @Provided private val context: Context,
    private val media3VideoCacheManager: Media3VideoCacheManager,
) {
    public val player: ExoPlayer by lazy {
        ExoPlayer
            .Builder(context.applicationContext)
            .setMediaSourceFactory(media3VideoCacheManager.mediaSourceFactory())
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
            }
    }

    public interface Binding {
        public fun update(
            rect: IntRect,
            isVisible: Boolean,
        )

        public fun dispose()
    }

    private data class Candidate(
        val binding: Binding,
        val request: VideoRequest,
        val rect: IntRect,
        val isVisible: Boolean,
        val callback: (ExoPlayer?) -> Unit,
    )

    private val candidates = mutableMapOf<Binding, Candidate>()
    private var activeBinding: Binding? = null
    private var activeRequest: VideoRequest? = null

    internal fun register(
        request: VideoRequest,
        onActiveChanged: (ExoPlayer?) -> Unit,
    ): Binding =
        object : Binding {
            override fun update(
                rect: IntRect,
                isVisible: Boolean,
            ) {
                candidates[this] = Candidate(this, request, rect, isVisible, onActiveChanged)
                recalculateActiveItem()
            }

            override fun dispose() {
                candidates.remove(this)
                if (activeBinding == this) {
                    activeBinding = null
                    player.pause() // Stop playback if the active one is removed
                    recalculateActiveItem()
                }
            }
        }

    private fun recalculateActiveItem() {
        val screenHeight =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context
                    .getSystemService<WindowManager>()
                    ?.currentWindowMetrics
                    ?.bounds
                    ?.height()
                    ?: context.resources.displayMetrics.heightPixels
            } else {
                context.resources.displayMetrics.heightPixels
            }
        val screenCenterY = screenHeight / 2f

        // Find best candidate
        val best =
            candidates.values
                .filter { it.isVisible }
                .minByOrNull { kotlin.math.abs(it.rect.center.y - screenCenterY) }

        if (best?.binding != activeBinding) {
            val oldBinding = activeBinding
            val newBinding = best?.binding

            activeBinding = newBinding

            if (best != null) {
                val oldCandidate = candidates[oldBinding]
                if (activeRequest != best.request) {
                    val mediaItem = MediaItem.fromUri(best.request.uri)
                    val mediaSource =
                        media3VideoCacheManager
                            .mediaSourceFactory(best.request.customHeaders)
                            .createMediaSource(mediaItem)
                    player.setMediaSource(mediaSource)
                    player.prepare()
                    player.play()
                    activeRequest = best.request
                } else {
                    if (!player.isPlaying) {
                        player.play()
                    }
                }

                oldCandidate?.callback?.invoke(null)
                best.callback.invoke(player)
            } else {
                // No candidate
                candidates[oldBinding]?.callback?.invoke(null)
                player.pause()
            }
        }
    }
}
