package dev.dimension.flare.ui.screen.compose

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume

@OptIn(ExperimentalFoundationApi::class)
internal class ComposeImageReceiver(
    private val context: Context,
    private val media: ComposeMediaState,
    private val insertText: ((String) -> Unit)? = null,
) : ReceiveContentListener {
    override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
        val images = mutableListOf<Uri>()
        val remaining =
            transferableContent.consume { item ->
                val uri = item.uri ?: return@consume false
                if (uri.scheme != "content" && uri.scheme != "file") return@consume false
                val suppliedType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                val extensionType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        uri.lastPathSegment?.substringAfterLast('.')?.lowercase(),
                    )
                val mime =
                    suppliedType?.takeUnless { it == "application/octet-stream" }
                        ?: extensionType?.takeUnless { it == "application/octet-stream" }
                val image =
                    mime?.startsWith("image/")
                        ?: transferableContent.clipEntry.clipData.description
                            .hasMimeType("image/*")
                if (image) images += uri
                image
            }
        media.importImages(images, transferableContent)
        if (insertText == null || remaining == null) return remaining
        val texts = mutableListOf<String>()
        val unhandled =
            remaining.consume { item ->
                item.text?.let { texts += it.toString() } != null
            }
        if (texts.isNotEmpty()) insertText.invoke(texts.joinToString("\n"))
        return unhandled
    }
}
