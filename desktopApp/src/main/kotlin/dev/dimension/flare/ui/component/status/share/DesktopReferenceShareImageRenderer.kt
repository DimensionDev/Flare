package dev.dimension.flare.ui.component.status.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ReferenceShareImageRenderer
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

@Composable
internal fun rememberDesktopReferenceShareImageRenderer(): ReferenceShareImageRenderer {
    val globalAppearance = LocalGlobalAppearance.current
    val timelineAppearance = LocalTimelineAppearance.current
    return remember(globalAppearance, timelineAppearance) {
        object : ReferenceShareImageRenderer {
            override fun render(
                post: UiTimelineV2,
                completion: (ComposeData.Media?, String?) -> Unit,
            ) {
                CoroutineScope(Dispatchers.Swing).launch {
                    runCatching {
                        val image =
                            renderDesktopStatusShareImage {
                                CompositionLocalProvider(
                                    LocalGlobalAppearance provides globalAppearance,
                                    LocalTimelineAppearance provides timelineAppearance,
                                ) {
                                    FlareTheme {
                                        DesktopStatusShareImageContent(
                                            statusKey = post.statusKey,
                                            status = post,
                                            timelineAppearance = timelineAppearance,
                                        )
                                    }
                                }
                            }
                        val file = File.createTempFile("flare-reference-${UUID.randomUUID()}", ".png")
                        check(ImageIO.write(image.toBufferedImage(), "png", file))
                        ComposeData.Media(file = FileItem(file), altText = null)
                    }.onSuccess { media ->
                        completion(media, null)
                    }.onFailure { throwable ->
                        completion(null, throwable.message)
                    }
                }
            }
        }
    }
}
