package dev.dimension.flare.ui.component.status.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalNetworkImageAllowHardware
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ReferenceShareImageRenderer
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
internal fun rememberAndroidReferenceShareImageRenderer(): ReferenceShareImageRenderer {
    val context = LocalContext.current
    val rootView = LocalView.current.rootView
    val density = LocalDensity.current
    val darkTheme = isSystemInDarkTheme()
    val globalAppearance = LocalGlobalAppearance.current
    val timelineAppearance = LocalTimelineAppearance.current
    val widthPx = with(density) { AndroidStatusShareCaptureWidth.roundToPx() }
    return remember(
        context,
        rootView,
        darkTheme,
        globalAppearance,
        timelineAppearance,
        widthPx,
    ) {
        object : ReferenceShareImageRenderer {
            override fun render(
                post: UiTimelineV2,
                completion: (ComposeData.Media?, String?) -> Unit,
            ) {
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    val bitmap =
                        renderAndroidStatusShareImage(
                            context = context,
                            view = rootView,
                            widthPx = widthPx,
                        ) {
                            CompositionLocalProvider(
                                LocalGlobalAppearance provides globalAppearance,
                                LocalTimelineAppearance provides timelineAppearance,
                                LocalNetworkImageAllowHardware provides false,
                            ) {
                                FlareTheme(darkTheme = darkTheme) {
                                    AndroidStatusShareImageContent(
                                        statusKey = post.statusKey,
                                        status = post,
                                    )
                                }
                            }
                        }
                    if (bitmap == null) {
                        completion(null, "Unable to render referenced post image.")
                        return@launch
                    }
                    runCatching {
                        withContext(Dispatchers.IO) {
                            bitmap.toComposeMedia(context)
                        }
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

private fun Bitmap.toComposeMedia(context: Context): ComposeData.Media {
    val directory = File(context.cacheDir, "reference_share")
    directory.mkdirs()
    val file = File(directory, "reference_${UUID.randomUUID()}.png")
    FileOutputStream(file).use { output ->
        check(compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    return ComposeData.Media(
        file = FileItem(context, Uri.fromFile(file)),
        altText = null,
    )
}
