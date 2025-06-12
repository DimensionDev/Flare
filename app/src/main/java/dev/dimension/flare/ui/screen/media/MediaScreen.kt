package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import moe.tlaster.precompose.molecule.producePresenter
import moe.tlaster.swiper.Swiper
import moe.tlaster.swiper.rememberSwiperState
import org.koin.compose.koinInject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
                            .clickable {
                                state.setShowUi(!state.showUi)
                            },
                )
            }
            AnimatedVisibility(
                visible = state.showUi,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                Row(
                    modifier =
                        Modifier
                            .systemBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            onDismiss.invoke()
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Xmark,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                if (!permissionState.status.isGranted) {
                                    permissionState.launchPermissionRequest()
                                } else {
                                    state.save()
                                }
                            } else {
                                state.save()
                            }
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Download,
                            contentDescription = stringResource(id = R.string.media_menu_save),
                        )
                    }
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
    var showUi by remember {
        mutableStateOf(true)
    }
    object {
        val showUi = showUi

        fun setShowUi(value: Boolean) {
            showUi = value
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
    return options.outMimeType?.lowercase() ?: "image/jpeg"
}

internal fun saveByteArrayToDownloads(
    context: Context,
    byteArray: ByteArray,
    fileName: String,
    mimeType: String = getMimeType(byteArray),
) {
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
