package dev.dimension.flare.ui.screen.status.action

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.Image
import compose.icons.fontawesomeicons.solid.Link
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.ViewBox
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.screen.media.saveByteArrayToDownloads
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private enum class SharePreviewTheme {
    Light,
    Dark,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatusShareSheet(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    shareUrl: String,
    fxShareUrl: String?,
    fixvxShareUrl: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewGraphicsLayer = rememberGraphicsLayer()
    var previewTheme by remember { mutableStateOf(SharePreviewTheme.Light) }
    val state by producePresenter("status_share_sheet_${statusKey}_$shareUrl") {
        StatusPresenter(accountType = accountType, statusKey = statusKey).invoke()
    }
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenHorizontalPadding)
                .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlareTheme(
            darkTheme = previewTheme == SharePreviewTheme.Dark,
        ) {
            ViewBox(
                modifier =
                    Modifier
                        .sizeIn(maxHeight = 360.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .drawWithContent {
                                previewGraphicsLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawContent()
                            },
                ) {
                    Box(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    ) {
                        Card(
                            modifier =
                                Modifier
                                    .padding(64.dp)
                                    .width(360.dp)
                                    .captureableShadow(cornerRadius = 12.dp, shadowRadius = 16.dp),
                        ) {
                            Box {
                                CompositionLocalProvider(
                                    LocalComponentAppearance provides
                                        LocalComponentAppearance.current.copy(
                                            showTranslateButton = false,
                                            videoAutoplay = ComponentAppearance.VideoAutoplay.NEVER,
                                        ),
                                ) {
                                    StatusItem(
                                        item = state.status.takeSuccess(),
                                        detailStatusKey = statusKey,
                                    )
                                }
                                Box(
                                    modifier =
                                        Modifier
                                            .matchParentSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {},
                                            ),
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    shareText(context = context, content = shareUrl)
                    onBack()
                },
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.Link,
                    contentDescription = stringResource(id = R.string.rss_detail_share),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.rss_detail_share))
            }

            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val bitmap = captureShareBitmap(previewGraphicsLayer)
                        if (bitmap == null) {
                            Toast
                                .makeText(context, R.string.media_save_fail, Toast.LENGTH_SHORT)
                                .show()
                            return@launch
                        }
                        saveBitmapToDownloads(
                            context = context,
                            bitmap = bitmap,
                            statusKey = statusKey.toString(),
                        )
                    }
                },
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.Download,
                    contentDescription = stringResource(id = R.string.media_menu_save),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.media_menu_save))
            }
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val bitmap = captureShareBitmap(previewGraphicsLayer)
                        if (bitmap == null) {
                            Toast
                                .makeText(context, R.string.media_save_fail, Toast.LENGTH_SHORT)
                                .show()
                            return@launch
                        }
                        val imageFile =
                            shareBitmapAsImage(
                                context = context,
                                bitmap = bitmap,
                                statusKey = statusKey.toString(),
                            )
                        if (imageFile == null) {
                            Toast
                                .makeText(context, R.string.media_save_fail, Toast.LENGTH_SHORT)
                                .show()
                            return@launch
                        }
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                context.packageName + ".provider",
                                imageFile,
                            )
                        val intent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, uri)
                                setDataAndType(uri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                @SuppressLint("LocalContextGetResourceValueCall")
                                context.getString(R.string.media_menu_share_image),
                            ),
                        )
                        onBack()
                    }
                },
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.Image,
                    contentDescription = stringResource(id = R.string.media_menu_save),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.media_menu_share_image))
            }

            if (fxShareUrl != null) {
                FilledTonalButton(
                    onClick = {
                        shareText(context = context, content = fxShareUrl)
                        onBack()
                    },
                ) {
                    Text(text = stringResource(id = R.string.status_menu_share_via_fxembed))
                }
            }

            if (fixvxShareUrl != null) {
                FilledTonalButton(
                    onClick = {
                        shareText(context = context, content = fixvxShareUrl)
                        onBack()
                    },
                ) {
                    Text(text = stringResource(id = R.string.status_menu_share_via_fixvx))
                }
            }
        }
        SegmentedListItem(
            onClick = {},
            shapes = ListItemDefaults.single(),
            content = {
                Text(
                    text = stringResource(id = R.string.status_share_sheet_theme_title),
                )
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val entries = SharePreviewTheme.entries
                    entries.forEachIndexed { index, value ->
                        ToggleButton(
                            checked = previewTheme == value,
                            onCheckedChange = {
                                if (it) previewTheme = value
                            },
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                        ) {
                            Text(
                                text =
                                    when (value) {
                                        SharePreviewTheme.Light -> stringResource(R.string.settings_appearance_theme_light)
                                        SharePreviewTheme.Dark -> stringResource(R.string.settings_appearance_theme_dark)
                                    },
                            )
                        }
                    }
                }
            },
        )
    }
}

private fun shareText(
    context: Context,
    content: String,
) {
    val sendIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

private suspend fun captureShareBitmap(graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer): Bitmap? =
    runCatching {
        graphicsLayer.toImageBitmap().asAndroidBitmap()
    }.getOrNull()

private fun saveBitmapToDownloads(
    context: Context,
    bitmap: Bitmap,
    statusKey: String,
) {
    val bytes =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    val fileName = "status_${statusKey}_${System.currentTimeMillis()}.png"
    saveByteArrayToDownloads(
        context = context,
        byteArray = bytes,
        fileName = fileName,
        mimeType = "image/png",
    )
    Toast
        .makeText(context, context.getString(R.string.media_save_success), Toast.LENGTH_SHORT)
        .show()
}

private fun shareBitmapAsImage(
    context: Context,
    bitmap: Bitmap,
    statusKey: String,
): File? {
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return null
    }
    val file = File(context.cacheDir, "status_share_${statusKey}_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return file
}

private fun Modifier.captureableShadow(
    color: Color = Color.Black,
    cornerRadius: Dp = 0.dp,
    shadowRadius: Dp = 8.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    alpha: Float = 0.2f,
) = this.drawBehind {
    val shadowColor = color.copy(alpha = alpha).toArgb()
    val transparentColor = color.copy(alpha = 0f).toArgb()

    this.drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = transparentColor

        frameworkPaint.setShadowLayer(
            shadowRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            shadowColor,
        )

        it.drawRoundRect(
            0f,
            0f,
            this.size.width,
            this.size.height,
            cornerRadius.toPx(),
            cornerRadius.toPx(),
            paint,
        )
    }
}
