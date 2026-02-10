package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.Image
import compose.icons.fontawesomeicons.solid.Link
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.copied_to_clipboard
import dev.dimension.flare.media_save
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.settings_appearance_theme_dark
import dev.dimension.flare.settings_appearance_theme_light
import dev.dimension.flare.status_share
import dev.dimension.flare.status_share_image
import dev.dimension.flare.status_share_via_fixvx
import dev.dimension.flare.status_share_via_fxembed
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.ComposeInAppNotification
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.ViewBox
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalContentColor
import io.github.composefluent.LocalTextStyle
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private enum class SharePreviewTheme {
    Light,
    Dark,
}

@Composable
internal fun StatusShareSheet(
    accountType: AccountType,
    statusKey: MicroBlogKey,
    shareUrl: String,
    fxShareUrl: String?,
    fixvxShareUrl: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inAppNotification: ComposeInAppNotification = koinInject()
    val window = LocalComposeWindow.current
    val scope = rememberCoroutineScope()
    val previewGraphicsLayer = rememberGraphicsLayer()
    var previewTheme by remember { mutableStateOf(SharePreviewTheme.Light) }

    val state by producePresenter("DesktopStatusShareSheet_${accountType}_$statusKey") {
        StatusPresenter(accountType = accountType, statusKey = statusKey).invoke()
    }
    FluentDialog(
        visible = true,
    ) {
        Column(
            modifier =
                modifier
                    .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LiteFilter(
                modifier =
                    Modifier
                        .align(Alignment.End),
            ) {
                PillButton(
                    selected = previewTheme == SharePreviewTheme.Light,
                    onSelectedChanged = { previewTheme = SharePreviewTheme.Light },
                ) {
                    Text(stringResource(Res.string.settings_appearance_theme_light))
                }
                PillButton(
                    selected = previewTheme == SharePreviewTheme.Dark,
                    onSelectedChanged = { previewTheme = SharePreviewTheme.Dark },
                ) {
                    Text(stringResource(Res.string.settings_appearance_theme_dark))
                }
            }

            FlareTheme(
                isDarkTheme = previewTheme == SharePreviewTheme.Dark,
            ) {
                ViewBox(
                    modifier = Modifier.sizeIn(maxHeight = 360.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.drawWithContent {
                                previewGraphicsLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawContent()
                            },
                    ) {
                        CompositionLocalProvider(
                            LocalComponentAppearance provides
                                LocalComponentAppearance.current.copy(
                                    showTranslateButton = false,
                                    videoAutoplay = ComponentAppearance.VideoAutoplay.NEVER,
                                ),
                            LocalContentColor provides FluentTheme.colors.text.text.primary,
                            LocalTextStyle provides LocalTextStyle.current.copy(Color.Unspecified),
                        ) {
                            Box(
                                modifier = Modifier.background(FluentTheme.colors.background.mica.base),
                            ) {
                                StatusItem(
                                    item = state.status.takeSuccess(),
                                    detailStatusKey = statusKey,
                                )
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentButton(onClick = {
                    shareText(shareUrl)
                    inAppNotification.message(Res.string.copied_to_clipboard)
                }) {
                    FAIcon(
                        FontAwesomeIcons.Solid.Link,
                        contentDescription = stringResource(Res.string.status_share),
                    )
                    Text(stringResource(Res.string.status_share))
                }
                Button(
                    onClick = {
                        scope.launch {
                            val image = capturePreviewImage(previewGraphicsLayer) ?: return@launch
                            saveImageWithDialog(window, image, "status_$statusKey.png")
                        }
                    },
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.Download,
                        contentDescription = stringResource(Res.string.media_save),
                    )
                    Text(stringResource(Res.string.media_save))
                }
                Button(
                    onClick = {
                        scope.launch {
                            val image = capturePreviewImage(previewGraphicsLayer) ?: return@launch
                            shareImageToClipboard(image)
                            inAppNotification.message(Res.string.copied_to_clipboard)
                        }
                    },
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.Image,
                        contentDescription = stringResource(Res.string.status_share_image),
                    )
                    Text(stringResource(Res.string.status_share_image))
                }
                if (fxShareUrl != null) {
                    Button(onClick = {
                        shareText(fxShareUrl)
                        inAppNotification.message(Res.string.copied_to_clipboard)
                    }) {
                        Text(stringResource(Res.string.status_share_via_fxembed))
                    }
                }
                if (fixvxShareUrl != null) {
                    Button(onClick = {
                        shareText(fixvxShareUrl)
                        inAppNotification.message(Res.string.copied_to_clipboard)
                    }) {
                        Text(stringResource(Res.string.status_share_via_fixvx))
                    }
                }
            }

            AccentButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.cancel))
            }
        }
    }
}

private suspend fun capturePreviewImage(graphicsLayer: GraphicsLayer): ImageBitmap? =
    runCatching { graphicsLayer.toImageBitmap() }.getOrNull()

private fun saveImageWithDialog(
    window: androidx.compose.ui.awt.ComposeWindow?,
    image: ImageBitmap,
    defaultName: String,
) {
    val dialog =
        FileDialog(window).apply {
            mode = FileDialog.SAVE
            file = defaultName
            isVisible = true
        }
    val directory = dialog.directory
    val file = dialog.file
    if (directory.isNullOrEmpty() || file.isNullOrEmpty()) {
        return
    }
    val target = File(directory, file)
    ImageIO.write(image.toBufferedImage(), "png", target)
}

private fun shareImageToClipboard(image: ImageBitmap) {
    val buffered = image.toBufferedImage()
    val selection =
        object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

            override fun getTransferData(flavor: DataFlavor): Any {
                require(flavor == DataFlavor.imageFlavor)
                return buffered
            }
        }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
}

private fun shareText(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(
        java.awt.datatransfer.StringSelection(text),
        null,
    )
}

private fun ImageBitmap.toBufferedImage(): BufferedImage {
    val awt = this.toAwtImage()
    val buffered =
        BufferedImage(awt.getWidth(null), awt.getHeight(null), BufferedImage.TYPE_INT_ARGB)
    val graphics = buffered.createGraphics()
    graphics.drawImage(awt, 0, 0, null)
    graphics.dispose()
    return buffered
}
