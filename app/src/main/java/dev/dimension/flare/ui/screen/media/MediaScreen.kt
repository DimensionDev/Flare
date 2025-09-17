package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.Xmark
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
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
    val hapticFeedback = LocalHapticFeedback.current
    val hazeState = rememberHazeState()
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
            Swiper(
                state = swiperState,
                modifier =
                    Modifier
                        .hazeSource(state = hazeState),
            ) {
                val zoomableState =
                    rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f)))
                ZoomableAsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(uri)
                            .placeholderMemoryCacheKey(previewUrl)
                            .crossfade(1_000)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    state = zoomableState,
                    onClick = {
                        state.setShowUi(!state.showUi)
                    },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(
                            HapticFeedbackType.LongPress,
                        )
                        state.setShowSheet(true)
                    },
                    modifier =
                        Modifier
                            .fillMaxSize(),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Glassify(
                        onClick = {
                            onDismiss.invoke()
                        },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        hazeState = hazeState,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Xmark,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Glassify(
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
                        hazeState = hazeState,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Download,
                            contentDescription = stringResource(id = R.string.media_menu_save),
                        )
                    }
                    Glassify(
                        onClick = {
                            state.share()
                        },
                        hazeState = hazeState,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.ShareNodes,
                            contentDescription = stringResource(id = R.string.media_menu_share_image),
                        )
                    }
                }
            }
        }

        if (state.showSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    state.setShowSheet(false)
                },
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(id = R.string.media_menu_save))
                    },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Download,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier =
                        Modifier
                            .clickable {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    if (!permissionState.status.isGranted) {
                                        permissionState.launchPermissionRequest()
                                    } else {
                                        state.save()
                                    }
                                } else {
                                    state.save()
                                }
                                state.setShowSheet(false)
                            },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(id = R.string.media_menu_share_image))
                    },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.ShareNodes,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier =
                        Modifier
                            .clickable {
                                state.share()
                                state.setShowSheet(false)
                            },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )
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
    var showSheet by remember {
        mutableStateOf(false)
    }
    object {
        val showUi = showUi

        val showSheet = showSheet

        fun setShowSheet(value: Boolean) {
            showSheet = value
        }

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

        fun share() {
            scope.launch {
                context.imageLoader.diskCache?.openSnapshot(uri)?.use {
                    val originFile = it.data.toFile()
                    val targetFile =
                        File(
                            context.cacheDir,
                            uri.substringAfterLast("/"),
                        )
                    originFile.copyTo(targetFile, overwrite = true)
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            targetFile,
                        )
                    val intent =
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, uri)
                            setDataAndType(
                                uri,
                                "image/*",
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            context.getString(R.string.media_menu_share_image),
                        ),
                    )
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
