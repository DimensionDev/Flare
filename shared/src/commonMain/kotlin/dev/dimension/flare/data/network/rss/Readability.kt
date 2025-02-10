package dev.dimension.flare.data.network.rss

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class Readability(
    private val scraper: NativeWebScraper,
) {
    suspend fun parse(url: String): DocumentData =
        suspendCoroutine { continuation ->
            scraper.parse(
                url = url,
                scriptToInject = ReadabilityJS,
                callback = {
                    continuation.resume(it.decodeJson<DocumentData>())
                },
            )
        }
}

@Immutable
@Serializable
public data class DocumentData(
    val title: String,
    val content: String,
    val textContent: String,
    val length: Int?,
    val excerpt: String?,
    val byline: String?,
    val dir: String?,
    val siteName: String?,
    val lang: String?,
    val publishedTime: String?,
) {
    val richTextContent: UiRichText by lazy {
        parseHtml(content).toUi()
    }
}
