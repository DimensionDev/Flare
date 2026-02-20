package dev.dimension.flare.ui.component

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.ui.component.status.LocalIsScrollingInProgress
import dev.dimension.flare.ui.theme.PlatformTheme
import io.github.composefluent.component.ProgressRing
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import kotlin.math.roundToLong

@OptIn(ExperimentalFoundationApi::class)
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
            ProgressRing(
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
    var currentRect by remember { mutableStateOf(androidx.compose.ui.unit.IntRect.Zero) }
    val binding = rememberSurfaceBinding(uri)
    val playerState = binding.first
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current

    LaunchedEffect(binding.second, currentRect, visible, windowInfo.containerSize.height) {
        binding.second.update(currentRect, visible, windowInfo.containerSize.height / 2f)
    }

    LaunchedEffect(muted, playerState) {
        playerState?.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(visible, autoPlay, playerState) {
        if (visible && autoPlay) {
            playerState?.play()
        } else {
            playerState?.pause()
        }
    }

    LaunchedEffect(playerState?.isPlaying, playerState?.sliderPos) {
        isLoaded = playerState?.isPlaying == true && playerState.sliderPos > 0f
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
        if (!isLoaded && LocalIsScrollingInProgress.current || !visible || playerState == null) {
            idlePlaceholder()
        } else {
            AnimatedContent(
                isLoaded,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
            ) { loaded ->
                if (loaded) {
                    val playerModifier =
                        Modifier
                            .clipToBounds()
                            .let {
                                if (onClick != null) {
                                    it.combinedClickable(
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                    )
                                } else {
                                    it
                                }
                            }.let {
                                if (aspectRatio != null) {
                                    it.aspectRatio(aspectRatio)
                                } else {
                                    it
                                }
                            }

                    DisposableEffect(Unit) {
                        onDispose {
                            playerState.stop()
                        }
                    }
                    Box {
                        VideoPlayerSurface(
                            playerState = playerState,
                            modifier = playerModifier,
                            contentScale = contentScale,
                        )
                        val remainingTime by remember {
                            derivedStateOf {
                                if (playerState.sliderPos > 0f) {
                                    (((playerState.currentTime / (playerState.sliderPos / 1000)) - playerState.currentTime) * 1000)
                                        .roundToLong()
                                } else {
                                    0L
                                }
                            }
                        }
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
private fun rememberSurfaceBinding(uri: String): Pair<VideoPlayerState?, SurfaceBindingManager.Binding> {
    val manager: SurfaceBindingManager = org.koin.compose.koinInject()
    var player by remember { mutableStateOf<VideoPlayerState?>(null) }
    val binding =
        remember(uri, manager) {
            manager.register(uri) { videoPlayerState ->
                player = videoPlayerState
            }
        }

    androidx.compose.runtime.DisposableEffect(binding) {
        onDispose {
            binding.dispose()
        }
    }

    return player to binding
}

@androidx.compose.runtime.Stable
public class SurfaceBindingManager {
    public val player: VideoPlayerState by lazy {
        io.github.kdroidfilter.composemediaplayer.createVideoPlayerState().apply {
            loop = true
            volume = 0f
        }
    }

    public interface Binding {
        public fun update(
            rect: androidx.compose.ui.unit.IntRect,
            isVisible: Boolean,
            windowCenterY: Float,
        )

        public fun dispose()
    }

    private data class Candidate(
        val binding: Binding,
        val uri: String,
        val rect: androidx.compose.ui.unit.IntRect,
        val isVisible: Boolean,
        val windowCenterY: Float,
        val callback: (VideoPlayerState?) -> Unit,
    )

    private val candidates = mutableMapOf<Binding, Candidate>()
    private var activeBinding: Binding? = null
    private var currentUri: String? = null

    internal fun register(
        uri: String,
        onActiveChanged: (VideoPlayerState?) -> Unit,
    ): Binding =
        object : Binding {
            override fun update(
                rect: androidx.compose.ui.unit.IntRect,
                isVisible: Boolean,
                windowCenterY: Float,
            ) {
                candidates[this] =
                    Candidate(this, uri, rect, isVisible, windowCenterY, onActiveChanged)
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
        // Find best candidate
        val best =
            candidates.values
                .filter { it.isVisible }
                .minByOrNull { kotlin.math.abs(it.rect.center.y - it.windowCenterY) }

        if (best?.binding != activeBinding) {
            val oldBinding = activeBinding
            val newBinding = best?.binding

            activeBinding = newBinding

            if (best != null) {
                // Check if we are switching to a candidate with the SAME URI
                val oldCandidate = candidates[oldBinding]
                val sameUri = oldCandidate?.uri == best.uri

                if (!sameUri) {
                    // Different URI: Prepare player
                    if (currentUri != best.uri) {
                        currentUri = best.uri
                        player.openUri(best.uri)
                    } else {
                        if (!player.isPlaying) {
                            player.play()
                        }
                    }
                    // Notify bindings
                } else {
                    // Same URI: Seamless handover
                    // Do nothing to the player state (it keeps playing)
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
