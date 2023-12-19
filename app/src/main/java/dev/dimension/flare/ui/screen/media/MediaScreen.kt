package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mxalbert.zoomable.Zoomable
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.FullScreenBox
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.rememberKoinInject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FullScreenDialogStyle : DestinationStyle.Dialog {
    override val properties =
        DialogProperties(
            usePlatformDefaultWidth = false,
        )
}

@Composable
@Destination(
    style = FullScreenDialogStyle::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
)
fun MediaRoute(
    uri: String,
    navigator: DestinationsNavigator,
) {
    MediaScreen(
        uri = uri,
        onDismiss = navigator::navigateUp,
    )
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
)
@Composable
internal fun MediaScreen(
    uri: String,
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
        FullScreenBox(
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
        ) {
            Zoomable(
                dismissGestureEnabled = true,
                onDismiss = {
                    onDismiss.invoke()
                    true
                },
            ) {
                val painter =
                    rememberAsyncImagePainter(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data(uri)
                                .size(Size.ORIGINAL)
                                .build(),
                    )
                if (painter.state is AsyncImagePainter.State.Success) {
                    val size = painter.intrinsicSize
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .aspectRatio(size.width / size.height)
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        state.setShowMenu(true)
                                    },
                                ),
                    )
                }
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
    scope: CoroutineScope = rememberKoinInject(),
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
                    Toast.makeText(
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

private fun saveByteArrayToDownloads(
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
