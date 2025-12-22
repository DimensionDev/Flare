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
import kotlinx.coroutines.android.awaitFrame
import org.koin.compose.koinInject

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
    val binding = rememberSurfaceBinding(uri)
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
        if (!isLoaded && LocalIsScrollingInProgress.current || !visible || player == null) {
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
                    isLoaded = player.isPlaying
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
private fun rememberSurfaceBinding(uri: String): Pair<ExoPlayer?, SurfaceBindingManager.Binding> {
    val manager: SurfaceBindingManager = koinInject()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val binding =
        remember(uri, manager) {
            manager.register(uri) { exoPlayer ->
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

@Stable
public class SurfaceBindingManager(
    private val context: Context,
) {
    public val player: ExoPlayer by lazy {
        ExoPlayer
            .Builder(context)
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
        val uri: String,
        val rect: IntRect,
        val isVisible: Boolean,
        val callback: (ExoPlayer?) -> Unit,
    )

    private val candidates = mutableMapOf<Binding, Candidate>()
    private var activeBinding: Binding? = null

    internal fun register(
        uri: String,
        onActiveChanged: (ExoPlayer?) -> Unit,
    ): Binding =
        object : Binding {
            override fun update(
                rect: IntRect,
                isVisible: Boolean,
            ) {
                candidates[this] = Candidate(this, uri, rect, isVisible, onActiveChanged)
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
                // Check if we are switching to a candidate with the SAME URI
                val oldCandidate = candidates[oldBinding]
                val sameUri = oldCandidate?.uri == best.uri

                if (!sameUri) {
                    // Different URI: Prepare player
                    val currentUri =
                        player.currentMediaItem
                            ?.localConfiguration
                            ?.uri
                            ?.toString()
                    if (currentUri != best.uri) {
                        player.setMediaItem(MediaItem.fromUri(best.uri))
                        player.prepare()
                        player.play()
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
