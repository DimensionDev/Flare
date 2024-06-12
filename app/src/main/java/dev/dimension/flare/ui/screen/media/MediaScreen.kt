package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import moe.tlaster.swiper.Swiper
import moe.tlaster.swiper.rememberSwiperState
import org.koin.compose.koinInject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FullScreenDialogStyle : DestinationStyle.Dialog() {
    override val properties =
        DialogProperties(
            usePlatformDefaultWidth = true,
            decorFitsSystemWindows = false,
        )
}

@Composable
fun getActivityWindow(): Window? = LocalView.current.context.getActivityWindow()

private tailrec fun Context.getActivityWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.getActivityWindow()
        else -> null
    }

@Composable
fun SetDialogDestinationToEdgeToEdge() {
    val activityWindow = getActivityWindow()
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    val parentView = LocalView.current.parent as View
    SideEffect {
        if (activityWindow != null && dialogWindow != null) {
            val attributes = WindowManager.LayoutParams()
            attributes.copyFrom(activityWindow.attributes)
            attributes.type = dialogWindow.attributes.type
            dialogWindow.attributes = attributes
            parentView.layoutParams =
                FrameLayout.LayoutParams(
                    activityWindow.decorView.width,
                    activityWindow.decorView.height,
                )
        }
    }
}

@Composable
@Destination<RootGraph>(
    style = FullScreenDialogStyle::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.RawImage.ROUTE,
        ),
    ],
)
fun MediaRoute(
    uri: String,
    navigator: DestinationsNavigator,
    previewUrl: String? = null,
) {
    SetDialogDestinationToEdgeToEdge()
    MediaScreen(
        uri = uri,
        onDismiss = navigator::navigateUp,
        previewUrl = previewUrl,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
internal fun MediaScreen(
    uri: String,
    previewUrl: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val permissionState =
        rememberPermissionState(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

    val state by producePresenter(uri) {
        mediaPresenter(uri, context)
    }
    val haptics = LocalHapticFeedback.current
    FlareTheme(
        darkTheme = true,
    ) {
        val swiperState =
            rememberSwiperState(
                onDismiss = onDismiss,
            )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 1 - swiperState.progress))
                    .alpha(1 - swiperState.progress),
        ) {
            Swiper(state = swiperState) {
                val zoomableState =
                    rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
                val painter =
                    rememberAsyncImagePainter(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(uri)
                                .placeholderMemoryCacheKey(previewUrl)
                                .crossfade(1_000)
                                .build(),
                    )
                LaunchedEffect(painter.intrinsicSize) {
                    zoomableState.setContentLocation(
                        ZoomableContentLocation.scaledInsideAndCenterAligned(
                            painter.intrinsicSize,
                        ),
                    )
                }
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Inside,
                    alignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zoomable(zoomableState)
                            .combinedClickable(
                                onClick = {
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    state.setShowMenu(true)
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ),
                )
            }
            if (state.showMenu) {
                ModalBottomSheet(
                    onDismissRequest = {
                        state.setShowMenu(false)
                    },
                ) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.media_menu_save))
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = stringResource(R.string.media_menu_save),
                            )
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                        if (!permissionState.status.isGranted) {
                                            permissionState.launchPermissionRequest()
                                        } else {
                                            state.setShowMenu(false)
                                            state.save()
                                        }
                                    } else {
                                        state.setShowMenu(false)
                                        state.save()
                                    }
                                },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun mediaPresenter(
    uri: String,
    context: Context,
    scope: CoroutineScope = koinInject(),
) = run {
    var showMenu by remember { mutableStateOf(false) }
    object {
        val showMenu: Boolean
            get() = showMenu

        fun setShowMenu(value: Boolean) {
            showMenu = value
        }

        fun save() {
            scope.launch {
                context.imageLoader.diskCache?.openSnapshot(uri)?.use {
                    val byteArray = it.data.toFile().readBytes()
                    val fileName = uri.substringAfterLast("/")
                    saveByteArrayToDownloads(context, byteArray, fileName)
                }
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.media_save_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }
}

private fun getMimeType(byteArray: ByteArray): String {
    val options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
    BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
    val extension =
        when (options.outMimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/ico" -> "ico"
            "image/tiff" -> "tiff"
            "image/svg+xml" -> "svg"
            "image/x-icon" -> "ico"
            "image/x-ms-bmp" -> "bmp"
            "image/x-tiff" -> "tiff"
            "image/x-tga" -> "tga"
            "image/x-pcx" -> "pcx"
            "image/x-pict" -> "pict"
            else -> "jpg"
        }
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg" // 默认为JPEG
}

internal fun saveByteArrayToDownloads(
    context: Context,
    byteArray: ByteArray,
    fileName: String,
) {
    val mimeType = getMimeType(byteArray)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        try {
            context.contentResolver.openOutputStream(uri ?: return)?.use { outputStream ->
                outputStream.write(byteArray)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    } else {
        val directory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(directory, fileName)
        try {
            FileOutputStream(file).use { outputStream ->
                outputStream.write(byteArray)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
