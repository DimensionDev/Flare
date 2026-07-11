package dev.dimension.flare.ui.screen.status.action

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.Image
import compose.icons.fontawesomeicons.solid.Link
import compose.icons.fontawesomeicons.solid.Retweet
import dev.dimension.flare.R
import dev.dimension.flare.common.AndroidDownloadManager
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalNetworkImageAllowHardware
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.ViewBox
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.screen.compose.ShortcutComposeActivity
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private enum class SharePreviewTheme {
    Light,
    Dark,
}

private val SharePreviewMaxHeight = 360.dp
private val ShareCardWidth = 360.dp
private val ShareCardPadding = 64.dp
private val ShareCardShapeCornerRadius = 16.dp
private val ShareCardShadowCornerRadius = 12.dp
private val ShareCardShadowRadius = 16.dp
private val ShareCaptureWidth = ShareCardWidth + ShareCardPadding * 2
private const val SHARE_LONG_CAPTURE_HEIGHT_THRESHOLD_PX = 4096

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
    val downloadManager = koinInject<AndroidDownloadManager>()
    val density = LocalDensity.current
    val view = LocalView.current
    val parentCompositionContext = rememberCompositionContext()
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    val scope = rememberCoroutineScope()
    val previewGraphicsLayer = rememberGraphicsLayer()
    var captureRequested by remember { mutableStateOf(false) }
    var isCrossPosting by remember { mutableStateOf(false) }
    var previewContentHeightPx by remember { mutableIntStateOf(0) }
    var previewTheme by remember { mutableStateOf(SharePreviewTheme.Light) }
    val state by producePresenter("status_share_sheet_${statusKey}_$shareUrl") {
        remember(accountType, statusKey) {
            StatusPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
    }
    val status = state.status.takeSuccess()
    val shareCaptureWidthPx = with(density) { ShareCaptureWidth.roundToPx() }

    suspend fun captureBitmap(): Bitmap? {
        val isLongCapture =
            previewContentHeightPx > SHARE_LONG_CAPTURE_HEIGHT_THRESHOLD_PX
        return if (isLongCapture) {
            captureOffscreenShareBitmap(
                context = context,
                view = view,
                parentCompositionContext = parentCompositionContext,
                lifecycleOwner = lifecycleOwner,
                savedStateRegistryOwner = savedStateRegistryOwner,
                viewModelStoreOwner = viewModelStoreOwner,
                widthPx = shareCaptureWidthPx,
            ) {
                CompositionLocalProvider(
                    LocalNetworkImageAllowHardware provides false,
                ) {
                    FlareTheme(
                        darkTheme = previewTheme == SharePreviewTheme.Dark,
                    ) {
                        StatusShareCard(
                            statusKey = statusKey,
                            status = status,
                        )
                    }
                }
            }
        } else {
            capturePreviewShareBitmap(
                graphicsLayer = previewGraphicsLayer,
                onCaptureRequestedChange = { captureRequested = it },
            )
        }
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
        StatusSharePreview(
            statusKey = statusKey,
            status = status,
            previewTheme = previewTheme,
            captureRequested = captureRequested,
            previewGraphicsLayer = previewGraphicsLayer,
            onContentHeightChanged = {
                previewContentHeightPx = it
            },
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = status != null && !isCrossPosting,
                onClick = {
                    scope.launch {
                        isCrossPosting = true
                        try {
                            val bitmap = captureBitmap()
                            val uri =
                                bitmap?.let {
                                    createShareImageUri(
                                        context = context,
                                        bitmap = it,
                                        statusKey = statusKey.toString(),
                                    )
                                }
                            if (uri == null) {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.status_share_crosspost_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                return@launch
                            }

                            val intent =
                                Intent(context, ShortcutComposeActivity::class.java).apply {
                                    action = Intent.ACTION_SEND
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(ShortcutComposeActivity.EXTRA_INITIAL_TEXT, "\n\n$shareUrl")
                                    putExtra(ShortcutComposeActivity.EXTRA_INITIAL_CURSOR_POSITION, 0)
                                    clipData = ClipData.newUri(context.contentResolver, "Cross-post image", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            if (runCatching { context.startActivity(intent) }.isFailure) {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.status_share_crosspost_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                return@launch
                            }
                            onBack()
                        } finally {
                            isCrossPosting = false
                        }
                    }
                },
            ) {
                if (isCrossPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    FAIcon(
                        FontAwesomeIcons.Solid.Retweet,
                        contentDescription = stringResource(id = R.string.status_share_crosspost),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.status_share_crosspost))
            }

            FilledTonalButton(
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
                        val bitmap = captureBitmap()
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

            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val bitmap = captureBitmap()
                        if (bitmap == null) {
                            Toast
                                .makeText(context, R.string.media_save_fail, Toast.LENGTH_SHORT)
                                .show()
                            return@launch
                        }
                        saveBitmapToDownloads(
                            context = context,
                            downloadManager = downloadManager,
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

@Composable
private fun StatusSharePreview(
    statusKey: MicroBlogKey,
    status: UiTimelineV2?,
    previewTheme: SharePreviewTheme,
    captureRequested: Boolean,
    previewGraphicsLayer: GraphicsLayer,
    onContentHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlareTheme(
        darkTheme = previewTheme == SharePreviewTheme.Dark,
    ) {
        ViewBox(
            modifier =
                modifier
                    .sizeIn(maxHeight = SharePreviewMaxHeight),
        ) {
            Box(
                modifier =
                    Modifier
                        .onSizeChanged {
                            onContentHeightChanged(it.height)
                        }.drawWithContent {
                            if (captureRequested) {
                                previewGraphicsLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            drawContent()
                        },
            ) {
                StatusShareCard(
                    statusKey = statusKey,
                    status = status,
                    blockInteractions = true,
                )
            }
        }
    }
}

@Composable
private fun StatusShareCard(
    statusKey: MicroBlogKey,
    status: UiTimelineV2?,
    modifier: Modifier = Modifier,
    blockInteractions: Boolean = false,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(ShareCardPadding)
                    .width(ShareCardWidth)
                    .captureableShadow(
                        cornerRadius = ShareCardShadowCornerRadius,
                        shadowRadius = ShareCardShadowRadius,
                    ),
            shape = RoundedCornerShape(ShareCardShapeCornerRadius),
        ) {
            Box {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides
                        LocalTimelineAppearance.current.copy(
                            expandContentWarning = true,
                            showTranslateButton = false,
                            videoAutoplay = VideoAutoplay.NEVER,
                        ),
                ) {
                    StatusItem(
                        item = status,
                        detailStatusKey = statusKey,
                    )
                }
                if (blockInteractions) {
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

private suspend fun capturePreviewShareBitmap(
    graphicsLayer: GraphicsLayer,
    onCaptureRequestedChange: (Boolean) -> Unit,
): Bitmap? =
    try {
        onCaptureRequestedChange(true)
        withFrameNanos { }
        withFrameNanos { }
        runCatching {
            graphicsLayer.toImageBitmap().asAndroidBitmap()
        }.getOrNull()
    } finally {
        onCaptureRequestedChange(false)
    }

private suspend fun captureOffscreenShareBitmap(
    context: Context,
    view: View,
    parentCompositionContext: CompositionContext,
    lifecycleOwner: LifecycleOwner,
    savedStateRegistryOwner: SavedStateRegistryOwner,
    viewModelStoreOwner: ViewModelStoreOwner?,
    widthPx: Int,
    content: @Composable () -> Unit,
): Bitmap? =
    runCatching {
        val themedContext = ContextThemeWrapper(context, context.theme)
        val captureHost =
            checkNotNull(view.findCaptureHostView()) {
                "Unable to find a host view for share capture"
            }
        val composeView =
            ComposeView(themedContext).apply {
                setParentCompositionContext(parentCompositionContext)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                viewModelStoreOwner?.let {
                    setViewTreeViewModelStoreOwner(it)
                }
                setContent(content)
            }
        val container =
            FrameLayout(themedContext).apply {
                alpha = 0f
                translationX = -10_000f
                clipChildren = false
                clipToPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        widthPx,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        try {
            captureHost.addView(
                container,
                ViewGroup.LayoutParams(
                    widthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )

            repeat(2) {
                withFrameNanos { }
            }

            val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

            repeat(2) {
                container.measure(widthSpec, heightSpec)
                container.layout(
                    -container.measuredWidth * 2,
                    0,
                    -container.measuredWidth,
                    container.measuredHeight,
                )
                withFrameNanos { }
            }

            val captureWidth = composeView.measuredWidth
            val captureHeight = composeView.measuredHeight

            check(captureWidth > 0 && captureHeight > 0) {
                "Unable to measure share content"
            }

            createBitmap(captureWidth, captureHeight).also { bitmap ->
                composeView.draw(Canvas(bitmap))
            }
        } finally {
            captureHost.removeView(container)
            composeView.disposeComposition()
        }
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()

private fun View.findCaptureHostView(): ViewGroup? =
    rootView.findViewById<ViewGroup?>(android.R.id.content)
        ?: rootView as? ViewGroup
        ?: parent as? ViewGroup

private suspend fun saveBitmapToDownloads(
    context: Context,
    downloadManager: AndroidDownloadManager,
    bitmap: Bitmap,
    statusKey: String,
) {
    val bytes =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    val fileName = MediaFileNamePolicy.screenshotFileName(statusKey)
    val success =
        downloadManager.saveByteArray(
            byteArray = bytes,
            fileName = fileName,
            mimeType = "image/png",
        )
    Toast
        .makeText(
            context,
            context.getString(
                if (success) {
                    R.string.media_save_success
                } else {
                    R.string.media_save_fail
                },
            ),
            Toast.LENGTH_SHORT,
        ).show()
}

private fun shareBitmapAsImage(
    context: Context,
    bitmap: Bitmap,
    statusKey: String,
): File? {
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return null
    }
    val file =
        File(
            context.cacheDir,
            "status_share_${MediaFileNamePolicy.sanitizeFileName(statusKey)}_${System.currentTimeMillis()}.png",
        )
    FileOutputStream(file).use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return file
}

private fun createShareImageUri(
    context: Context,
    bitmap: Bitmap,
    statusKey: String,
) = runCatching {
    val imageFile = shareBitmapAsImage(context, bitmap, statusKey) ?: return@runCatching null
    FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        imageFile,
    )
}.getOrNull()

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
        val frameworkPaint = paint.nativePaint
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
